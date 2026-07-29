package com.denggl2.mason.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkillArchiveValidatorTest {
    @Test
    fun acceptsOneSkillUnderOneRoot() {
        archiveOf("writer/SKILL.md" to "# Writer", "writer/references/guide.md" to "guide").use { archive ->
            val validated = SkillArchiveValidator.validate(archive)
            assertEquals("writer", validated.skillRoot)
            assertEquals(setOf("SKILL.md", "references/guide.md"), validated.files.map { it.relativePath }.toSet())
        }
    }

    @Test
    fun rejectsPathTraversalAndMultipleSkills() {
        archiveOf("writer/SKILL.md" to "# Writer", "../outside.txt" to "bad").use { archive ->
            assertThrows(IllegalArgumentException::class.java) { SkillArchiveValidator.validate(archive) }
        }
        archiveOf("first/SKILL.md" to "# First", "second/SKILL.md" to "# Second").use { archive ->
            assertThrows(IllegalArgumentException::class.java) { SkillArchiveValidator.validate(archive) }
        }
    }

    @Test
    fun rejectsFilesOutsideTheSingleSkillRoot() {
        archiveOf("writer/SKILL.md" to "# Writer", "readme.txt" to "outside").use { archive ->
            assertThrows(IllegalArgumentException::class.java) { SkillArchiveValidator.validate(archive) }
        }
    }

    private fun archiveOf(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("mason-skill-validator-", ".zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun File.use(block: (File) -> Unit) {
        try {
            block(this)
        } finally {
            delete()
        }
    }
}
