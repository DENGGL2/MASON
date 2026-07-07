package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class CameraTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "camera"
    override val description = "使用相机拍照"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                return ToolResult.error("Camera requires UI interaction - not available in direct mode")
    }
}
