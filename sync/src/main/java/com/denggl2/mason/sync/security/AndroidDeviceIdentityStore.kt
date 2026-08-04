package com.denggl2.mason.sync.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.denggl2.mason.protocol.DeviceKeyAlgorithm
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

data class DevicePublicIdentity(
    val keyAlgorithm: DeviceKeyAlgorithm,
    val publicKey: String,
)

interface DeviceIdentitySigner {
    fun getOrCreateIdentity(): DevicePublicIdentity
    fun sign(payload: String): String
}

class AndroidDeviceIdentityStore(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : DeviceIdentitySigner {
    private val lock = Any()

    override fun getOrCreateIdentity(): DevicePublicIdentity = synchronized(lock) {
        val entry = getOrCreateEntry()
        DevicePublicIdentity(
            keyAlgorithm = DeviceKeyAlgorithm.ECDSA_P256_SHA256,
            publicKey = Base64.getEncoder().encodeToString(entry.certificate.publicKey.encoded),
        )
    }

    override fun sign(payload: String): String = synchronized(lock) {
        val entry = getOrCreateEntry()
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(entry.privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
    }

    private fun getOrCreateEntry(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry)?.let { return it }

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_NAME))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKeyPair()
        }

        return keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
            ?: error("AndroidKeyStore did not create device identity")
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "mason_device_identity_p256_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CURVE_NAME = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
