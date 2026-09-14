package com.denggl2.mason.connector

import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import com.denggl2.mason.protocol.MasonProtocolJson
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.Serializable

@Serializable
data class StoredConnectorIdentity(
    val schemaVersion: Int = CONNECTOR_IDENTITY_SCHEMA_VERSION,
    val keyAlgorithm: DeviceKeyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
    val publicKey: String,
    val protectedPrivateKey: String,
    val createdAt: Long,
)

data class ConnectorPublicIdentity(
    val keyAlgorithm: DeviceKeyAlgorithm,
    val publicKey: String,
    val publicKeyFingerprint: String,
    val createdAt: Long,
)

interface ConnectorSecretProtector {
    fun protect(plainText: ByteArray): ByteArray
    fun unprotect(cipherText: ByteArray): ByteArray
}

class WindowsDpapiProtector : ConnectorSecretProtector {
    init {
        require(Platform.isWindows()) { "Windows DPAPI is only available on Windows" }
    }

    override fun protect(plainText: ByteArray): ByteArray = Crypt32Util.cryptProtectData(
        plainText,
        ENTROPY,
        WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
        "MASON Connector device identity",
        null,
    )

    override fun unprotect(cipherText: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(
        cipherText,
        ENTROPY,
        WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
        null,
    )

    private companion object {
        val ENTROPY: ByteArray = "mason-connector-identity-v1".toByteArray(StandardCharsets.UTF_8)
    }
}

class WindowsConnectorIdentityStore(
    identityPath: Path,
    private val protector: ConnectorSecretProtector = WindowsDpapiProtector(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val path = identityPath.toAbsolutePath().normalize()
    private val lock = ReentrantLock()

    fun getOrCreateIdentity(): ConnectorPublicIdentity = lock.withLock {
        loadOrCreate().toPublicIdentity()
    }

    fun sign(payload: String): String = lock.withLock {
        val stored = loadOrCreate()
        withPrivateKey(stored) { privateKey -> EcdsaP256Crypto.sign(privateKey, payload) }
    }

    private fun loadOrCreate(): StoredConnectorIdentity {
        if (!Files.exists(path)) return createIdentity()
        val stored = runCatching {
            MasonProtocolJson.decode<StoredConnectorIdentity>(Files.readString(path, StandardCharsets.UTF_8))
        }.getOrElse { error ->
            throw IllegalStateException("Cannot read Connector identity at $path", error)
        }
        validate(stored)
        return stored
    }

    private fun createIdentity(): StoredConnectorIdentity {
        val keyPair = EcdsaP256Crypto.generateKeyPair()
        val privateKeyBytes = keyPair.private.encoded
            ?: error("Generated Connector private key is not encodable")
        val protectedBytes = try {
            protector.protect(privateKeyBytes)
        } finally {
            privateKeyBytes.fill(0)
        }
        val stored = StoredConnectorIdentity(
            publicKey = EcdsaP256Crypto.encodePublicKey(keyPair.public),
            protectedPrivateKey = Base64.getEncoder().encodeToString(protectedBytes),
            createdAt = now(),
        )
        protectedBytes.fill(0)
        write(stored)
        return stored
    }

    private fun validate(stored: StoredConnectorIdentity) {
        require(stored.schemaVersion == CONNECTOR_IDENTITY_SCHEMA_VERSION) {
            "Unsupported Connector identity schema: ${stored.schemaVersion}"
        }
        require(stored.keyAlgorithm == DeviceKeyAlgorithm.ECDSA_P256_SHA256) {
            "Unsupported Connector identity algorithm: ${stored.keyAlgorithm}"
        }
        val publicKey = runCatching { EcdsaP256Crypto.decodePublicKey(stored.publicKey) }.getOrElse {
            throw IllegalStateException("Connector identity public key is invalid", it)
        }
        val signature = runCatching {
            withPrivateKey(stored) { privateKey -> EcdsaP256Crypto.sign(privateKey, IDENTITY_SELF_CHECK) }
        }.getOrElse { error ->
            throw IllegalStateException("Cannot unlock Connector identity for the current Windows user", error)
        }
        check(EcdsaP256Crypto.verify(publicKey, IDENTITY_SELF_CHECK, signature)) {
            "Connector identity public and private keys do not match"
        }
    }

    private fun <T> withPrivateKey(
        stored: StoredConnectorIdentity,
        block: (java.security.PrivateKey) -> T,
    ): T {
        val cipherText = Base64.getDecoder().decode(stored.protectedPrivateKey)
        val privateKeyBytes = try {
            protector.unprotect(cipherText)
        } finally {
            cipherText.fill(0)
        }
        return try {
            val privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            block(privateKey)
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    private fun StoredConnectorIdentity.toPublicIdentity() = ConnectorPublicIdentity(
        keyAlgorithm = keyAlgorithm,
        publicKey = publicKey,
        publicKeyFingerprint = EcdsaP256Crypto.fingerprint(publicKey),
        createdAt = createdAt,
    )

    private fun write(identity: StoredConnectorIdentity) {
        val parent = requireNotNull(path.parent) { "Identity path must have a parent directory" }
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
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        private const val IDENTITY_SELF_CHECK = "mason-connector-identity-self-check-v1"
    }
}

const val CONNECTOR_IDENTITY_SCHEMA_VERSION: Int = 1
