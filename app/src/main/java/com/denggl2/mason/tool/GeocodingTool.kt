package com.denggl2.mason.tool

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocodingTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "geocoding"
    override val description = "地理编码与逆地理编码：forward 将地址转为经纬度，reverse 将经纬度转为地址"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：forward（地址→坐标）、reverse（坐标→地址）",
            required = true,
            enum = listOf("forward", "reverse"),
        ),
        "address" to ParameterDef(
            type = "string",
            description = "地址字符串，action=forward 时必填",
            required = false,
        ),
        "latitude" to ParameterDef(
            type = "number",
            description = "纬度，action=reverse 时必填",
            required = false,
        ),
        "longitude" to ParameterDef(
            type = "number",
            description = "经度，action=reverse 时必填",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false, error = "缺少 action 参数"
        )

        return when (action) {
            "forward" -> forwardGeocode(args["address"])
            "reverse" -> reverseGeocode(args["latitude"], args["longitude"])
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun forwardGeocode(addressStr: String?): ToolResult {
        if (addressStr.isNullOrBlank()) {
            return ToolResult(success = false, error = "forward 操作需要 address 参数")
        }

        if (!Geocoder.isPresent()) {
            return ToolResult(success = false, error = "设备不支持地理编码服务")
        }

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address> = geocoder.getFromLocationName(addressStr, 5)
                ?: emptyList()

            if (addresses.isEmpty()) {
                return ToolResult(success = false, error = "未找到匹配 \"$addressStr\" 的坐标")
            }

            val results = addresses.mapIndexed { index, addr ->
                mapOf(
                    "index" to index.toString(),
                    "latitude" to addr.latitude.toString(),
                    "longitude" to addr.longitude.toString(),
                    "address" to (addr.getAddressLine(0) ?: addressStr),
                    "feature" to (addr.featureName ?: ""),
                    "locality" to (addr.locality ?: ""),
                    "admin_area" to (addr.adminArea ?: ""),
                    "country" to (addr.countryName ?: ""),
                )
            }

            ToolResult(
                success = true,
                data = mapOf(
                    "query" to addressStr,
                    "count" to results.size.toString(),
                    "results" to results.joinToString("\n") { addr ->
                        "[${addr["index"]}] ${addr["address"]} (${addr["latitude"]}, ${addr["longitude"]})"
                    },
                ),
            )
        } catch (e: IOException) {
            ToolResult(success = false, error = "地理编码查询失败 (网络错误): ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "地理编码失败: ${e.message}")
        }
    }

    private fun reverseGeocode(latStr: String?, lngStr: String?): ToolResult {
        if (latStr.isNullOrBlank() || lngStr.isNullOrBlank()) {
            return ToolResult(success = false, error = "reverse 操作需要 latitude 和 longitude 参数")
        }

        val latitude = latStr.toDoubleOrNull()
            ?: return ToolResult(success = false, error = "无效的 latitude: $latStr")
        val longitude = lngStr.toDoubleOrNull()
            ?: return ToolResult(success = false, error = "无效的 longitude: $lngStr")

        if (!Geocoder.isPresent()) {
            return ToolResult(success = false, error = "设备不支持地理编码服务")
        }

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1)
                ?: emptyList()

            if (addresses.isEmpty()) {
                return ToolResult(success = false, error = "未找到 ($latitude, $longitude) 对应的地址")
            }

            val addr = addresses[0]
            val data = mutableMapOf(
                "latitude" to latitude.toString(),
                "longitude" to longitude.toString(),
                "address_line" to (addr.getAddressLine(0) ?: "N/A"),
            )

            addr.locality?.let { data["city"] = it }
            addr.adminArea?.let { data["state"] = it }
            addr.countryName?.let { data["country"] = it }
            addr.countryCode?.let { data["country_code"] = it }
            addr.postalCode?.let { data["postal_code"] = it }
            addr.thoroughfare?.let { data["street"] = it }
            addr.subThoroughfare?.let { data["street_number"] = it }
            addr.featureName?.let { data["feature"] = it }
            addr.subLocality?.let { data["district"] = it }

            ToolResult(success = true, data = data)
        } catch (e: IOException) {
            ToolResult(success = false, error = "逆地理编码查询失败 (网络错误): ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "逆地理编码失败: ${e.message}")
        }
    }
}
