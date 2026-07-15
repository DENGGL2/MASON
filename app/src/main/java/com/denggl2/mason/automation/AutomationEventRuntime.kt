package com.denggl2.mason.automation

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.core.content.ContextCompat
import com.denggl2.mason.data.AutomationPreferencesDataStore
import com.denggl2.mason.data.SkillAutomationStore
import com.denggl2.mason.tool.ToolExecutor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class AutomationEventDispatcher @Inject constructor(
    private val store: SkillAutomationStore,
    private val runner: AutomationRunner,
    private val preferencesStore: AutomationPreferencesDataStore,
) {
    suspend fun dispatch(triggerType: String, event: Map<String, String>) {
        if (!preferencesStore.preferences.first().backgroundExecutionEnabled) return
        store.listAutomations()
            .filter { it.enabled && it.trigger.type == triggerType && it.trigger.matches(event) }
            .forEach { spec -> runner.run(spec.id, AutomationRunner.SOURCE_EVENT, event) }
    }

    private fun com.denggl2.mason.data.MasonAutomationTrigger.matches(event: Map<String, String>): Boolean {
        if (value.isBlank()) return true
        val actual = when (type) {
            AutomationScheduler.TRIGGER_WIFI -> event["ssid"]
            AutomationScheduler.TRIGGER_BLUETOOTH -> event["device"]
            AutomationScheduler.TRIGGER_NOTIFICATION ->
                listOfNotNull(event["package"], event["title"], event["text"]).joinToString(" ")
            else -> ""
        }.orEmpty()
        return actual.contains(value, ignoreCase = true)
    }
}

class AutomationEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val triggerType = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> AutomationScheduler.TRIGGER_CHARGING
            ConnectivityManager.CONNECTIVITY_ACTION -> AutomationScheduler.TRIGGER_WIFI
            "android.bluetooth.device.action.ACL_CONNECTED" -> AutomationScheduler.TRIGGER_BLUETOOTH
            else -> return
        }
        val event = when (triggerType) {
            AutomationScheduler.TRIGGER_WIFI -> {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                mapOf("ssid" to wifi?.connectionInfo?.ssid.orEmpty().removeSurrounding("\""))
            }
            AutomationScheduler.TRIGGER_BLUETOOTH -> mapOf(
                "device" to intent.getParcelableExtra<android.bluetooth.BluetoothDevice>("android.bluetooth.device.extra.DEVICE")
                    ?.name.orEmpty(),
            )
            else -> emptyMap()
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                entryPoint(context).eventDispatcher().dispatch(triggerType, event)
            } finally {
                pending.finish()
            }
        }
    }
}

@Singleton
class AutomationEventMonitor @Inject constructor() {
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            AutomationEventReceiver(),
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }
}

class MasonNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val event = mapOf(
            "package" to sbn.packageName,
            "title" to extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            "text" to extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
        )
        scope.launch {
            entryPoint(applicationContext).eventDispatcher().dispatch(
                AutomationScheduler.TRIGGER_NOTIFICATION,
                event,
            )
        }
    }
}

class AutomationLocationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(AutomationScheduler.KEY_AUTOMATION_ID) ?: return Result.failure()
        val entry = entryPoint(applicationContext)
        val spec = entry.automationStore().automation(id) ?: return Result.failure()
        val parts = spec.trigger.value.split(',')
        val targetLat = parts.getOrNull(0)?.toDoubleOrNull() ?: return Result.failure()
        val targetLng = parts.getOrNull(1)?.toDoubleOrNull() ?: return Result.failure()
        val radius = parts.getOrNull(2)?.toDoubleOrNull()?.coerceAtLeast(50.0) ?: 200.0
        val location = entry.toolExecutor().execute("location", mapOf("provider" to "auto"))
        if (!location.success) return Result.retry()
        val lat = location.data["latitude"]?.toDoubleOrNull() ?: return Result.retry()
        val lng = location.data["longitude"]?.toDoubleOrNull() ?: return Result.retry()
        val inside = distanceMeters(lat, lng, targetLat, targetLng) <= radius
        val prefs = applicationContext.getSharedPreferences("automation_event_state", Context.MODE_PRIVATE)
        val wasInside = prefs.getBoolean("location-$id", false)
        prefs.edit().putBoolean("location-$id", inside).apply()
        if (inside && !wasInside) {
            entry.automationRunner().run(
                id,
                AutomationRunner.SOURCE_EVENT,
                mapOf("latitude" to lat.toString(), "longitude" to lng.toString()),
            )
        }
        return Result.success()
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

class AutomationEventPollWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(AutomationScheduler.KEY_AUTOMATION_ID) ?: return Result.failure()
        val entry = entryPoint(applicationContext)
        val spec = entry.automationStore().automation(id) ?: return Result.failure()
        val event = when (spec.trigger.type) {
            AutomationScheduler.TRIGGER_CHARGING -> {
                val battery = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val charging = battery.isCharging
                if (!charging) emptyMap() else mapOf("charging" to "true")
            }
            AutomationScheduler.TRIGGER_WIFI -> {
                val result = entry.toolExecutor().execute("get_wifi_info", emptyMap())
                if (!result.success) emptyMap() else mapOf("ssid" to result.data["ssid"].orEmpty())
            }
            else -> emptyMap()
        }
        val active = event.isNotEmpty() && when (spec.trigger.type) {
            AutomationScheduler.TRIGGER_WIFI -> event["ssid"].orEmpty()
                .contains(spec.trigger.value, ignoreCase = true)
            else -> true
        }
        val prefs = applicationContext.getSharedPreferences("automation_event_state", Context.MODE_PRIVATE)
        val key = "poll-$id"
        val wasActive = prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, active).apply()
        if (active && !wasActive) {
            entry.eventDispatcher().dispatch(spec.trigger.type, event)
        }
        return Result.success()
    }
}

private fun entryPoint(context: Context): AutomationEventEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, AutomationEventEntryPoint::class.java)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationEventEntryPoint {
    fun eventDispatcher(): AutomationEventDispatcher
    fun automationRunner(): AutomationRunner
    fun automationStore(): SkillAutomationStore
    fun toolExecutor(): ToolExecutor
}
