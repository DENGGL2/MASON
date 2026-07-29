package com.denggl2.mason.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class ValidatedSkillArchive(
    val skillRoot: String,
    val files: List<ArchiveSkillFile>,
)

internal data class ArchiveSkillFile(
    val entry: ZipEntry,
    val relativePath: String,
)

/** Strict central-directory validation before any archive entry is extracted. */
internal object SkillArchiveValidator {
    fun validate(archive: File): ValidatedSkillArchive {
        require(archive.isFile) { "Skill 压缩包不存在" }
        require(archive.length() <= MAX_ARCHIVE_BYTES) { "Skill 压缩包超过 20 MB 限制" }
        ZipFile(archive).use { zip ->
            val seen = mutableSetOf<String>()
            val entries = zip.entries().asSequence().mapNotNull { entry ->
                if (entry.isDirectory) return@mapNotNull null
                val path = normalize(entry.name)
                require(seen.add(path)) { "Skill 压缩包包含重复条目：$path" }
                require(entry.size < 0L || entry.size <= MAX_SINGLE_FILE_BYTES) { "Skill 中存在过大的单个文件" }
                entry to path
            }.toList()
            require(entries.isNotEmpty()) { "Skill 压缩包为空" }
            require(entries.size <= MAX_ARCHIVE_FILES) { "Skill 文件数量超过 $MAX_ARCHIVE_FILES 个" }
            require(entries.sumOf { (entry, _) -> entry.size.coerceAtLeast(0L) } <= MAX_EXTRACTED_BYTES) {
                "Skill 解压后超过 40 MB 限制"
            }

            val skillEntries = entries.filter { (_, path) -> path == "SKILL.md" || path.endsWith("/SKILL.md") }
            require(skillEntries.size == 1) { "压缩包必须且只能包含一个 Skill（一个 SKILL.md）" }
            val root = skillEntries.single().second.substringBeforeLast('/', "")
            val selected = entries.map { (entry, path) ->
                val relative = if (root.isBlank()) path else {
                    require(path.startsWith("$root/")) { "压缩包包含 Skill 根目录外的文件" }
                    path.removePrefix("$root/")
                }
                ArchiveSkillFile(entry, relative)
            }
            require(selected.any { it.relativePath == "SKILL.md" }) { "Skill 缺少 SKILL.md" }
            return ValidatedSkillArchive(root, selected)
        }
    }

    private fun normalize(raw: String): String {
        val path = raw.replace('\\', '/')
        require(path.isNotBlank() && !path.startsWith('/') && !WINDOWS_ABSOLUTE.matches(path)) { "Skill 包含绝对路径" }
        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) { "Skill 包含不安全路径" }
        return parts.joinToString("/")
    }

    private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:.*")
    private const val MAX_ARCHIVE_BYTES = 20L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 40L * 1024L * 1024L
    private const val MAX_SINGLE_FILE_BYTES = 8L * 1024L * 1024L
    private const val MAX_ARCHIVE_FILES = 500
}
