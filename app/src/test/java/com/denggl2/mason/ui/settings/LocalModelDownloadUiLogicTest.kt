package com.denggl2.mason.ui.settings

import com.denggl2.mason.data.LocalModelDownloadState
import com.denggl2.mason.data.LocalModelDownloadStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelDownloadUiLogicTest {
    @Test
    fun completedDownloadDoesNotKeepProgressVisible() {
        val completed = LocalModelDownloadState(
            modelId = "model",
            status = LocalModelDownloadStatus.Completed,
            downloadedBytes = 100L,
            totalBytes = 100L,
            message = "下载并校验完成",
        )

        assertFalse(shouldShowLocalModelDownloadProgress(completed))
    }

    @Test
    fun activeAndPausedDownloadsKeepProgressVisible() {
        val downloading = LocalModelDownloadState(
            modelId = "model",
            status = LocalModelDownloadStatus.Downloading,
            downloadedBytes = 10L,
            totalBytes = 100L,
        )
        val paused = downloading.copy(status = LocalModelDownloadStatus.Paused)

        assertTrue(shouldShowLocalModelDownloadProgress(downloading))
        assertTrue(shouldShowLocalModelDownloadProgress(paused))
    }

    @Test
    fun cancelledDownloadDoesNotKeepProgressOrMessageVisible() {
        val cancelled = LocalModelDownloadState(
            modelId = "model",
            status = LocalModelDownloadStatus.Cancelled,
            downloadedBytes = 10L,
            totalBytes = 100L,
            message = "已取消",
        )

        assertFalse(shouldShowLocalModelDownloadProgress(cancelled))
    }
}
