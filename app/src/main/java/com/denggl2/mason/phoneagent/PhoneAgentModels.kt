package com.denggl2.mason.phoneagent

import kotlinx.serialization.Serializable

data class PhoneAgentPoint(
    val x: Int,
    val y: Int,
)

@Serializable
data class PhoneAgentBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

@Serializable
data class PhoneAgentNodeSnapshot(
    val id: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val viewId: String? = null,
    val bounds: PhoneAgentBounds,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val selected: Boolean = false,
)

@Serializable
data class PhoneAgentScreenSnapshot(
    val version: Long,
    val capturedAt: Long,
    val packageName: String? = null,
    val windowTitle: String? = null,
    val width: Int,
    val height: Int,
    val nodes: List<PhoneAgentNodeSnapshot>,
    val truncated: Boolean,
)

data class PhoneAgentRuntimeState(
    val serviceConnected: Boolean = false,
    val paused: Boolean = false,
    val activePackage: String? = null,
    val latestSnapshotVersion: Long? = null,
    val lastEventAt: Long? = null,
)

@Serializable
data class PhoneAgentLogEntry(
    val id: String,
    val timestamp: Long,
    val action: String,
    val summary: String,
    val success: Boolean,
    val error: String? = null,
    val packageName: String? = null,
    val snapshotVersion: Long? = null,
    val details: Map<String, String> = emptyMap(),
)

internal data class PhoneAgentNodeIndex(
    val version: Long,
    val paths: Map<String, List<Int>>,
) {
    fun pathFor(nodeId: String, currentVersion: Long): List<Int>? =
        paths[nodeId]?.takeIf { version == currentVersion }
}

object PhoneAgentToolNames {
    const val OBSERVE = "phone_observe"
    const val SCREENSHOT = "phone_screenshot"
    const val CLICK_NODE = "phone_click_node"
    const val TAP = "phone_tap"
    const val SWIPE = "phone_swipe"
    const val SCROLL = "phone_scroll"
    const val SET_TEXT = "phone_set_text"
    const val GLOBAL_ACTION = "phone_global_action"

    val all = setOf(
        OBSERVE,
        SCREENSHOT,
        CLICK_NODE,
        TAP,
        SWIPE,
        SCROLL,
        SET_TEXT,
        GLOBAL_ACTION,
    )
}
