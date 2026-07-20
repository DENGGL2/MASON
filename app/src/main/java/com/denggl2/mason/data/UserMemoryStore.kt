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

    suspend fun saveEvaluatedMemory(
        evaluation: MemoryWriteEvaluation,
        scope: UserMemoryScope = UserMemoryScope.GLOBAL,
        scopeId: String? = null,
    ): MemorySaveOutcome {
        require(evaluation.accepted) { evaluation.reason }
        require(scope == UserMemoryScope.GLOBAL || !scopeId.isNullOrBlank()) {
            "Scoped memory requires a scope ID"
        }
        val existing = _items.value.firstOrNull { item ->
            item.scope == scope &&
                item.scopeId == scopeId &&
                item.label.normalizedMemoryText() == evaluation.label.normalizedMemoryText()
        }
        val unchanged = existing?.value?.normalizedMemoryText() == evaluation.value.normalizedMemoryText()
        val item = UserMemoryItem(
            id = existing?.id ?: UUID.randomUUID().toString(),
            label = evaluation.label,
            value = evaluation.value,
            type = evaluation.type,
            sensitive = evaluation.sensitive,
            autoUse = !evaluation.sensitive,
            scope = scope,
            scopeId = scopeId,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            keywords = (existing?.keywords.orEmpty() + evaluation.label + evaluation.type.label).distinct(),
        )
        upsert(item)
        return MemorySaveOutcome(
            item = item,
            status = when {
                existing == null -> MemorySaveStatus.Created
                unchanged -> MemorySaveStatus.Unchanged
                else -> MemorySaveStatus.Updated
            },
        )
    }

    suspend fun relevant(
        query: String,
        conversationScopeId: String? = null,
        projectScopeId: String? = null,
        allowSensitive: Boolean = false,
        limit: Int = 6,
    ): List<UserMemoryItem> {
        val selected = withContext(Dispatchers.Default) {
            rankRelevantMemories(
            items = _items.value,
            query = query,
            conversationScopeId = conversationScopeId,
            projectScopeId = projectScopeId,
            allowSensitive = allowSensitive,
            limit = limit,
            )
        }
        if (selected.isNotEmpty()) withContext(Dispatchers.IO) {
            val selectedIds = selected.map(UserMemoryItem::id).toSet()
            val now = System.currentTimeMillis()
            val nextItems = _items.value.map { item ->
                if (item.id in selectedIds) item.copy(lastUsedAtMillis = now) else item
            }
            writeItems(nextItems)
            _items.value = nextItems
        }
        return selected
    }

    suspend fun rememberExplicitStatement(text: String): UserMemoryItem? {
        val candidate = explicitCandidate(text) ?: return null
        val existing = _items.value.firstOrNull { item ->
            item.scope == candidate.scope &&
                item.scopeId == candidate.scopeId &&
                item.label.normalizedMemoryText() == candidate.label.normalizedMemoryText()
        }
        val resolved = candidate.copy(
            id = existing?.id ?: candidate.id,
            createdAtMillis = existing?.createdAtMillis ?: candidate.createdAtMillis,
        )
        upsert(resolved)
        return resolved
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
            sensitive = type in sensitiveMemoryTypes,
            autoUse = type !in sensitiveMemoryTypes,
            keywords = listOf(label, type.label).distinct(),
        )
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

enum class MemorySaveStatus { Created, Updated, Unchanged }

data class MemorySaveOutcome(
    val item: UserMemoryItem,
    val status: MemorySaveStatus,
)

internal fun rankRelevantMemories(
    items: List<UserMemoryItem>,
    query: String,
    conversationScopeId: String? = null,
    projectScopeId: String? = null,
    allowSensitive: Boolean = false,
    limit: Int = 6,
): List<UserMemoryItem> {
    val normalizedQuery = query.normalizedMemoryText()
    val intentTerms = memoryIntentTerms(normalizedQuery)
    return items.asSequence()
        .filter(UserMemoryItem::enabled)
        .filter { item ->
            when (item.scope) {
                UserMemoryScope.GLOBAL -> true
                UserMemoryScope.PROJECT -> projectScopeId != null && item.scopeId == projectScopeId
                UserMemoryScope.CONVERSATION -> conversationScopeId != null && item.scopeId == conversationScopeId
            }
        }
        .filter { item ->
            !item.sensitive || allowSensitive || normalizedQuery.contains(item.label.normalizedMemoryText())
        }
        .map { item ->
            val projectContextScore = if (
                item.scope == UserMemoryScope.PROJECT && item.scopeId == projectScopeId && item.autoUse
            ) 2 else 0
            item to (memoryRelevanceScore(item, normalizedQuery, intentTerms) + projectContextScore)
        }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<UserMemoryItem, Int>> { it.second }
                .thenByDescending { it.first.lastUsedAtMillis ?: it.first.updatedAtMillis },
        )
        .take(limit.coerceIn(1, 12))
        .map(Pair<UserMemoryItem, Int>::first)
        .toList()
}

private fun memoryRelevanceScore(
    item: UserMemoryItem,
    query: String,
    intentTerms: Set<String>,
): Int {
    var score = 0
    val label = item.label.normalizedMemoryText()
    if (label.isNotBlank() && query.contains(label)) score += 10
    if (item.type.memoryAliases.any(intentTerms::contains)) score += 6
    item.keywords.forEach { keyword ->
        val normalized = keyword.normalizedMemoryText()
        if (normalized.length >= 2 && query.contains(normalized)) score += 4
    }
    return score
}

private fun memoryIntentTerms(query: String): Set<String> = buildSet {
    memoryAliasesByType.values.flatten().forEach { alias ->
        if (query.contains(alias)) add(alias)
    }
}

private val UserMemoryType.memoryAliases: Set<String>
    get() = memoryAliasesByType[this].orEmpty()

private val memoryAliasesByType = mapOf(
    UserMemoryType.LICENSE_PLATE to setOf("车牌", "车号"),
    UserMemoryType.IDENTITY to setOf("身份", "姓名", "名字", "称呼", "叫什么"),
    UserMemoryType.ADDRESS to setOf("地址", "住址", "家里", "公司位置"),
    UserMemoryType.PAYMENT to setOf("支付", "账号", "收款"),
    UserMemoryType.OTHER to emptySet(),
)

private fun String.normalizedMemoryText(): String = lowercase()
    .replace(Regex("[\\s，。！？、,.!?:：;；_-]+"), "")
