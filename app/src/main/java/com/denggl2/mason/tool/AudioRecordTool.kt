package com.denggl2.mason.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecordTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {
    override val name = "audio_record"
    override val description = "录音控制：启动、停止录音或查询录音状态。录音保存为 M4A/AAC 格式到 /sdcard/Music/Mason/"
    override val parameters = mapOf(
        "action" to ParameterDef(
            type = "string",
            description = "操作：start（开始录音）、stop（停止录音）、status（查询状态）",
            required = true,
            enum = listOf("start", "stop", "status"),
        ),
        "duration_seconds" to ParameterDef(
            type = "integer",
            description = "最长录音秒数，默认30，仅 action=start 时有效",
            required = false,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult(
            success = false,
            error = "缺少 action 参数",
        )

        return when (action) {
            "start" -> startRecording(args["duration_seconds"])
            "stop" -> stopRecording()
            "status" -> getStatus()
            else -> ToolResult(success = false, error = "未知 action: $action")
        }
    }

    private fun getStatus(): ToolResult {
        val isRecording = mediaRecorder != null
        val result = mutableMapOf(
            "is_recording" to isRecording.toString(),
        )
        if (isRecording) {
            result["current_file"] = currentFile ?: "未知"
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            result["elapsed_seconds"] = elapsed.toString()
        }
        return ToolResult(success = true, data = result)
    }

    private fun startRecording(durationStr: String?): ToolResult {
        if (mediaRecorder != null) {
            return ToolResult(success = false, error = "已在录音中")
        }

        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(success = false, error = "缺少录音权限 (RECORD_AUDIO)，请在系统设置中授予权限")
        }

        val maxDurationSecs = durationStr?.toIntOrNull() ?: 30
        if (maxDurationSecs < 1 || maxDurationSecs > 3600) {
            return ToolResult(success = false, error = "duration_seconds 必须在 1-3600 之间")
        }

        return try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Mason")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "recording_$timestamp.m4a")
            val filePath = file.absolutePath

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(filePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setAudioChannels(1)
                setMaxDuration(maxDurationSecs * 1000)

                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopAndCleanup()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    lastError = "录音错误: what=$what extra=$extra"
                    stopAndCleanup()
                }

                prepare()
                start()
            }

            mediaRecorder = recorder
            currentFile = filePath
            startTime = System.currentTimeMillis()

            ToolResult(
                success = true,
                data = mapOf(
                    "status" to "recording",
                    "file_path" to filePath,
                    "max_duration_seconds" to maxDurationSecs.toString(),
                ),
            )
        } catch (e: Exception) {
            mediaRecorder = null
            currentFile = null
            ToolResult(success = false, error = "启动录音失败: ${e.message}")
        }
    }

    private fun stopRecording(): ToolResult {
        val recorder = mediaRecorder
        if (recorder == null) {
            return ToolResult(success = false, error = "当前没有正在进行的录音")
        }

        return try {
            val filePath = currentFile ?: ""
            val elapsed = (System.currentTimeMillis() - startTime) / 1000

            stopAndCleanup()

            val file = File(filePath)
            val fileSize = if (file.exists()) file.length() else 0L

            ToolResult(
                success = true,
                data = mapOf(
                    "status" to "stopped",
                    "file_path" to filePath,
                    "duration_seconds" to elapsed.toString(),
                    "file_size" to "${fileSize} bytes",
                ),
            )
        } catch (e: Exception) {
            mediaRecorder = null
            currentFile = null
            ToolResult(success = false, error = "停止录音失败: ${e.message}")
        }
    }

    private fun stopAndCleanup() {
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }
    }

    companion object {
        @Volatile
        var mediaRecorder: MediaRecorder? = null
            private set

        @Volatile
        var currentFile: String? = null
            private set

        @Volatile
        var startTime: Long = 0
            private set

        @Volatile
        var lastError: String? = null
    }
}
