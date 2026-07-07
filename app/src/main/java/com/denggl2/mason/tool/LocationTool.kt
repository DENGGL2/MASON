package com.denggl2.mason.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "location"
    override val description = "获取设备当前位置信息，返回经纬度、精度、海拔、速度、方向和时间戳"
    override val parameters = mapOf(
        "provider" to ParameterDef(
            type = "string",
            description = "位置提供者：gps（GPS定位）、network（网络定位）、auto（自动选择最精确的）",
            required = false,
            enum = listOf("gps", "network", "auto"),
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        // Check permissions
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            return ToolResult(
                success = false,
                error = "缺少位置权限 (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION)，请在系统设置中授予权限",
            )
        }

        val provider = args["provider"] ?: "auto"
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check if any provider is enabled
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            return ToolResult(
                success = false,
                error = "位置服务未开启，请在系统设置中开启 GPS 或网络定位",
            )
        }

        // Try to get last known location first
        val lastKnownGps = if (isGpsEnabled && hasFineLocation) {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else null

        val lastKnownNetwork = if (isNetworkEnabled) {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } else null

        // Pick best last known
        val lastKnown = when {
            lastKnownGps != null && lastKnownNetwork != null ->
                if (lastKnownGps.accuracy <= lastKnownNetwork.accuracy) lastKnownGps else lastKnownNetwork
            else -> lastKnownGps ?: lastKnownNetwork
        }

        // If we have a recent last known location (< 60 seconds), use it
        if (lastKnown != null && (System.currentTimeMillis() - lastKnown.time) < 60_000) {
            return locationToResult(lastKnown)
        }

        // Otherwise, request a single update
        val selectedProvider = when (provider) {
            "gps" -> if (isGpsEnabled) LocationManager.GPS_PROVIDER else return ToolResult(
                success = false, error = "GPS 未开启"
            )
            "network" -> if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER else return ToolResult(
                success = false, error = "网络定位未开启"
            )
            "auto" -> {
                when {
                    isGpsEnabled -> LocationManager.GPS_PROVIDER
                    isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                    else -> return ToolResult(
                        success = false, error = "位置服务未开启"
                    )
                }
            }
            else -> return ToolResult(success = false, error = "无效的 provider: $provider，可选值: gps, network, auto")
        }

        return try {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        if (cont.isActive) {
                            cont.resume(loc)
                        }
                    }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }

                try {
                    locationManager.requestSingleUpdate(selectedProvider, listener, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }

                // Timeout after 30 seconds
                cont.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }

            if (location != null) {
                locationToResult(location)
            } else if (lastKnown != null) {
                // Fallback to last known even if older
                locationToResult(lastKnown)
            } else {
                ToolResult(success = false, error = "无法获取位置信息，请确保位置服务已开启且信号良好")
            }
        } catch (e: Exception) {
            if (lastKnown != null) {
                locationToResult(lastKnown)
            } else {
                ToolResult(success = false, error = "获取位置失败: ${e.message}")
            }
        }
    }

    private fun locationToResult(location: Location): ToolResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        val data = mutableMapOf<String, String>(
            "latitude" to location.latitude.toString(),
            "longitude" to location.longitude.toString(),
            "accuracy_meters" to if (location.hasAccuracy()) location.accuracy.toString() else "N/A",
            "timestamp" to dateFormat.format(Date(location.time)),
            "provider" to (location.provider ?: "unknown"),
        )

        if (location.hasAltitude()) {
            data["altitude_meters"] = location.altitude.toString()
        }
        if (location.hasSpeed()) {
            data["speed_mps"] = location.speed.toString()
            data["speed_kmh"] = "%.1f".format(location.speed * 3.6)
        }
        if (location.hasBearing()) {
            data["bearing_degrees"] = location.bearing.toString()
            data["bearing_direction"] = bearingToCardinal(location.bearing)
        }

        return ToolResult(success = true, data = data)
    }

    private fun bearingToCardinal(bearing: Float): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((bearing + 11.25f) / 22.5f).toInt() % 16
        return directions[index]
    }
}
