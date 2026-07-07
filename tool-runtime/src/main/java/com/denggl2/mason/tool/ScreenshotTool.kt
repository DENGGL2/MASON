package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class ScreenshotTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "screenshot"
    override val description = "截取当前屏幕"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.error("Screenshot requires MediaProjection callback - not available in direct mode")
    }
}
