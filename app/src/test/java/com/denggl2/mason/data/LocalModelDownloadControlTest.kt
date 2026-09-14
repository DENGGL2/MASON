package com.denggl2.mason.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelDownloadControlTest {
    @Test
    fun pauseKeepsProgressWhileCancelResetsProgress() {
        val diskState = LocalModelDownloadState(
            modelId = "model",
            status = LocalModelDownloadStatus.Paused,
            downloadedBytes = 12_345L,
            totalBytes = 99_999L,
        )

        val paused = stoppedDownloadState(diskState, LocalModelDownloadStatus.Paused)
        val cancelled = stoppedDownloadState(diskState, LocalModelDownloadStatus.Cancelled)

        assertEquals(12_345L, paused.downloadedBytes)
        assertEquals(0L, cancelled.downloadedBytes)
        assertEquals(LocalModelDownloadStatus.Paused, paused.status)
        assertEquals(LocalModelDownloadStatus.Cancelled, cancelled.status)
        assertEquals("已暂停，可继续下载", paused.message)
        assertEquals(null, cancelled.message)
    }

    @Test
    fun downloadFinalNotificationOnlyPostsWhileAppIsInBackground() {
        assertTrue(
            shouldPostDownloadFinalNotification(
                appForeground = false,
                status = LocalModelDownloadStatus.Completed,
            ),
        )
        assertFalse(
            shouldPostDownloadFinalNotification(
                appForeground = true,
                status = LocalModelDownloadStatus.Completed,
            ),
        )
        assertFalse(
            shouldPostDownloadFinalNotification(
                appForeground = false,
                status = LocalModelDownloadStatus.Cancelled,
            ),
        )
    }

    @Test
    fun cancellingCoroutineCancelsTheInFlightHttpCall() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val call = OkHttpClient().newCall(
                Request.Builder().url(server.url("/model")).build(),
            )
            val job = launch(Dispatchers.IO) {
                call.executeCancellable { response -> response.body?.string() }
            }

            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(2_000L) { job.cancelAndJoin() }

            assertTrue(call.isCanceled())
        } finally {
            server.shutdown()
        }
    }
}
