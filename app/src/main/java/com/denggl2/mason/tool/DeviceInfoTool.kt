package com.denggl2.mason.tool

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

@Singleton
class DeviceInfoTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "get_device_info"
    override val description = "获取设备基本信息：品牌、型号、Android版本、SDK级别、系统语言、屏幕分辨率、DPI"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val info = mutableMapOf<String, String>()

        info["brand"] = Build.BRAND
        info["model"] = Build.MODEL
        info["manufacturer"] = Build.MANUFACTURER
        info["product"] = Build.PRODUCT
        info["device"] = Build.DEVICE
        info["board"] = Build.BOARD
        info["android_version"] = Build.VERSION.RELEASE
        info["sdk_level"] = Build.VERSION.SDK_INT.toString()
        info["security_patch"] = Build.VERSION.SECURITY_PATCH ?: "未知"
        info["build_type"] = Build.TYPE
        info["build_fingerprint"] = Build.FINGERPRINT ?: "未知"

        // 语言
        info["locale"] = Locale.getDefault().toString()

        // 屏幕信息
        val metrics = context.resources.displayMetrics
        info["resolution"] = "${metrics.widthPixels}x${metrics.heightPixels}"
        info["density_dpi"] = "${metrics.densityDpi} DPI"
        info["density_scale"] = "%.1fx".format(metrics.density)

        // 是否支持 Treble
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val cls = Class.forName("android.os.SystemProperties")
                val method = cls.getMethod("get", String::class.java, String::class.java)
                val treble = method.invoke(null, "ro.treble.enabled", "false") as String
                info["treble_support"] = if (treble == "true") "支持" else "不支持"
            } catch (_: Exception) {
                info["treble_support"] = "未知"
            }
        }

        return ToolResult(success = true, data = info)
    }
}
