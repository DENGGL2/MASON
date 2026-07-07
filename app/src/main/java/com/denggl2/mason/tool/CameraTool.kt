package com.denggl2.mason.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.SizeF
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "camera"
    override val description = "拍照或查询相机信息。capture 启动系统相机拍照，info 返回相机可用性和支持的分辨率"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：capture（拍照）或 info（查询相机信息）",
            required = true,
            enum = listOf("capture", "info"),
        ),
        "flash" to ParameterDef(
            type = "boolean",
            description = "是否开启闪光灯（仅 capture 有效，部分系统相机可能忽略），默认 false",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return when (action) {
            "capture" -> capture(args["flash"])
            "info" -> getCameraInfo()
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun capture(flashStr: String?): ToolResult {
        // 检查是否有相机硬件
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return ToolResult(success = false, error = "设备不支持相机")
        }

        val useFlash = flashStr?.toBoolean() ?: false

        return try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Mason")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "photo_$timestamp.jpg")

            // 保存输出路径供回调使用
            pendingPhotoPath = file.absolutePath

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                // 尝试通过 FileProvider 提供输出路径
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: Exception) {
                    // FileProvider 未配置或无权限，降级为默认行为（相机将返回缩略图）
                    pendingPhotoPath = null
                }
                if (useFlash) {
                    putExtra("android.intent.extra.CAMERA_FLASH_ON", true)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 检查是否有相机应用可响应
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo == null) {
                return ToolResult(success = false, error = "未找到系统相机应用")
            }

            context.startActivity(intent)

            val resultData = mutableMapOf(
                "status" to "camera_launched",
                "message" to "已打开系统相机。拍照后照片将保存到 DCIM/Mason/ 目录。",
            )
            if (file.exists()) {
                resultData["file_path"] = file.absolutePath
            } else {
                resultData["expected_path"] = file.absolutePath
            }

            ToolResult(success = true, data = resultData)
        } catch (e: Exception) {
            ToolResult(success = false, error = "启动相机失败: ${e.message}")
        }
    }

    private fun getCameraInfo(): ToolResult {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return ToolResult(
                success = true,
                data = mapOf("camera_available" to "false", "message" to "设备不支持相机"),
            )
        }

        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cm == null) {
                return ToolResult(success = false, error = "无法访问 CameraManager")
            }

            val cameraIds = cm.cameraIdList
            val data = mutableMapOf<String, String>()

            data["camera_count"] = cameraIds.size.toString()
            data["camera_available"] = (cameraIds.isNotEmpty()).toString()

            cameraIds.forEach { id ->
                val characteristics = cm.getCameraCharacteristics(id)
                val prefix = "camera_$id"

                // 方向
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val facingStr = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                    CameraCharacteristics.LENS_FACING_BACK -> "后置"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "外置"
                    else -> "未知"
                }
                data["${prefix}_facing"] = "$facing ($facingStr)"

                // 闪光灯
                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                data["${prefix}_flash"] = if (flashAvailable) "支持" else "不支持"

                // 硬件等级
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    val levelStr = when (hwLevel) {
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                        else -> "未知"
                    }
                    data["${prefix}_hardware_level"] = levelStr
                }

                // 分辨率（输出格式）
                val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (streamConfigMap != null) {
                    val outputSizes = streamConfigMap.getOutputSizes(ImageFormat.JPEG)
                    if (outputSizes != null && outputSizes.isNotEmpty()) {
                        val maxRes = outputSizes.maxByOrNull { it.width * it.height }
                        if (maxRes != null) {
                            data["${prefix}_max_resolution"] = "${maxRes.width}x${maxRes.height}"
                        }
                        val topRes = outputSizes
                            .sortedByDescending { it.width * it.height }
                            .take(3)
                            .joinToString(", ") { "${it.width}x${it.height}" }
                        data["${prefix}_top_resolutions"] = topRes
                        data["${prefix}_total_output_sizes"] = outputSizes.size.toString()
                    }
                }

                // 传感器尺寸
                val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) as? SizeF
                if (sensorSize != null) {
                    data["${prefix}_sensor_size"] = "%.2f x %.2f mm".format(sensorSize.width, sensorSize.height)
                }

                // 焦距
                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    data["${prefix}_focal_lengths"] = focalLengths.joinToString(", ") { "%.2fmm".format(it) }
                }
            }

            ToolResult(success = true, data = data)
        } catch (e: CameraAccessException) {
            ToolResult(success = false, error = "访问相机失败: ${e.message}")
        } catch (e: Exception) {
            ToolResult(success = false, error = "获取相机信息异常: ${e.message}")
        }
    }

    companion object {
        @Volatile
        var pendingPhotoPath: String? = null
            private set
    }
}
