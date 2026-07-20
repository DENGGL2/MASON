package com.denggl2.mason.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProjectContext(
    val id: String,
    val displayName: String,
    val boundAtMillis: Long = System.currentTimeMillis(),
)

@Singleton
class ProjectContextStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun resolve(conversationId: String, query: String): ProjectContext? {
        val detected = detectProjectContext(query)
        if (detected != null) {
            bind(conversationId, detected)
            return detected
        }
        return get(conversationId)
    }

    fun get(conversationId: String): ProjectContext? = preferences
        .getString(conversationKey(conversationId), null)
        ?.let { payload -> runCatching { json.decodeFromString<ProjectContext>(payload) }.getOrNull() }

    fun bind(conversationId: String, project: ProjectContext) {
        if (!conversationId.isValidContextId() || project.id.isBlank()) return
        preferences.edit()
            .putString(conversationKey(conversationId), json.encodeToString(project))
            .apply()
    }

    private fun conversationKey(conversationId: String): String = "conversation:$conversationId"

    private companion object {
        const val PREFS_NAME = "mason_project_context"
    }
}

internal fun detectProjectContext(query: String): ProjectContext? {
    val candidates = listOf(
        Regex("""(?i)\bproject\s+([\p{L}\p{N}][\p{L}\p{N}._-]{0,39})"""),
        Regex("""([\p{L}\p{N}][\p{L}\p{N}._-]{0,39})\s*(?:项目|專案)"""),
    )
    val raw = candidates.asSequence()
        .mapNotNull { regex -> regex.find(query)?.groupValues?.getOrNull(1) }
        .map { value -> value.trim().removeProjectCommandPrefix() }
        .firstOrNull { value ->
            value.isNotBlank() && normalizeProjectId(value) !in genericProjectReferences
        } ?: return null
    val id = normalizeProjectId(raw)
    if (id.isBlank()) return null
    return ProjectContext(id = id, displayName = raw.take(40))
}

internal fun normalizeProjectId(value: String): String = value.trim().lowercase()
    .replace(Regex("""[^\p{L}\p{N}._-]+"""), "-")
    .trim('-')
    .take(64)

private fun String.removeProjectCommandPrefix(): String {
    var value = this
    listOf("继续", "接着", "关于", "处理", "打开", "切换到", "在").forEach { prefix ->
        if (value.startsWith(prefix) && value.length > prefix.length) value = value.removePrefix(prefix)
    }
    return value.trim()
}

private fun String.isValidContextId(): Boolean = matches(Regex("[A-Za-z0-9._-]{1,100}"))

private val genericProjectReferences = setOf(
    "a", "an", "the", "this", "that", "it", "is", "are", "was", "were", "do", "does", "can", "should",
    "we", "you", "what", "which", "current", "same", "status", "settings", "details", "language", "name",
    "context", "file", "files", "task", "plan", "requirements",
    "当前", "本", "该", "这个", "那个", "什么", "哪个", "同一个", "状态", "设置", "详情", "语言", "名称", "任务", "计划",
)
