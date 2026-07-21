package com.denggl2.mason.data

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
    val dependencies: List<String> = emptyList(),
    val parameters: List<MasonSkillParameter> = emptyList(),
    val archived: Boolean = false,
)

@Serializable
data class MasonSkillParameter(
    val key: String,
    val label: String,
    val required: Boolean = false,
    val defaultValue: String = "",
    val secret: Boolean = false,
)

data class SkillSafetyReport(
    val safe: Boolean,
    val warnings: List<String>,
)

data class InstalledSkill(
    val manifest: MasonSkillManifest,
    val path: String,
    val instructions: String,
)

internal fun evaluateSkillSafety(
    manifest: MasonSkillManifest,
    instructions: String,
    enabledSkillIds: Set<String>,
): SkillSafetyReport {
    val warnings = buildList {
        val unknownPermissions = manifest.permissions
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it in KNOWN_SKILL_PERMISSIONS }
        if (unknownPermissions.isNotEmpty()) {
            add("声明了不支持的权限：${unknownPermissions.joinToString()}")
        }
        if (manifest.permissions.any { it.equals("shell", true) || it.equals("root", true) }) {
            add("声明了高风险系统执行权限")
        }
        if (instructions.contains(DANGEROUS_SKILL_INSTRUCTION)) {
            add("说明中包含高风险命令模式")
        }
        val missing = manifest.dependencies.filterNot(enabledSkillIds::contains)
        if (missing.isNotEmpty()) add("缺少已启用依赖：${missing.distinct().joinToString()}")
    }
    return SkillSafetyReport(warnings.isEmpty(), warnings)
}

private val KNOWN_SKILL_PERMISSIONS = setOf(
    "network", "files", "calendar", "location", "contacts", "sms", "camera", "microphone",
    "notifications", "clipboard", "shell", "root",
)

private val DANGEROUS_SKILL_INSTRUCTION = Regex("(?i)(rm -rf|rmdir /s|del /s|curl.+\\|.+sh)")

@Serializable
data class MasonAutomationTrigger(
    val type: String,
    val value: String = "",
)

@Serializable
data class MasonAutomationAction(
    val type: String,
    val arguments: Map<String, String> = emptyMap(),
    val id: String = "",
    val title: String = "",
    val inputKey: String = "",
    val outputKey: String = "",
    val condition: MasonAutomationCondition? = null,
    val continueOnFailure: Boolean = false,
)

@Serializable
data class MasonAutomationCondition(
    val key: String,
    val operator: String = "not_empty",
    val value: String = "",
)

@Serializable
data class MasonAutomationConstraints(
    val network: String = "none",
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
)

