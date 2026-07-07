package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class LocationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "location"
    override val description = "获取当前位置"
    override val parameters: Map<String, ParameterDef> = mapOf()

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val last = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        return if (last != null) ToolResult.success(mapOf("lat" to last.latitude.toString(), "lng" to last.longitude.toString()))
        else ToolResult.error("Location unavailable")
    }
}
