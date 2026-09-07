package com.denggl2.masonremote.transport

data class PairingOffer(
    val bootstrap: PairingBootstrapPayload,
) {
    val serverUrl: String get() = bootstrap.endpoint
    val fingerprint: String get() = bootstrap.tlsCertificateSha256
    val deviceName: String get() = bootstrap.connectorDisplayName
}

sealed interface PairingResult {
    data class Connected(val connector: PairedConnector) : PairingResult {
        val deviceName: String get() = connector.displayName
    }

    data class Failed(val message: String) : PairingResult
}

interface PairingTransport {
    suspend fun pair(offer: PairingOffer): PairingResult
}

internal fun TransportMode.displayName(): String = when (this) {
    TransportMode.LOCAL_TLS -> "本地网络"
    TransportMode.CLOUDFLARE_TUNNEL -> "Cloudflare 隧道"
    TransportMode.WEBRTC_DIRECT -> "WebRTC 直连"
}

internal fun RemoteAgentKind.displayName(): String = when (this) {
    RemoteAgentKind.MASON_CODEX -> "Codex"
    RemoteAgentKind.CUSTOM -> "远程 Agent"
}

fun parsePairingOffer(raw: String): PairingOffer? {
    val trimmed = raw.trim()
    val bootstrap = runCatching {
        RemoteProtocolJson.decode<PairingBootstrapPayload>(trimmed)
    }.getOrNull()
    if (bootstrap != null && bootstrap.protocolVersion == MASON_PROTOCOL_VERSION) {
        return PairingOffer(bootstrap)
    }
    return null
}
