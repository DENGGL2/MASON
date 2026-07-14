package com.denggl2.mason.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

enum class LocalModelDownloadStatus {
    Idle,
    Checking,
    Downloading,
    Paused,
    Verifying,
    Completed,
    Failed,
}

data class LocalModelDownloadState(
    val modelId: String,
    val status: LocalModelDownloadStatus = LocalModelDownloadStatus.Idle,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Singleton
class LocalModelDownloader @Inject constructor(
    private val localModelStore: LocalModelStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun stateFor(model: LocalModelPreset): LocalModelDownloadState {
        val downloaded = localModelStore.partialDownloadBytes(model.id)
        return LocalModelDownloadState(
            modelId = model.id,
            status = if (downloaded > 0L) LocalModelDownloadStatus.Paused else LocalModelDownloadStatus.Idle,
            downloadedBytes = downloaded,
            totalBytes = model.expectedSizeBytes,
            message = if (downloaded > 0L) "可继续上次下载" else null,
        )
    }

    suspend fun download(
        model: LocalModelPreset,
        onProgress: (LocalModelDownloadState) -> Unit,
    ): LocalModelFileState = withContext(Dispatchers.IO) {
        val target = localModelStore.modelFileForDownload(model.id)
        val partial = localModelStore.partialFileForDownload(model.id)
        target.parentFile?.mkdirs()

        check(!target.exists()) { "模型已安装，无需重复下载" }
        val existingBytes = partial.takeIf { it.isFile }?.length() ?: 0L
        check(existingBytes <= model.expectedSizeBytes) {
            "未完成文件大小异常，请删除后重新下载"
        }
        val remainingBytes = (model.expectedSizeBytes - existingBytes).coerceAtLeast(0L)
        val safetyMargin = 128L * 1024L * 1024L
        val availableBytes = localModelStore.availableStorageBytes()
        check(availableBytes > remainingBytes + safetyMargin) {
            "存储空间不足，还需要约 ${formatDownloadBytes(remainingBytes + safetyMargin)}"
        }

        onProgress(
            LocalModelDownloadState(
                modelId = model.id,
                status = LocalModelDownloadStatus.Checking,
                downloadedBytes = existingBytes,
                totalBytes = model.expectedSizeBytes,
                message = "正在连接官方模型源",
            ),
        )

        if (existingBytes < model.expectedSizeBytes) {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .apply {
                    if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
                }
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "模型源返回 HTTP ${response.code}" }
                val append = existingBytes > 0L && response.code == 206
                var downloadedBytes = if (append) existingBytes else 0L
                val body = requireNotNull(response.body) { "模型源没有返回文件内容" }
                val totalBytes = model.expectedSizeBytes.takeIf { it > 0L }
                    ?: (downloadedBytes + body.contentLength().coerceAtLeast(0L))

                onProgress(
                    LocalModelDownloadState(
                        modelId = model.id,
                        status = LocalModelDownloadStatus.Downloading,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                    ),
                )

                body.byteStream().use { input ->
                    FileOutputStream(partial, append).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
                        var lastUpdateAt = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 250L) {
                                lastUpdateAt = now
                                onProgress(
                                    LocalModelDownloadState(
                                        modelId = model.id,
                                        status = LocalModelDownloadStatus.Downloading,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        onProgress(
            LocalModelDownloadState(
                modelId = model.id,
                status = LocalModelDownloadStatus.Verifying,
                downloadedBytes = partial.length(),
                totalBytes = model.expectedSizeBytes,
                message = "正在校验模型文件",
            ),
        )

        check(partial.length() == model.expectedSizeBytes) {
            "文件大小校验失败：${formatDownloadBytes(partial.length())}/${formatDownloadBytes(model.expectedSizeBytes)}"
        }
        check(sha256(partial).equals(model.sha256, ignoreCase = true)) {
            "SHA-256 校验失败，请重新下载"
        }
        check(!target.exists()) { "目标模型文件已存在，请先刷新状态" }
        check(partial.renameTo(target)) { "模型文件安装失败" }

        onProgress(
            LocalModelDownloadState(
                modelId = model.id,
                status = LocalModelDownloadStatus.Completed,
                downloadedBytes = model.expectedSizeBytes,
                totalBytes = model.expectedSizeBytes,
                message = "下载并校验完成",
            ),
        )
        localModelStore.stateFor(model)
    }

    private suspend fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal fun formatDownloadBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 0.1) return "%.2f GB".format(java.util.Locale.US, gb)
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(java.util.Locale.US, mb)
}
