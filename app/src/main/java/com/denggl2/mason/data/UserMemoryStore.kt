package com.denggl2.mason.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserMemoryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(readItems())

    val items: StateFlow<List<UserMemoryItem>> = _items

    suspend fun upsert(item: UserMemoryItem) {
        withContext(Dispatchers.IO) {
            val nextItems = _items.value
                .filterNot { it.id == item.id }
                .plus(item.copy(updatedAtMillis = System.currentTimeMillis()))
                .sortedByDescending { it.updatedAtMillis }
            writeItems(nextItems)
            _items.value = nextItems
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val nextItems = _items.value.filterNot { it.id == id }
            writeItems(nextItems)
            _items.value = nextItems
        }
    }

    suspend fun relevant(
        query: String,
        scopeId: String? = null,
        allowSensitive: Boolean = false,
        limit: Int = 6,
    ): List<UserMemoryItem> = withContext(Dispatchers.Default) {
        val normalized = query.lowercase()
        _items.value.asSequence()
            .filter(UserMemoryItem::enabled)
            .filter { item ->
                item.scope == UserMemoryScope.GLOBAL || item.scopeId == scopeId
            }
            .filter { item ->
                !item.sensitive || allowSensitive || normalized.contains(item.label.lowercase())
            }
            .map { item -> item to relevanceScore(item, normalized) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(limit.coerceIn(1, 12))
            .map(Pair<UserMemoryItem, Int>::first)
            .toList()
    }

    suspend fun rememberExplicitStatement(text: String): UserMemoryItem? {
        val candidate = explicitCandidate(text) ?: return null
        upsert(candidate)
        return candidate
    }

    fun explicitCandidate(text: String): UserMemoryItem? {
        val match = Regex("(?:请)?记住(?:我的)?(.+?)(?:是|为|：|:)(.+)").find(text.trim()) ?: return null
        val label = match.groupValues[1].trim('，', ',', '。', ' ')
        val value = match.groupValues[2].trim('，', ',', '。', ' ')
        if (label.isBlank() || value.isBlank() || label.length > 40 || value.length > 500) return null
        val type = when {
            label.contains("车牌") -> UserMemoryType.LICENSE_PLATE
            label.contains("地址") || label.contains("公司") || label.contains("家") -> UserMemoryType.ADDRESS
            label.contains("身份") || label.contains("姓名") -> UserMemoryType.IDENTITY
            label.contains("支付") || label.contains("账号") -> UserMemoryType.PAYMENT
            else -> UserMemoryType.OTHER
        }
        return UserMemoryItem(
            id = UUID.randomUUID().toString(),
            label = label,
            value = value,
            type = type,
            sensitive = type in setOf(UserMemoryType.IDENTITY, UserMemoryType.PAYMENT, UserMemoryType.ADDRESS),
            autoUse = type !in setOf(UserMemoryType.IDENTITY, UserMemoryType.PAYMENT, UserMemoryType.ADDRESS),
            keywords = listOf(label, type.label).distinct(),
        )
    }

    private fun relevanceScore(item: UserMemoryItem, query: String): Int {
        var score = 0
        if (query.contains(item.label.lowercase())) score += 8
        if (query.contains(item.type.label.lowercase())) score += 4
        item.keywords.forEach { keyword -> if (keyword.isNotBlank() && query.contains(keyword.lowercase())) score += 3 }
        if (item.autoUse && query.contains("我")) score += 1
        return score
    }

    private fun readItems(): List<UserMemoryItem> {
        val payload = sharedPreferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<UserMemoryItem>>(decrypt(payload))
        }.getOrDefault(emptyList())
    }

    private fun writeItems(items: List<UserMemoryItem>) {
        val payload = json.encodeToString(items)
        sharedPreferences.edit()
            .putString(KEY_ITEMS, encrypt(payload))
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return "${iv.toBase64()}:${encrypted.toBase64()}"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2) { "Invalid encrypted payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "mason_secure_memory"
        const val KEY_ITEMS = "items"
        const val KEY_ALIAS = "mason_user_memory_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }
}
