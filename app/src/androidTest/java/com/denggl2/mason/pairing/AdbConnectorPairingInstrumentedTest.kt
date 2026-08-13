package com.denggl2.mason.pairing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denggl2.mason.protocol.DeviceCapability
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.sync.remote.PairedConnector
import com.denggl2.mason.sync.remote.PairedConnectorStore
import com.denggl2.mason.sync.remote.PinnedPairingHttpsClient
import com.denggl2.mason.sync.remote.PinnedConnectorClient
import com.denggl2.mason.sync.remote.RemotePairingException
import com.denggl2.mason.sync.security.AndroidDeviceIdentityStore
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdbConnectorPairingInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun pairFromAdbInstrumentationArgument() = runBlocking {
        val encoded = InstrumentationRegistry.getArguments().getString(BOOTSTRAP_ARGUMENT)
            ?: error("Missing instrumentation argument: $BOOTSTRAP_ARGUMENT")
        val rawBootstrap = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            .toString(Charsets.UTF_8)
        val bootstrap = MasonProtocolJson.decode<PairingBootstrap>(rawBootstrap)
        val deviceId = getOrCreateDeviceId()
        val client = PinnedPairingHttpsClient(bootstrap, AndroidDeviceIdentityStore())
        try {
            client.pair(
                deviceId = deviceId,
                displayName = Build.MODEL.ifBlank { "Android device" },
                capabilities = setOf(
                    DeviceCapability.ANDROID_TOOLS,
                    DeviceCapability.FILE_RECEIVE,
                ),
                requestedPermissions = REQUIRED_PERMISSIONS,
            )
        } catch (error: RemotePairingException) {
            if (error.errorCode != "DEVICE_ALREADY_PAIRED") throw error
        }

        val grant = client.authenticate(deviceId)
        val session = client.getSession(grant.sessionToken)
        assertEquals(deviceId, session.deviceId)
        assertTrue(session.permissions.containsAll(REQUIRED_PERMISSIONS))

        val connector = PairedConnector(
            connectorDeviceId = bootstrap.offer.connectorDeviceId,
            endpoint = bootstrap.endpoint.trimEnd('/'),
            tlsCertificateSha256 = bootstrap.tlsCertificateSha256.lowercase(),
            pairedAt = System.currentTimeMillis(),
            displayName = bootstrap.connectorDisplayName.trim().ifBlank { "电脑" },
        )
        PairedConnectorStore(context).save(connector)
        assertEquals(connector, PairedConnectorStore(context).load())
    }

    @Test
    fun remoteControlRoundTripFromAdbArguments() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val threadQuery = arguments.getString(THREAD_QUERY_ARGUMENT)
            ?: error("Missing instrumentation argument: $THREAD_QUERY_ARGUMENT")
        val connector = PairedConnectorStore(context).load()
            ?: error("MASON is not paired with a Connector")
        val deviceId = getOrCreateDeviceId()
        val client = PinnedConnectorClient(connector, AndroidDeviceIdentityStore())
        val conversation = client.listConversations(deviceId, limit = 30)
            .conversations
            .firstOrNull { it.title.contains(threadQuery, ignoreCase = true) }
            ?: error("No remote conversation title contains: $threadQuery")

        val started = client.sendMessage(
            deviceId = deviceId,
            threadId = conversation.threadId,
            text = "Reply exactly MASON_REMOTE_OK",
        )
        assertEquals(RemoteExecutionStatus.RUNNING, started.status)
        val completed = awaitDetail(client, deviceId, conversation.threadId) { detail ->
            detail.executionStatus != RemoteExecutionStatus.RUNNING &&
                detail.messages.lastOrNull()?.text?.contains("MASON_REMOTE_OK") == true
        }
        assertEquals(RemoteExecutionStatus.COMPLETED, completed.executionStatus)

        val longTurn = client.sendMessage(
            deviceId = deviceId,
            threadId = conversation.threadId,
            text = STOP_TEST_MESSAGE,
        )
        assertEquals(RemoteExecutionStatus.RUNNING, longTurn.status)
        delay(INTERRUPT_DELAY_MILLIS)
        val interrupt = client.interrupt(deviceId, conversation.threadId)
        assertEquals(longTurn.turnId, interrupt.turnId)
        val stopped = awaitDetail(client, deviceId, conversation.threadId) { detail ->
            detail.executionStatus == RemoteExecutionStatus.INTERRUPTED
        }
        assertEquals(RemoteExecutionStatus.INTERRUPTED, stopped.executionStatus)
    }

    @Test
    fun remoteInterruptFromAdbArguments() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val threadQuery = arguments.getString(THREAD_QUERY_ARGUMENT)
            ?: error("Missing instrumentation argument: $THREAD_QUERY_ARGUMENT")
        val connector = PairedConnectorStore(context).load()
            ?: error("MASON is not paired with a Connector")
        val deviceId = getOrCreateDeviceId()
        val client = PinnedConnectorClient(connector, AndroidDeviceIdentityStore())
        val conversation = client.listConversations(deviceId, limit = 30)
            .conversations
            .firstOrNull { it.title.contains(threadQuery, ignoreCase = true) }
            ?: error("No remote conversation title contains: $threadQuery")

        val started = client.sendMessage(deviceId, conversation.threadId, STOP_TEST_MESSAGE)
        assertEquals(RemoteExecutionStatus.RUNNING, started.status)
        delay(INTERRUPT_DELAY_MILLIS)
        val interrupt = client.interrupt(deviceId, conversation.threadId)
        assertEquals(started.turnId, interrupt.turnId)
        val stopped = awaitDetail(client, deviceId, conversation.threadId) { detail ->
            detail.executionStatus == RemoteExecutionStatus.INTERRUPTED
        }
        assertEquals(RemoteExecutionStatus.INTERRUPTED, stopped.executionStatus)
    }

    @Test
    fun logRemoteConversationIndex() = runBlocking {
        val connector = PairedConnectorStore(context).load()
            ?: error("MASON is not paired with a Connector")
        val deviceId = getOrCreateDeviceId()
        PinnedConnectorClient(connector, AndroidDeviceIdentityStore())
            .listConversations(deviceId, limit = 30)
            .conversations
            .forEachIndexed { index, conversation ->
                Log.i(
                    LOG_TAG,
                    "REMOTE_THREAD|$index|${conversation.threadId}|${conversation.title}",
                )
            }
    }

    private suspend fun awaitDetail(
        client: PinnedConnectorClient,
        deviceId: String,
        threadId: String,
        predicate: (RemoteConversationDetail) -> Boolean,
    ): RemoteConversationDetail = withTimeout(ROUND_TRIP_TIMEOUT_MILLIS) {
        while (true) {
            val detail = client.readConversation(deviceId, threadId)
            if (predicate(detail)) return@withTimeout detail
            delay(POLL_INTERVAL_MILLIS)
        }
        error("Unreachable")
    }

    private fun getOrCreateDeviceId(): String {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        check(databaseFile.isFile) { "MASON local database does not exist" }
        return SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.readDeviceId() ?: UUID.randomUUID().toString().also { deviceId ->
                database.execSQL(
                    "INSERT INTO local_device(singleton_id, device_id, created_at) VALUES(1, ?, ?)",
                    arrayOf<Any>(deviceId, System.currentTimeMillis()),
                )
            }
        }
    }

    private fun SQLiteDatabase.readDeviceId(): String? = rawQuery(
        "SELECT device_id FROM local_device WHERE singleton_id = 1",
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else cursor.getString(0).takeIf(String::isNotBlank)
    }

    private companion object {
        const val BOOTSTRAP_ARGUMENT = "bootstrapBase64"
        const val THREAD_QUERY_ARGUMENT = "threadQuery"
        const val DATABASE_NAME = "mason_database.db"
        const val POLL_INTERVAL_MILLIS = 1_500L
        const val INTERRUPT_DELAY_MILLIS = 1_000L
        const val ROUND_TRIP_TIMEOUT_MILLIS = 180_000L
        const val LOG_TAG = "MasonAdbPairingTest"
        const val STOP_TEST_MESSAGE =
            "Run a PowerShell command that waits for 60 seconds, then reply MASON_STOP_TEST."
        val REQUIRED_PERMISSIONS = setOf(
            DevicePermission.VIEW_SHARED_CONVERSATIONS,
            DevicePermission.SEND_MESSAGES,
            DevicePermission.CONTROL_EXECUTION,
        )
    }
}
