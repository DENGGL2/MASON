package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class AudioRecordTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "audio_record"
    override val description = "录制音频（需要 RECORD_AUDIO 权限）"
    override val parameters: Map<String, ParameterDef> = mapOf(        "duration" to ParameterDef("string", "录制时长（秒）", required = false))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.error("Audio recording requires foreground permission - not available in direct mode")
    }
}
