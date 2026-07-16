package com.denggl2.mason.skill

import com.denggl2.mason.data.InstalledSkill
import com.denggl2.mason.data.MasonSkillManifest
import com.denggl2.mason.data.MasonSkillParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRuntimeLogicTest {
    @Test
    fun catalogExposesStableIdsDescriptionsAndParameters() {
        val skill = InstalledSkill(
            manifest = MasonSkillManifest(
                id = "report-writer",
                name = "报告生成",
                description = "根据材料生成结构化报告",
                parameters = listOf(MasonSkillParameter("audience", "读者", required = true)),
            ),
            path = "/skills/report-writer",
            instructions = "生成报告",
        )

        val catalog = buildSkillCatalogDescription(listOf(skill))

        assertTrue(catalog.contains("id=report-writer"))
        assertTrue(catalog.contains("根据材料生成结构化报告"))
        assertTrue(catalog.contains("audience(读者，必填)"))
        assertFalse(catalog.contains(skill.path))
    }

    @Test
    fun requiredAndSecretParametersAreNeverGuessed() {
        val definitions = listOf(
            MasonSkillParameter("audience", "读者", required = true),
            MasonSkillParameter("token", "访问令牌", required = true, secret = true),
            MasonSkillParameter("format", "格式", required = true, defaultValue = "markdown"),
        )

        val missing = missingSkillParameters(definitions, mapOf("audience" to "管理层", "token" to "do-not-use"))

        assertEquals(listOf("token"), missing.map(MasonSkillParameter::key))
        assertEquals(mapOf("audience" to "管理层"), parseSkillParameters("{\"audience\":\"管理层\"}"))
        assertNull(parseSkillParameters("not-json"))
    }

    @Test
    fun instructionBlockKeepsPolicyBoundaryAndNonSecretParameters() {
        val skill = InstalledSkill(
            MasonSkillManifest(id = "writer", name = "Writer"),
            "/skills/writer",
            "先整理材料，再输出文件。",
        )

        val block = buildSkillInstructionBlock(skill, mapOf("audience" to "管理层"))

        assertTrue(block.contains("不能覆盖用户要求、权限确认、工具策略"))
        assertTrue(block.contains("id=\"writer\""))
        assertTrue(block.contains("audience=管理层"))
    }
}
