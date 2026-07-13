package com.denggl2.mason.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MasonSkillManifest(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val source: String = "local",
    val entry: String = "SKILL.md",
    val permissions: List<String> = emptyList(),
    val version: String = "1.0.0",
)

@Serializable
data class MasonAutomationTrigger(
    val type: String,
    val value: String = "",
)

@Serializable
data class MasonAutomationAction(
    val type: String,
    val arguments: Map<String, String> = emptyMap(),
)

@Serializable
data class MasonAutomationSpec(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val trigger: MasonAutomationTrigger,
    val actions: List<MasonAutomationAction>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class MasonAutomationRunLog(
    val automationId: String,
    val status: String,
    val message: String,
    val ranAt: Long = System.currentTimeMillis(),
)

@Singleton
class SkillAutomationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun listSkills(): List<MasonSkillManifest> = withContext(Dispatchers.IO) {
        skillsRoot().listFiles()
            .orEmpty()
            .filter { it.isDirectory || it.isFile }
            .mapNotNull(::readSkillManifest)
            .sortedBy { it.name.lowercase() }
    }

    suspend fun setSkillEnabled(skillId: String, enabled: Boolean): MasonSkillManifest? =
        withContext(Dispatchers.IO) {
            val folder = File(skillsRoot(), skillId)
            val manifestFile = File(folder, SKILL_MANIFEST)
            val current = readSkillManifest(folder) ?: return@withContext null
            val next = current.copy(enabled = enabled)
            folder.mkdirs()
            manifestFile.writeText(json.encodeToString(next), Charsets.UTF_8)
            next
        }

    suspend fun saveAutomation(spec: MasonAutomationSpec): MasonAutomationSpec =
        withContext(Dispatchers.IO) {
            val folder = File(automationsRoot(), spec.id)
            folder.mkdirs()
            val next = spec.copy(updatedAt = System.currentTimeMillis())
            File(folder, AUTOMATION_MANIFEST).writeText(json.encodeToString(next), Charsets.UTF_8)
            next
        }

    suspend fun listAutomations(): List<MasonAutomationSpec> = withContext(Dispatchers.IO) {
        automationsRoot().listFiles()
            .orEmpty()
            .filter { it.isDirectory || it.isFile }
            .mapNotNull(::readAutomationSpec)
            .sortedByDescending { it.updatedAt }
    }

    suspend fun appendAutomationLog(log: MasonAutomationRunLog) = withContext(Dispatchers.IO) {
        val folder = File(automationsRoot(), log.automationId).also { it.mkdirs() }
        val logFile = File(folder, AUTOMATION_LOG)
        val logs = readAutomationLogs(log.automationId).takeLast(MAX_LOGS - 1) + log
        logFile.writeText(
            json.encodeToString(ListSerializer(MasonAutomationRunLog.serializer()), logs),
            Charsets.UTF_8,
        )
    }

    suspend fun readAutomationLogs(automationId: String): List<MasonAutomationRunLog> =
        withContext(Dispatchers.IO) {
            val logFile = File(File(automationsRoot(), automationId), AUTOMATION_LOG)
            if (!logFile.exists() || !logFile.isFile) return@withContext emptyList()
            runCatching {
                json.decodeFromString(
                    ListSerializer(MasonAutomationRunLog.serializer()),
                    logFile.readText(Charsets.UTF_8),
                )
            }.getOrDefault(emptyList())
        }

    private fun readSkillManifest(file: File): MasonSkillManifest? {
        val manifestFile = if (file.isDirectory) File(file, SKILL_MANIFEST) else file
        val fallbackId = file.nameWithoutExtension.ifBlank { file.name }
        val fallbackName = fallbackId.replace('-', ' ').replace('_', ' ').trim()
        return if (manifestFile.exists() && manifestFile.isFile && manifestFile.name == SKILL_MANIFEST) {
            runCatching {
                json.decodeFromString(MasonSkillManifest.serializer(), manifestFile.readText(Charsets.UTF_8))
            }.getOrNull()
        } else {
            val skillFile = if (file.isDirectory) File(file, "SKILL.md") else file
            if (!skillFile.exists() || !skillFile.isFile) return null
            MasonSkillManifest(
                id = fallbackId,
                name = skillFile.readFirstHeading() ?: fallbackName.ifBlank { fallbackId },
                description = skillFile.readFirstParagraph().orEmpty(),
                entry = skillFile.name,
            )
        }
    }

    private fun readAutomationSpec(file: File): MasonAutomationSpec? {
        val manifestFile = if (file.isDirectory) File(file, AUTOMATION_MANIFEST) else file
        if (!manifestFile.exists() || !manifestFile.isFile || manifestFile.name != AUTOMATION_MANIFEST) {
            return null
        }
        return runCatching {
            json.decodeFromString(MasonAutomationSpec.serializer(), manifestFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun skillsRoot(): File =
        File(context.filesDir, "skills").also { it.mkdirs() }

    private fun automationsRoot(): File =
        File(context.filesDir, "automations").also { it.mkdirs() }

    private fun File.readFirstHeading(): String? =
        runCatching {
            readLines(Charsets.UTF_8)
                .firstOrNull { it.trimStart().startsWith("#") }
                ?.trim()
                ?.trimStart('#')
                ?.trim()
        }.getOrNull()

    private fun File.readFirstParagraph(): String? =
        runCatching {
            readLines(Charsets.UTF_8)
                .asSequence()
                .map { it.trim() }
                .firstOrNull { line ->
                    line.isNotBlank() &&
                        !line.startsWith("#") &&
                        !line.startsWith("---") &&
                        !line.startsWith("name:", ignoreCase = true)
                }
                ?.take(220)
        }.getOrNull()

    private companion object {
        const val SKILL_MANIFEST = "skill.json"
        const val AUTOMATION_MANIFEST = "automation.json"
        const val AUTOMATION_LOG = "runs.json"
        const val MAX_LOGS = 80
    }
}
