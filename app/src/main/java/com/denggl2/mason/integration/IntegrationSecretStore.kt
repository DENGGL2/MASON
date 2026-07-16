package com.denggl2.mason.integration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(value: String, existingRef: String? = null): String {
        val ref = existingRef ?: UUID.randomUUID().toString()
        preferences.edit().putString(ref, encrypt(value)).apply()
        return ref
    }

    fun get(ref: String?): String = ref
        ?.let { preferences.getString(it, null) }
        ?.let(::decrypt)
        .orEmpty()

    fun remove(ref: String?) {
        if (ref != null) preferences.edit().remove(ref).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return "${cipher.iv.toBase64()}:${cipher.doFinal(value.toByteArray(Charsets.UTF_8)).toBase64()}"
    }

    private fun decrypt(payload: String): String = runCatching {
        val (iv, encrypted) = payload.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(iv, Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrDefault("")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "mason_integration_secrets"
        const val KEY_ALIAS = "mason_integration_secret_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }
}
