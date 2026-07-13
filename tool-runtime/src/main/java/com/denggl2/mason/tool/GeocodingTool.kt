package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class GeocodingTool constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "geocoding"
    override val description = "地理编码：坐标转地址"
    override val parameters: Map<String, ParameterDef> = mapOf(        "lat" to ParameterDef("string", "纬度", required = true),
        "lng" to ParameterDef("string", "经度", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val lat = args["lat"]?.toDoubleOrNull() ?: return ToolResult.error("Invalid lat")
        val lng = args["lng"]?.toDoubleOrNull() ?: return ToolResult.error("Invalid lng")
        val geocoder = android.location.Geocoder(context)
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        return if (addresses.isNullOrEmpty()) ToolResult.error("No address found")
        else ToolResult.success(mapOf("address" to addresses[0].getAddressLine(0)))
    }
}
