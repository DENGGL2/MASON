package com.denggl2.mason.tool

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "screenshot"
    override val description = "屏幕截图，需要用户授权 MediaProjection。首次使用会触发授权弹窗，授权后自动截图保存到 /sdcard/Pictures/Mason/"
    override val parameters = emptyMap<String, ParameterDef>()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val projection = activeProjection
        if (projection != null) {
            return captureScreenshot(projection)
        }

        // 检查是否已有待处理的授权回调
        val pending = pendingAuthCallback
        if (pending != null) {
            return ToolResult(
                success = false,
                error = "等待用户授权中，请在弹出的授权窗口中确认",
                data = mapOf("status" to "awaiting_authorization"),
            )
        }

        // 首次调用：创建授权 Intent
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return ToolResult(success = false, error = "设备不支持 MediaProjection")

        val authIntent = mpManager.createScreenCaptureIntent()

        // 保存回调，等待 Activity 调用 onAuthResult
        pendingAuthCallback = AuthCallback { resultCode, data ->
            pendingAuthCallback = null
            try {
                if (resultCode != android.app.Activity.RESULT_OK || data == null) {
                    lastError = "用户拒绝了截图授权"
                    return@AuthCallback
                }
                val mp = mpManager.getMediaProjection(resultCode, data)
                activeProjection = mp
                // 注册 onStop 回调
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        activeProjection = null
                    }
                }, null)
                lastError = null
            } catch (e: Exception) {
                lastError = "授权失败: ${e.message}"
                activeProjection = null
            }
        }

        return ToolResult(
            success = true,
            data = mapOf(
                "status" to "needs_authorization",
                "message" to "需要用户授权屏幕截图。请在弹窗中点击「立即开始」，授权后请再次调用此工具完成截图。",
                "action" to "request_media_projection",
            ),
        )
    }

    private fun captureScreenshot(projection: MediaProjection): ToolResult {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics: DisplayMetrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                DisplayMetrics().also {
                    it.widthPixels = bounds.width()
                    it.heightPixels = bounds.height()
                    it.densityDpi = context.resources.displayMetrics.densityDpi
                }
            } else {
                @Suppress("DEPRECATION")
                DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            }
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            val virtualDisplay: VirtualDisplay = projection.createVirtualDisplay(
                "Screenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null, null,
            )

            // 等待一帧
            val image = imageReader.acquireLatestImage()
            if (image == null) {
                virtualDisplay.release()
                imageReader.close()
                return ToolResult(success = false, error = "截图失败：无法获取屏幕图像")
            }

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()

            image.close()
            virtualDisplay.release()
            imageReader.close()

            // 保存到 /sdcard/Pictures/Mason/
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mason")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "screenshot_$timestamp.png")

            FileOutputStream(file).use { fos ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            croppedBitmap.recycle()

            val fileSize = file.length()
            ToolResult(
                success = true,
                data = mapOf(
                    "status" to "captured",
                    "file_path" to file.absolutePath,
                    "file_size" to "${fileSize} bytes",
                    "width" to width.toString(),
                    "height" to height.toString(),
                ),
            )
        } catch (e: Exception) {
            ToolResult(success = false, error = "截图异常: ${e.message}")
        }
    }

    companion object {
        @Volatile
        var activeProjection: MediaProjection? = null
            private set

        @Volatile
        var pendingAuthCallback: AuthCallback? = null
            private set

        @Volatile
        var lastError: String? = null

        fun onAuthResult(resultCode: Int, data: Intent?) {
            pendingAuthCallback?.invoke(resultCode, data)
        }
    }
}

typealias AuthCallback = (Int, Intent?) -> Unit
