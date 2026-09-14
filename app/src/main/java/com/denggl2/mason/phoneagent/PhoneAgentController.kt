package com.denggl2.mason.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.denggl2.mason.tool.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PhoneAgentController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlay: PhoneAgentOverlayController,
) {
    private val json = Json { encodeDefaults = true }
    private val eventVersion = AtomicLong(1L)
    private val _runtimeState = MutableStateFlow(PhoneAgentRuntimeState())
    val runtimeState = _runtimeState.asStateFlow()

    @Volatile
    private var service: MasonAccessibilityService? = null
    private var latestNodeIndex: PhoneAgentNodeIndex? = null

    fun attach(service: MasonAccessibilityService) {
        this.service = service
        invalidateSnapshot(service.rootInActiveWindow?.packageName?.toString())
        _runtimeState.value = _runtimeState.value.copy(serviceConnected = true)
    }

    fun detach(service: MasonAccessibilityService) {
        if (this.service !== service) return
        this.service = null
        latestNodeIndex = null
        _runtimeState.value = _runtimeState.value.copy(
            serviceConnected = false,
            latestSnapshotVersion = null,
        )
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        invalidateSnapshot(event.packageName?.toString())
    }

    fun onInterrupt() {
        latestNodeIndex = null
        _runtimeState.value = _runtimeState.value.copy(latestSnapshotVersion = null)
    }

    fun pause() {
        latestNodeIndex = null
        _runtimeState.value = _runtimeState.value.copy(
            paused = true,
            latestSnapshotVersion = null,
        )
    }

    fun resume() {
        _runtimeState.value = _runtimeState.value.copy(paused = false)
    }

    suspend fun observe(maxNodes: Int): ToolResult = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext unavailableResult()
        val root = activeService.rootInActiveWindow
            ?: return@withContext ToolResult.error("当前没有可读取的活动窗口")
        try {
            val version = eventVersion.get()
            val limitedMaxNodes = maxNodes.coerceIn(20, MAX_RETURNED_NODES)
            val paths = linkedMapOf<String, List<Int>>()
            val nodes = mutableListOf<PhoneAgentNodeSnapshot>()
            var visited = 0
            var truncated = false

            fun visit(node: AccessibilityNodeInfo, path: List<Int>, depth: Int) {
                if (visited >= MAX_VISITED_NODES || nodes.size >= limitedMaxNodes || depth > MAX_DEPTH) {
                    truncated = true
                    return
                }
                visited += 1
                if (node.isUsefulForAgent()) {
                    val nodeId = "v${version}_n${nodes.size}"
                    paths[nodeId] = path
                    nodes += node.toSnapshot(nodeId)
                }
                for (childIndex in 0 until node.childCount) {
                    if (nodes.size >= limitedMaxNodes || visited >= MAX_VISITED_NODES) {
                        truncated = true
                        break
                    }
                    val child = node.getChild(childIndex) ?: continue
                    try {
                        visit(child, path + childIndex, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }

            visit(root, emptyList(), 0)
            val (width, height) = screenSize()
            val snapshot = PhoneAgentScreenSnapshot(
                version = version,
                capturedAt = System.currentTimeMillis(),
                packageName = root.packageName?.toString(),
                windowTitle = runCatching { root.window?.title?.toString() }.getOrNull(),
                width = width,
                height = height,
                nodes = nodes,
                truncated = truncated,
            )
            latestNodeIndex = PhoneAgentNodeIndex(version, paths)
            _runtimeState.value = _runtimeState.value.copy(
                activePackage = snapshot.packageName,
                latestSnapshotVersion = version,
            )
            ToolResult.success(
                mapOf(
                    "snapshot_version" to version.toString(),
                    "package_name" to snapshot.packageName.orEmpty(),
                    "window_title" to snapshot.windowTitle.orEmpty(),
                    "node_count" to nodes.size.toString(),
                    "truncated" to truncated.toString(),
                    "snapshot_json" to json.encodeToString(snapshot),
                    "node_id_rule" to "界面变化后节点 ID 会失效；执行动作前若收到过新界面，请重新调用 phone_observe。",
                ),
            )
        } finally {
            root.recycle()
        }
    }

    suspend fun clickNode(nodeId: String): ToolResult = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext unavailableResult()
        val resolved = resolveNode(activeService, nodeId)
        val node = resolved.node ?: return@withContext ToolResult.error(resolved.error.orEmpty())
        val bounds = Rect()
        val clicked = try {
            node.getBoundsInScreen(bounds)
            overlay.pulse(PhoneAgentPoint(bounds.centerX(), bounds.centerY()))
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            node.recycle()
        }
        if (clicked) {
            ToolResult.success(mapOf("node_id" to nodeId, "method" to "accessibility_action"))
        } else {
            val fallback = dispatchTap(activeService, bounds.centerX(), bounds.centerY())
            if (fallback) {
                ToolResult.success(mapOf("node_id" to nodeId, "method" to "center_coordinate_fallback"))
            } else {
                ToolResult.error("控件点击失败，界面可能已变化")
            }
        }
    }

    suspend fun nodeCenter(nodeId: String): PhoneAgentPoint? = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext null
        val resolved = resolveNode(activeService, nodeId)
        val node = resolved.node ?: return@withContext null
        val bounds = Rect()
        try {
            node.getBoundsInScreen(bounds)
            PhoneAgentPoint(bounds.centerX(), bounds.centerY())
        } finally {
            node.recycle()
        }
    }

    suspend fun tap(x: Int, y: Int): ToolResult = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext unavailableResult()
        val (width, height) = screenSize()
        if (x !in 0 until width || y !in 0 until height) {
            return@withContext ToolResult.error("点击坐标超出屏幕范围：$width x $height")
        }
        overlay.pulse(PhoneAgentPoint(x, y))
        if (dispatchTap(activeService, x, y)) {
            ToolResult.success(mapOf("x" to x.toString(), "y" to y.toString()))
        } else {
            ToolResult.error("系统拒绝执行点击手势")
        }
    }

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long,
    ): ToolResult = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext unavailableResult()
        val (width, height) = screenSize()
        val points = listOf(startX to startY, endX to endY)
        if (points.any { (x, y) -> x !in 0 until width || y !in 0 until height }) {
            return@withContext ToolResult.error("滑动坐标超出屏幕范围：$width x $height")
        }
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val duration = durationMs.coerceIn(100L, 2_000L)
        val completed = dispatchGesture(
            activeService,
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
                .build(),
        )
        if (completed) {
            ToolResult.success(
                mapOf(
                    "start" to "$startX,$startY",
                    "end" to "$endX,$endY",
                    "duration_ms" to duration.toString(),
                ),
            )
        } else {
            ToolResult.error("系统拒绝执行滑动手势")
        }
    }

    suspend fun scroll(nodeId: String, direction: String): ToolResult = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext unavailableResult()
        val resolved = resolveNode(activeService, nodeId)
        val node = resolved.node ?: return@withContext ToolResult.error(resolved.error.orEmpty())
        val action = when (direction) {
            "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> {
                node.recycle()
                return@withContext ToolResult.error("direction 仅支持 forward 或 backward")
            }
        }
        val success = try {
            node.performAction(action)
        } finally {
            node.recycle()
        }
        if (success) {
            ToolResult.success(mapOf("node_id" to nodeId, "direction" to direction))
        } else {
            ToolResult.error("控件不支持该方向的滚动")
        }
    }

    suspend fun setText(nodeId: String, text: String, append: Boolean): ToolResult =
        withContext(Dispatchers.Main.immediate) {
            val activeService = readyService() ?: return@withContext unavailableResult()
            val resolved = resolveNode(activeService, nodeId)
            val node = resolved.node ?: return@withContext ToolResult.error(resolved.error.orEmpty())
            val finalText = if (append) node.text?.toString().orEmpty() + text else text
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
            }
            val success = try {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } finally {
                node.recycle()
            }
            if (success) {
                ToolResult.success(
                    mapOf(
                        "node_id" to nodeId,
                        "mode" to if (append) "append" else "replace",
                        "character_count" to text.length.toString(),
                    ),
                )
            } else {
                ToolResult.error("控件不支持文本输入，或界面已经变化")
            }
        }

    suspend fun globalAction(action: String): ToolResult = withContext(Dispatchers.Main.immediate) {
        val activeService = readyService() ?: return@withContext unavailableResult()
        val actionCode = when (action) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> return@withContext ToolResult.error("不支持的系统动作：$action")
        }
        if (activeService.performGlobalAction(actionCode)) {
            ToolResult.success(mapOf("action" to action))
        } else {
            ToolResult.error("系统拒绝执行全局动作：$action")
        }
    }

    suspend fun screenshot(): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ToolResult.error("无障碍截图需要 Android 11 或更高版本")
        }
        val activeService = withContext(Dispatchers.Main.immediate) { readyService() }
            ?: return unavailableResult()
        val bitmapResult = takeScreenshot(activeService)
        val bitmap = bitmapResult.getOrElse { return ToolResult.error(it.message ?: "截图失败") }
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(context.cacheDir, "phone-agent/screenshots").also { it.mkdirs() }
                val file = File(directory, "screen-${System.currentTimeMillis()}.png")
                file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                ToolResult.success(
                    mapOf(
                        "path" to file.absolutePath,
                        "width" to bitmap.width.toString(),
                        "height" to bitmap.height.toString(),
                        "storage" to "app_cache",
                        "model_visibility" to "截图仅保存在本机缓存，当前不会作为工具图像上传给模型。",
                    ),
                )
            } catch (error: Exception) {
                ToolResult.error("保存截图失败：${error.message}")
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun invalidateSnapshot(packageName: String?) {
        eventVersion.incrementAndGet()
        latestNodeIndex = null
        _runtimeState.value = _runtimeState.value.copy(
            activePackage = packageName ?: _runtimeState.value.activePackage,
            latestSnapshotVersion = null,
            lastEventAt = System.currentTimeMillis(),
        )
    }

    private fun readyService(): MasonAccessibilityService? = service?.takeUnless {
        _runtimeState.value.paused
    }

    private fun unavailableResult(): ToolResult = when {
        _runtimeState.value.paused -> ToolResult.error("屏幕助手已暂停，请先在设置页恢复")
        service == null -> ToolResult.error("屏幕助手的无障碍服务未开启")
        else -> ToolResult.error("屏幕助手当前不可用")
    }

    private data class ResolvedNode(
        val node: AccessibilityNodeInfo? = null,
        val error: String? = null,
    )

    private fun resolveNode(activeService: MasonAccessibilityService, nodeId: String): ResolvedNode {
        val currentVersion = eventVersion.get()
        val path = latestNodeIndex?.pathFor(nodeId, currentVersion)
            ?: return ResolvedNode(error = "节点 ID 已失效，请重新调用 phone_observe")
        var current = activeService.rootInActiveWindow
            ?: return ResolvedNode(error = "当前没有可操作的活动窗口")
        path.forEach { childIndex ->
            val child = current.getChild(childIndex)
            current.recycle()
            current = child ?: return ResolvedNode(error = "节点已不存在，请重新调用 phone_observe")
        }
        return ResolvedNode(node = current)
    }

    private suspend fun dispatchTap(
        activeService: MasonAccessibilityService,
        x: Int,
        y: Int,
    ): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(activeService, gesture)
    }

    private suspend fun dispatchGesture(
        activeService: MasonAccessibilityService,
        gesture: GestureDescription,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val accepted = activeService.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
            null,
        )
        if (!accepted && continuation.isActive) continuation.resume(false)
    }

    private suspend fun takeScreenshot(
        activeService: MasonAccessibilityService,
    ): Result<Bitmap> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            activeService.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                context.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        val copy = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        wrapped?.recycle()
                        buffer.close()
                        if (!continuation.isActive) {
                            copy?.recycle()
                        } else if (copy != null) {
                            continuation.resume(Result.success(copy))
                        } else {
                            continuation.resume(Result.failure(IllegalStateException("无法读取截图缓冲区")))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(IllegalStateException("系统截图失败，错误码 $errorCode")))
                        }
                    }
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = manager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = context.resources.displayMetrics
            manager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun AccessibilityNodeInfo.isUsefulForAgent(): Boolean =
        text?.isNotBlank() == true ||
            contentDescription?.isNotBlank() == true ||
            viewIdResourceName?.isNotBlank() == true ||
            isClickable || isEditable || isScrollable || isCheckable

    private fun AccessibilityNodeInfo.toSnapshot(nodeId: String): PhoneAgentNodeSnapshot {
        val bounds = Rect().also(::getBoundsInScreen)
        return PhoneAgentNodeSnapshot(
            id = nodeId,
            text = text?.toString()?.takeUnless { isPassword }?.cleanNodeText(),
            contentDescription = contentDescription?.toString()?.cleanNodeText(),
            className = className?.toString()?.substringAfterLast('.'),
            viewId = viewIdResourceName,
            bounds = PhoneAgentBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            clickable = isClickable,
            editable = isEditable,
            scrollable = isScrollable,
            enabled = isEnabled,
            checked = isChecked.takeIf { isCheckable },
            selected = isSelected,
        )
    }

    private fun String.cleanNodeText(): String = replace(Regex("\\s+"), " ").trim().take(240)

    private companion object {
        const val MAX_RETURNED_NODES = 300
        const val MAX_VISITED_NODES = 1_500
        const val MAX_DEPTH = 30
    }
}

fun isPhoneAgentAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, MasonAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices.split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { it == expected }
}
