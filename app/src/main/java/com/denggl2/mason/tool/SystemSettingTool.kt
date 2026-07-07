package com.denggl2.mason.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemSettingTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "system_setting"
    override val description = "读取或设置系统设置项（亮度、音量、勿扰模式）"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作类型：get（读取）或 set（设置）",
            required = true,
            enum = listOf("get", "set"),
        ),
        "key" to ParameterDef(
            type = "string",
            description = "设置项：brightness（亮度）/ volume_media（媒体音量）/ volume_ring（铃声音量）/ do_not_disturb（勿扰模式）",
            required = true,
            enum = listOf("brightness", "volume_media", "volume_ring", "do_not_disturb"),
        ),
        "value" to ParameterDef(
            type = "string",
            description = "要设置的值（action=set 时必填）。亮度: 0-255，音量: 0-最大音量，勿扰: 1（开启）或 0（关闭）",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )
        val key = args["key"] ?: return ToolResult(
            success = false,
            error = "缺少 key 参数",
        )

        return when (action) {
            "get" -> getSetting(key)
            "set" -> setSetting(key, args["value"])
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun getSetting(key: String): ToolResult {
        return try {
            when (key) {
                "brightness" -> {
                    val brightness = try {
                        Settings.System.getInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS
                        )
                    } catch (_: Settings.SettingNotFoundException) {
                        -1
                    }
                    val mode = try {
                        Settings.System.getInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE
                        )
                    } catch (_: Settings.SettingNotFoundException) {
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    }
                    ToolResult(
                        success = true,
                        data = mapOf(
                            "brightness" to brightness.toString(),
                            "brightness_percent" to "${brightness * 100 / 255}%",
                            "mode" to if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "自动" else "手动",
                        ),
                    )
                }
                "volume_media", "volume_ring" -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val streamType = when (key) {
                        "volume_media" -> AudioManager.STREAM_MUSIC
                        "volume_ring" -> AudioManager.STREAM_RING
                        else -> AudioManager.STREAM_MUSIC
                    }
                    val current = am.getStreamVolume(streamType)
                    val max = am.getStreamMaxVolume(streamType)
                    ToolResult(
                        success = true,
                        data = mapOf(
                            key to current.toString(),
                            "${key}_max" to max.toString(),
                            "${key}_percent" to "${current * 100 / max.coerceAtLeast(1)}%",
                        ),
                    )
                }
                "do_not_disturb" -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val dndEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        nm.isNotificationPolicyAccessGranted &&
                                nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                    } else {
                        try {
                            Settings.Global.getInt(
                                context.contentResolver,
                                "zen_mode"
                            ) != 0
                        } catch (_: Settings.SettingNotFoundException) {
                            false
                        }
                    }
                    ToolResult(
                        success = true,
                        data = mapOf(
                            "do_not_disturb" to if (dndEnabled) "1" else "0",
                            "status" to if (dndEnabled) "已开启" else "已关闭",
                        ),
                    )
                }
                else -> ToolResult(success = false, error = "未知 key: $key")
            }
        } catch (e: Exception) {
            ToolResult(success = false, error = "读取设置失败: ${e.message}")
        }
    }

    private fun setSetting(key: String, value: String?): ToolResult {
        if (value == null) {
            return ToolResult(success = false, error = "value 参数不能为空")
        }

        return try {
            when (key) {
                "brightness" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_SETTINGS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return ToolResult(
                                success = false,
                                error = "缺少系统设置写入权限 (WRITE_SETTINGS)，请在设置中授予",
                            )
                        }
                    }

                    val brightnessValue = value.toIntOrNull()
                    if (brightnessValue == null || brightnessValue < 0 || brightnessValue > 255) {
                        return ToolResult(
                            success = false,
                            error = "亮度值必须为 0-255 之间的整数",
                        )
                    }

                    // 先切换到手动模式
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        brightnessValue
                    )
                    ToolResult(
                        success = true,
                        data = mapOf(
                            "brightness" to brightnessValue.toString(),
                            "brightness_percent" to "${brightnessValue * 100 / 255}%",
                            "status" to "已设置",
                        ),
                    )
                }
                "volume_media", "volume_ring" -> {
                    val volumeValue = value.toIntOrNull()
                    if (volumeValue == null || volumeValue < 0) {
                        return ToolResult(
                            success = false,
                            error = "音量值必须为非负整数",
                        )
                    }
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val streamType = when (key) {
                        "volume_media" -> AudioManager.STREAM_MUSIC
                        "volume_ring" -> AudioManager.STREAM_RING
                        else -> AudioManager.STREAM_MUSIC
                    }
                    val max = am.getStreamMaxVolume(streamType)
                    val clampedValue = volumeValue.coerceAtMost(max)
                    am.setStreamVolume(streamType, clampedValue, 0)
                    ToolResult(
                        success = true,
                        data = mapOf(
                            key to clampedValue.toString(),
                            "${key}_max" to max.toString(),
                            "status" to "已设置",
                        ),
                    )
                }
                "do_not_disturb" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        if (!nm.isNotificationPolicyAccessGranted) {
                            return ToolResult(
                                success = false,
                                error = "缺少勿扰模式权限 (NOTIFICATION_POLICY_ACCESS)，请在设置中授予",
                            )
                        }
                        if (value == "1") {
                            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
                        } else {
                            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
                    } else {
                        Settings.Global.putInt(
                            context.contentResolver,
                            "zen_mode",
                            if (value == "1") 1 else 0
                        )
                    }
                    ToolResult(
                        success = true,
                        data = mapOf(
                            "do_not_disturb" to value,
                            "status" to if (value == "1") "已开启勿扰模式" else "已关闭勿扰模式",
                        ),
                    )
                }
                else -> ToolResult(success = false, error = "未知 key: $key")
            }
        } catch (e: SecurityException) {
            ToolResult(
                success = false,
                error = "权限不足，请在设置中授予相应权限: ${e.message}",
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                error = "设置失败: ${e.message}",
            )
        }
    }
}