@Serializable
data class MasonAutomationSpec(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val trigger: MasonAutomationTrigger,
    val actions: List<MasonAutomationAction>,
    val constraints: MasonAutomationConstraints = MasonAutomationConstraints(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

@Serializable
data class MasonAutomationStepLog(
    val actionId: String,
    val title: String,
    val status: String,
    val message: String,
    val startedAt: Long,
    val finishedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class MasonAutomationRunLog(
    val automationId: String,
    val status: String,
    val message: String,
    val source: String = "manual",
    val artifactPath: String? = null,
    val ranAt: Long = System.currentTimeMillis(),
    val steps: List<MasonAutomationStepLog> = emptyList(),
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun listSkills(): List<MasonSkillManifest> = withContext(Dispatchers.IO) {
        listInstalledSkillsInternal().filterNot { it.manifest.archived }.map(InstalledSkill::manifest)
    }

    suspend fun listInstalledSkills(enabledOnly: Boolean = false): List<InstalledSkill> =
        withContext(Dispatchers.IO) {
            listInstalledSkillsInternal()
                .filterNot { it.manifest.archived }
                .filter { !enabledOnly || it.manifest.enabled }
        }

    suspend fun setSkillEnabled(skillId: String, enabled: Boolean): MasonSkillManifest? =
        withContext(Dispatchers.IO) {
            val installed = listInstalledSkillsInternal()
                .firstOrNull { it.manifest.id == skillId }
                ?: return@withContext null
            setSkillEnabledInternal(installed.path, enabled)
        }

    suspend fun setSkillEnabledAtPath(path: String, enabled: Boolean): MasonSkillManifest? =
        withContext(Dispatchers.IO) {
            setSkillEnabledInternal(path, enabled)
        }

    suspend fun installFromGitHub(rawUrl: String): InstalledSkill = withContext(Dispatchers.IO) {
        val source = parseGitHubSource(rawUrl)
        val archive = File.createTempFile("mason-skill-", ".zip", context.cacheDir)
        try {
            var downloadedRef: String? = null
            for (ref in source.refs) {
                if (downloadGitHubArchive(source, ref, archive)) {
                    downloadedRef = ref
                    break
                }
            }
            checkNotNull(downloadedRef) {
                "GitHub 仓库没有找到 main 或 master 分支，请使用包含 /tree/分支名/ 的链接"
            }
            installArchive(archive, source)
        } finally {
            archive.delete()
        }
    }

    suspend fun updateFromGitHub(skillId: String): InstalledSkill = withContext(Dispatchers.IO) {
        val current = listInstalledSkillsInternal().firstOrNull { it.manifest.id == skillId }
            ?: error("Skill 未安装：$skillId")
        require(current.manifest.source.startsWith("https://github.com/")) { "这个 Skill 不是从 GitHub 安装的" }
        val source = parseGitHubSource(current.manifest.source)
        val archive = File.createTempFile("mason-skill-update-", ".zip", context.cacheDir)
        try {
            val downloaded = source.refs.firstOrNull { downloadGitHubArchive(source, it, archive) }
            checkNotNull(downloaded) { "GitHub 仓库没有找到可更新分支" }
            installArchive(archive, source, replaceSkillId = skillId)
        } finally {
            archive.delete()
        }
    }

    suspend fun archiveSkill(skillId: String): MasonSkillManifest? = withContext(Dispatchers.IO) {
        val installed = listInstalledSkillsInternal().firstOrNull { it.manifest.id == skillId }
            ?: return@withContext null
        val folder = File(installed.path)
        val next = installed.manifest.copy(enabled = false, archived = true)
        File(folder, SKILL_MANIFEST).writeText(json.encodeToString(next), Charsets.UTF_8)
        next
    }

    suspend fun safetyReport(skillId: String): SkillSafetyReport = withContext(Dispatchers.IO) {
        val installed = listInstalledSkillsInternal()
        val skill = installed.firstOrNull { it.manifest.id == skillId }
            ?: return@withContext SkillSafetyReport(false, listOf("Skill 未安装"))
        evaluateSkillSafety(
            manifest = skill.manifest,
            instructions = skill.instructions,
            enabledSkillIds = installed.filter { it.manifest.enabled }.map { it.manifest.id }.toSet(),
        )
    }

    suspend fun saveAutomation(spec: MasonAutomationSpec): MasonAutomationSpec =
        withContext(Dispatchers.IO) {
            require(spec.id.matches(Regex("[a-z0-9._-]{1,80}"))) { "自动化 ID 格式不正确" }
            require(spec.name.isNotBlank()) { "自动化名称不能为空" }
            require(spec.actions.isNotEmpty()) { "自动化至少需要一个动作" }
            val folder = File(automationsRoot(), spec.id)
            check(folder.isInside(automationsRoot())) { "自动化路径不安全" }
            folder.mkdirs()
            val next = spec.copy(updatedAt = System.currentTimeMillis())
            File(folder, AUTOMATION_MANIFEST).writeText(json.encodeToString(next), Charsets.UTF_8)
            next
        }

    suspend fun automation(automationId: String): MasonAutomationSpec? = withContext(Dispatchers.IO) {
        if (!automationId.isSafeAutomationId()) return@withContext null
        readAutomationSpec(File(automationsRoot(), automationId))
    }

    suspend fun setAutomationEnabled(
        automationId: String,
        enabled: Boolean,
    ): MasonAutomationSpec? = withContext(Dispatchers.IO) {
        if (!automationId.isSafeAutomationId()) return@withContext null
        val current = readAutomationSpec(File(automationsRoot(), automationId))
            ?: return@withContext null
        saveAutomation(current.copy(enabled = enabled))
    }

    suspend fun listAutomations(): List<MasonAutomationSpec> = withContext(Dispatchers.IO) {
        automationsRoot().listFiles()
            .orEmpty()
            .filter { it.isDirectory || it.isFile }
            .mapNotNull(::readAutomationSpec)
            .filterNot { it.archived }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun archiveAutomation(automationId: String): MasonAutomationSpec? = withContext(Dispatchers.IO) {
        if (!automationId.isSafeAutomationId()) return@withContext null
        val current = readAutomationSpec(File(automationsRoot(), automationId)) ?: return@withContext null
        saveAutomation(current.copy(enabled = false, archived = true))
    }

    suspend fun appendAutomationLog(log: MasonAutomationRunLog) = withContext(Dispatchers.IO) {
        require(log.automationId.isSafeAutomationId()) { "自动化 ID 格式不正确" }
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
            if (!automationId.isSafeAutomationId()) return@withContext emptyList()
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
                    .let { manifest -> manifest.copy(description = manifest.description.cleanSkillDescription()) }
            }.getOrNull()
        } else {
            val skillFile = if (file.isDirectory) File(file, "SKILL.md") else file
            if (!skillFile.exists() || !skillFile.isFile) return null
            MasonSkillManifest(
                id = fallbackId,
                name = skillFile.readFirstHeading() ?: fallbackName.ifBlank { fallbackId },
                description = skillFile.readFrontMatterValue("description")
                    ?: skillFile.readFirstParagraph().orEmpty(),
                entry = skillFile.name,
            )
        }
    }

    private fun listInstalledSkillsInternal(): List<InstalledSkill> = skillRoots()
        .distinctBy { it.safeCanonicalPath() }
        .flatMap { root ->
            root.listFiles()
                .orEmpty()
                .filter { it.isDirectory || it.isFile }
                .mapNotNull(::readInstalledSkill)
        }
        .distinctBy { it.path }
        .sortedBy { it.manifest.name.lowercase() }

    private fun readInstalledSkill(file: File): InstalledSkill? {
        val manifest = readSkillManifest(file) ?: return null
        val entryFile = if (file.isDirectory) File(file, manifest.entry) else file
        val safeEntry = if (file.isDirectory) entryFile.isInside(file) else entryFile == file
        if (!entryFile.exists() || !entryFile.isFile || !safeEntry) return null
        val instructions = runCatching {
            entryFile.readText(Charsets.UTF_8).take(MAX_SKILL_INSTRUCTIONS_CHARS)
        }.getOrNull() ?: return null
        return InstalledSkill(
            manifest = manifest,
            path = file.absolutePath,
            instructions = instructions,
        )
    }

    private fun setSkillEnabledInternal(path: String, enabled: Boolean): MasonSkillManifest? {
        val skill = File(path)
        if (!skill.isDirectory || skillRoots().none { skill.isInside(it) }) return null
        val current = readSkillManifest(skill) ?: return null
        val next = current.copy(enabled = enabled, archived = if (enabled) false else current.archived)
        File(skill, SKILL_MANIFEST).writeText(json.encodeToString(next), Charsets.UTF_8)
        return next
    }

    private fun parseGitHubSource(rawUrl: String): GitHubSource {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
            ?: error("GitHub 链接格式不正确")
        val host = uri.host?.lowercase()
        require(uri.scheme == "https" && (host == "github.com" || host == "www.github.com")) {
            "仅支持 https://github.com/ 开头的公开仓库链接"
        }
        val segments = uri.path.trim('/').split('/').filter(String::isNotBlank)
        require(segments.size >= 2) { "GitHub 链接缺少仓库名称" }
        val owner = segments[0].validatedGitHubPart("仓库所有者")
        val repository = segments[1].removeSuffix(".git").validatedGitHubPart("仓库名称")
        val treeIndex = segments.indexOf("tree")
        val explicitRef = treeIndex.takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }
        val subPath = treeIndex.takeIf { it >= 0 }
            ?.let { segments.drop(it + 2).joinToString("/") }
            .orEmpty()
        return GitHubSource(
            owner = owner,
            repository = repository,
            refs = explicitRef?.let(::listOf) ?: listOf("main", "master"),
            subPath = subPath,
            originalUrl = rawUrl.trim(),
        )
    }

    private fun downloadGitHubArchive(source: GitHubSource, ref: String, target: File): Boolean {
        val request = Request.Builder()
            .url("https://codeload.github.com/${source.owner}/${source.repository}/zip/$ref")
            .header("User-Agent", "Mason-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return false
            check(response.isSuccessful) { "GitHub 下载失败：HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "GitHub 没有返回仓库内容" }
            val declaredLength = body.contentLength()
            check(declaredLength < 0L || declaredLength <= MAX_ARCHIVE_BYTES) {
                "Skill 压缩包超过 ${MAX_ARCHIVE_BYTES / 1024 / 1024} MB 限制"
            }
            body.byteStream().use { input ->
                FileOutputStream(target, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_ARCHIVE_BYTES) {
                            "Skill 压缩包超过 ${MAX_ARCHIVE_BYTES / 1024 / 1024} MB 限制"
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        return true
    }

    private fun installArchive(
        archive: File,
        source: GitHubSource,
        replaceSkillId: String? = null,
    ): InstalledSkill {
        ZipFile(archive).use { zip ->
            val files = zip.entries().asSequence()
                .filterNot(ZipEntry::isDirectory)
                .map { entry -> entry to entry.safeRepositoryPath() }
                .toList()

            val requestedRoot = source.subPath.trim('/').takeIf(String::isNotBlank)
            val skillFiles = files.filter { (_, path) ->
                path == "SKILL.md" || path.endsWith("/SKILL.md")
            }
            val skillEntry = if (requestedRoot != null) {
                skillFiles.firstOrNull { (_, path) -> path == "$requestedRoot/SKILL.md" }
                    ?: error("指定目录中没有 SKILL.md")
            } else {
                skillFiles.firstOrNull { (_, path) -> path == "SKILL.md" }
                    ?: skillFiles.singleOrNull()
                    ?: error("仓库包含多个 Skill，请使用 GitHub 的 /tree/分支名/技能目录 链接")
            }
            val installRoot = skillEntry.second.substringBeforeLast('/', "")
            val selected = files.mapNotNull { (entry, path) ->
                val relative = when {
                    installRoot.isBlank() -> path
                    path == installRoot -> ""
                    path.startsWith("$installRoot/") -> path.removePrefix("$installRoot/")
                    else -> return@mapNotNull null
                }
                if (relative.isBlank()) null else ArchiveFile(entry, relative)
            }
            check(selected.isNotEmpty()) { "Skill 目录为空" }
            check(selected.size <= MAX_ARCHIVE_FILES) { "Skill 文件数量超过 $MAX_ARCHIVE_FILES 个" }
            val declaredSize = selected.sumOf { it.entry.size.coerceAtLeast(0L) }
            check(declaredSize <= MAX_EXTRACTED_BYTES) {
                "Skill 解压后超过 ${MAX_EXTRACTED_BYTES / 1024 / 1024} MB 限制"
            }

            val archivedManifest = selected.firstOrNull { it.relativePath == SKILL_MANIFEST }
                ?.let { item ->
                    runCatching {
                        zip.getInputStream(item.entry).bufferedReader(Charsets.UTF_8).use { reader ->
                            json.decodeFromString(MasonSkillManifest.serializer(), reader.readText())
                        }
                    }.getOrNull()
                }
            val entryName = archivedManifest?.entry ?: "SKILL.md"
            check(selected.any { it.relativePath == entryName }) { "Skill 入口文件不存在：$entryName" }
            val fallbackId = installRoot.substringAfterLast('/').ifBlank { source.repository }
            val skillId = (replaceSkillId ?: archivedManifest?.id ?: fallbackId).toSafeSkillId()
            val baseManifest = archivedManifest ?: MasonSkillManifest(
                id = skillId,
                name = skillEntry.first.let { entry ->
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText().lineSequence()
                            .firstOrNull { it.trimStart().startsWith("#") }
                            ?.trim()?.trimStart('#')?.trim()
                            ?: skillId
                    }
                },
                description = "",
            )
            val instructions = selected.first { it.relativePath == entryName }.let { item ->
                zip.getInputStream(item.entry).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText().take(MAX_SKILL_INSTRUCTIONS_CHARS)
                }
            }
            val enabledSkillIds = listInstalledSkillsInternal()
                .filter { it.manifest.enabled && it.manifest.id != replaceSkillId }
                .map { it.manifest.id }
                .toSet()
            val safety = evaluateSkillSafety(
                manifest = baseManifest.copy(id = skillId),
                instructions = instructions,
                enabledSkillIds = enabledSkillIds,
            )
            check(safety.safe) { "Skill 安全检查未通过：${safety.warnings.joinToString()}" }
            val target = File(skillsRoot(), skillId)
            check(replaceSkillId != null || !target.exists()) { "Skill $skillId 已安装，请先处理现有版本" }
            check(target.exists() || target.mkdirs()) { "无法创建 Skill 目录" }

            val targetRoot = target.canonicalFile
            var extractedBytes = 0L
            selected.forEach { item ->
                val output = File(target, item.relativePath).canonicalFile
                check(output.path.startsWith(targetRoot.path + File.separator)) { "Skill 包含不安全路径" }
                output.parentFile?.mkdirs()
                zip.getInputStream(item.entry).use { input ->
                    FileOutputStream(output).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var fileBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            fileBytes += count
                            extractedBytes += count
                            check(fileBytes <= MAX_SINGLE_FILE_BYTES) { "Skill 中存在过大的单个文件" }
                            check(extractedBytes <= MAX_EXTRACTED_BYTES) { "Skill 解压内容超过限制" }
                            stream.write(buffer, 0, count)
                        }
                    }
                }
            }

            val installedManifest = baseManifest.copy(
                id = skillId,
                enabled = true,
                source = source.originalUrl,
                archived = false,
            )
            File(target, SKILL_MANIFEST).writeText(json.encodeToString(installedManifest), Charsets.UTF_8)
            return checkNotNull(readInstalledSkill(target)) { "Skill 入口文件不存在：${installedManifest.entry}" }
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

    private fun skillRoots(): List<File> = listOfNotNull(
        skillsRoot(),
        context.getExternalFilesDir(null)?.let { File(it, "skills") },
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mason/skills"),
    ).filter { it.exists() && it.isDirectory }

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

    private fun File.readFrontMatterValue(key: String): String? = runCatching {
        val lines = readLines(Charsets.UTF_8)
        if (lines.firstOrNull()?.trim() != "---") return@runCatching null
        lines.drop(1)
            .takeWhile { it.trim() != "---" }
            .firstOrNull { it.trimStart().startsWith("$key:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf(String::isNotBlank)
            ?.take(220)
    }.getOrNull()

    private fun File.isInside(root: File): Boolean {
        val candidate = if (isDirectory) this else parentFile ?: return false
        val rootPath = root.canonicalFile.path
        val candidatePath = candidate.canonicalFile.path
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    private fun File.safeCanonicalPath(): String = runCatching { canonicalPath }.getOrDefault(absolutePath)

    private fun String.validatedGitHubPart(label: String): String {
        require(matches(Regex("[A-Za-z0-9_.-]+"))) { "$label 包含不支持的字符" }
        return this
    }

    private fun String.toSafeSkillId(): String =
        lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.').take(80)
            .ifBlank { "skill-${System.currentTimeMillis()}" }

    private fun String.cleanSkillDescription(): String =
        if (startsWith("description:", ignoreCase = true)) substringAfter(':').trim() else trim()

    private fun String.isSafeAutomationId(): Boolean = matches(Regex("[a-z0-9._-]{1,80}"))

    private fun ZipEntry.safeRepositoryPath(): String {
        val normalized = name.replace('\\', '/').trimStart('/')
        check(normalized.isNotBlank() && ':' !in normalized) { "Skill 包含不安全路径" }
        val parts = normalized.split('/')
        check(parts.none { it == ".." }) { "Skill 包含不安全路径" }
        return parts.drop(1).joinToString("/")
    }

    private companion object {
        const val SKILL_MANIFEST = "skill.json"
        const val AUTOMATION_MANIFEST = "automation.json"
        const val AUTOMATION_LOG = "runs.json"
        const val MAX_LOGS = 80
        const val MAX_SKILL_INSTRUCTIONS_CHARS = 32_000
        const val MAX_ARCHIVE_BYTES = 20L * 1024L * 1024L
        const val MAX_EXTRACTED_BYTES = 50L * 1024L * 1024L
        const val MAX_SINGLE_FILE_BYTES = 8L * 1024L * 1024L
        const val MAX_ARCHIVE_FILES = 500
    }

    private data class GitHubSource(
        val owner: String,
        val repository: String,
        val refs: List<String>,
        val subPath: String,
        val originalUrl: String,
    )

    private data class ArchiveFile(
        val entry: ZipEntry,
        val relativePath: String,
    )
}
