package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class StorageTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "storage"
    override val description = "获取存储空间使用情况"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
        return ToolResult.success(mapOf("total" to stat.totalBytes.toString(), "free" to stat.availableBytes.toString()))
    }
}
