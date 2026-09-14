package com.denggl2.mason.connector

import com.denggl2.mason.protocol.MasonProtocolJson
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.withLock
import kotlinx.serialization.Serializable

@Serializable
data class StoredTlsIdentity(
    val schemaVersion: Int = TLS_IDENTITY_SCHEMA_VERSION,
    val certificateDer: String,
    val certificateSha256: String,
    val protectedBundle: String,
    val createdAt: Long,
)

@Serializable
private data class TlsIdentityBundle(
    val password: String,
    val pkcs12: String,
)

class ConnectorTlsIdentity internal constructor(
    val keyStore: KeyStore,
    val keyAlias: String,
    val password: CharArray,
    val certificateDer: String,
    val certificateSha256: String,
    val createdAt: Long,
) : AutoCloseable {
    override fun close() {
        password.fill('\u0000')
    }
}

class ConnectorTlsIdentityStore(
    identityPath: Path,
    private val protector: ConnectorSecretProtector = WindowsDpapiProtector(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val path = identityPath.toAbsolutePath().normalize()
    private val lock = ReentrantLock()

    fun getOrCreateIdentity(): ConnectorTlsIdentity = lock.withLock {
        if (Files.exists(path)) load(readStored()) else create()
    }

    private fun create(): ConnectorTlsIdentity {
        val password = randomPassword()
        val generated = buildKeyStore {
            certificate(TLS_KEY_ALIAS) {
                this.password = password
                subject = X500Principal("CN=MASON Connector")
                daysValid = TLS_CERTIFICATE_VALID_DAYS
                keySizeInBits = 2048
                keyType = KeyType.Server
                domains = listOf("localhost")
                ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
            }
        }
        val passwordChars = password.toCharArray()
        val pkcs12 = KeyStore.getInstance("PKCS12").apply {
            load(null, passwordChars)
            setKeyEntry(
                TLS_KEY_ALIAS,
                generated.getKey(TLS_KEY_ALIAS, passwordChars),
                passwordChars,
                generated.getCertificateChain(TLS_KEY_ALIAS),
            )
        }
        val certificate = pkcs12.getCertificate(TLS_KEY_ALIAS) as X509Certificate
        val pkcs12Bytes = ByteArrayOutputStream().use { output ->
            pkcs12.store(output, passwordChars)
            output.toByteArray()
        }
        val bundleBytes = MasonProtocolJson.encode(
            TlsIdentityBundle(
                password = password,
                pkcs12 = Base64.getEncoder().encodeToString(pkcs12Bytes),
            ),
        ).toByteArray(StandardCharsets.UTF_8)
        pkcs12Bytes.fill(0)
        val protectedBytes = try {
            protector.protect(bundleBytes)
        } finally {
            bundleBytes.fill(0)
        }
        val stored = StoredTlsIdentity(
            certificateDer = Base64.getEncoder().encodeToString(certificate.encoded),
            certificateSha256 = certificateSha256(certificate.encoded),
            protectedBundle = Base64.getEncoder().encodeToString(protectedBytes),
            createdAt = now(),
        )
        protectedBytes.fill(0)
        write(stored)
        return ConnectorTlsIdentity(
            keyStore = pkcs12,
            keyAlias = TLS_KEY_ALIAS,
            password = passwordChars,
            certificateDer = stored.certificateDer,
            certificateSha256 = stored.certificateSha256,
            createdAt = stored.createdAt,
        )
    }

    private fun readStored(): StoredTlsIdentity = runCatching {
        MasonProtocolJson.decode<StoredTlsIdentity>(Files.readString(path, StandardCharsets.UTF_8))
    }.getOrElse { error ->
        throw IllegalStateException("Cannot read Connector TLS identity at $path", error)
    }

    private fun load(stored: StoredTlsIdentity): ConnectorTlsIdentity {
        require(stored.schemaVersion == TLS_IDENTITY_SCHEMA_VERSION) {
            "Unsupported Connector TLS identity schema: ${stored.schemaVersion}"
        }
        val cipherText = runCatching { Base64.getDecoder().decode(stored.protectedBundle) }.getOrElse {
            throw IllegalStateException("Connector TLS identity bundle is invalid", it)
        }
        val bundleBytes = try {
            protector.unprotect(cipherText)
        } catch (error: Exception) {
            throw IllegalStateException("Cannot unlock Connector TLS identity for the current Windows user", error)
        } finally {
            cipherText.fill(0)
        }
        val bundle = try {
            MasonProtocolJson.decode<TlsIdentityBundle>(String(bundleBytes, StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalStateException("Connector TLS identity bundle is invalid", error)
        } finally {
            bundleBytes.fill(0)
        }
        val passwordChars = bundle.password.toCharArray()
        val pkcs12Bytes = runCatching { Base64.getDecoder().decode(bundle.pkcs12) }.getOrElse {
            passwordChars.fill('\u0000')
            throw IllegalStateException("Connector TLS PKCS12 bundle is invalid", it)
        }
        try {
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                ByteArrayInputStream(pkcs12Bytes).use { load(it, passwordChars) }
            }
            check(keyStore.isKeyEntry(TLS_KEY_ALIAS)) { "Connector TLS private key is missing" }
            val certificate = keyStore.getCertificate(TLS_KEY_ALIAS) as? X509Certificate
                ?: error("Connector TLS certificate is missing")
            certificate.checkValidity()
            val certificateDer = Base64.getEncoder().encodeToString(certificate.encoded)
            val fingerprint = certificateSha256(certificate.encoded)
            check(certificateDer == stored.certificateDer) { "Connector TLS certificate does not match stored DER" }
            check(fingerprint.equals(stored.certificateSha256, ignoreCase = true)) {
                "Connector TLS certificate fingerprint does not match"
            }
            return ConnectorTlsIdentity(
                keyStore = keyStore,
                keyAlias = TLS_KEY_ALIAS,
                password = passwordChars,
                certificateDer = certificateDer,
                certificateSha256 = fingerprint,
                createdAt = stored.createdAt,
            )
        } catch (error: Exception) {
            passwordChars.fill('\u0000')
            throw IllegalStateException("Connector TLS identity failed validation", error)
        } finally {
            pkcs12Bytes.fill(0)
        }
    }

    private fun write(identity: StoredTlsIdentity) {
        val parent = requireNotNull(path.parent) { "TLS identity path must have a parent directory" }
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
        try {
            Files.writeString(
                temporary,
                MasonProtocolJson.encode(identity),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}

internal fun certificateSha256(der: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(der)
    .joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

const val TLS_IDENTITY_SCHEMA_VERSION: Int = 1
private const val TLS_KEY_ALIAS = "mason-connector-tls"
private const val TLS_CERTIFICATE_VALID_DAYS = 825L
