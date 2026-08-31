package com.denggl2.mason.connector

import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.protocol.PairingIceServer
import com.denggl2.mason.protocol.PairingRoute
import com.denggl2.mason.protocol.RemoteAgentKind
import com.denggl2.mason.protocol.SignalingMode
import com.denggl2.mason.protocol.TransportMode
import com.denggl2.mason.protocol.signingPayload
import java.awt.Desktop
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal fun runLocalPairing(arguments: List<String>) {
    runPairing(
        host = ConnectorLoopbackServer.DEFAULT_LOOPBACK_HOST,
        arguments = arguments,
        usage = "Usage: mason-codex-connector pair-local <port> <qr-output.png> [state-directory]",
        readyMessage = "MASON local pairing is ready",
        serveRemoteConversations = true,
        transportMode = TransportMode.LOCAL_TLS,
    )
}

internal fun runPrivatePairing(arguments: List<String>) {
    require(arguments.size in 3..4) {
        "Usage: mason-codex-connector pair-private <private-ipv4> <port> <qr-output.png> [state-directory]"
    }
    val host = validatePrivatePairingHost(arguments[0])
    runPairing(
        host = host,
        arguments = arguments.drop(1),
        usage = "Usage: mason-codex-connector pair-private <private-ipv4> <port> <qr-output.png> [state-directory]",
        readyMessage = "MASON private-network pairing is ready",
        serveRemoteConversations = true,
        transportMode = TransportMode.LOCAL_TLS,
    )
}

internal fun runCloudflarePairing(arguments: List<String>) {
    require(arguments.size in 2..4) {
        "Usage: mason-codex-connector pair-cloudflare <port> <qr-output.png> [state-directory] [cloudflared-path]"
    }
    runPairing(
        host = ConnectorLoopbackServer.DEFAULT_LOOPBACK_HOST,
        arguments = arguments,
        usage = "Usage: mason-codex-connector pair-cloudflare <port> <qr-output.png> [state-directory] [cloudflared-path]",
        readyMessage = "MASON Cloudflare Tunnel pairing is ready",
        serveRemoteConversations = true,
        transportMode = TransportMode.CLOUDFLARE_TUNNEL,
        cloudflaredPath = arguments.getOrNull(3)?.let(Path::of),
    )
}

internal fun runCloudflareNamedPairing(arguments: List<String>) {
    require(arguments.size in 4..6) {
        "Usage: mason-codex-connector pair-cloudflare-named <port> <hostname> <tunnel-name-or-uuid> <qr-output.png> [state-directory] [cloudflared-path]"
    }
    val hostname = validateCloudflareHostname(arguments[1])
    val tunnelName = arguments[2].trim()
    require(tunnelName.isNotBlank()) { "Cloudflare tunnel name or UUID must not be blank" }
    runPairing(
        host = ConnectorLoopbackServer.DEFAULT_LOOPBACK_HOST,
        arguments = buildList {
            add(arguments[0])
            add(arguments[3])
            arguments.getOrNull(4)?.let(::add)
        },
        usage = "Usage: mason-codex-connector pair-cloudflare-named <port> <hostname> <tunnel-name-or-uuid> <qr-output.png> [state-directory] [cloudflared-path]",
        readyMessage = "MASON named Cloudflare Tunnel pairing is ready",
        serveRemoteConversations = true,
        transportMode = TransportMode.CLOUDFLARE_TUNNEL,
        cloudflaredPath = arguments.getOrNull(5)?.let(Path::of),
        cloudflareHostname = hostname,
        cloudflareTunnelName = tunnelName,
        cloudflareTunnelToken = System.getenv("MASON_CLOUDFLARE_TUNNEL_TOKEN")
            ?.trim()
            ?.takeIf(String::isNotBlank),
    )
}

internal fun runWebRtcPairing(arguments: List<String>) {
    require(arguments.size in 3..4) {
        "Usage: mason-codex-connector pair-webrtc <port> <qr-output.png> <signaling-endpoint> [state-directory]"
    }
    val signalingEndpoint = validateWebRtcSignalingEndpoint(arguments[2])
    runPairing(
        host = ConnectorLoopbackServer.DEFAULT_LOOPBACK_HOST,
        arguments = buildList {
            add(arguments[0])
            add(arguments[1])
            arguments.getOrNull(3)?.let(::add)
        },
        usage = "Usage: mason-codex-connector pair-webrtc <port> <qr-output.png> <signaling-endpoint> [state-directory]",
        readyMessage = "MASON WebRTC pairing is ready",
        serveRemoteConversations = true,
        transportMode = TransportMode.WEBRTC_DIRECT,
        signalingEndpoint = signalingEndpoint,
    )
}

private fun runPairing(
    host: String,
    arguments: List<String>,
    usage: String,
    readyMessage: String,
    serveRemoteConversations: Boolean,
    transportMode: TransportMode,
    cloudflaredPath: Path? = null,
    cloudflareHostname: String? = null,
    cloudflareTunnelName: String? = null,
    cloudflareTunnelToken: String? = null,
    signalingEndpoint: String? = null,
    agentKind: RemoteAgentKind = configuredRemoteAgentKind(),
) {
    require(arguments.size in 2..4) { usage }
    val port = arguments[0].toIntOrNull() ?: error("Pairing port must be a number")
    require(port in 1..65535) { "Pairing port must be between 1 and 65535" }
    val qrOutput = Path.of(arguments[1])
    require(!Files.exists(qrOutput)) { "QR output already exists: $qrOutput" }
    val stateDirectory = arguments.getOrNull(2)?.let(Path::of) ?: defaultConnectorStateDirectory()
    Files.createDirectories(stateDirectory)

    val stateStore = ConnectorStateStore(stateDirectory.resolve(CONNECTOR_STATE_FILE))
    val connectorIdentityStore = WindowsConnectorIdentityStore(
        stateDirectory.resolve(CONNECTOR_IDENTITY_FILE),
    )
    val connectorIdentity = connectorIdentityStore.getOrCreateIdentity()
    var codexClient: CodexAppServerClient? = null
    var codexNotificationScope: CoroutineScope? = null
    var agentAdapter: RemoteAgentAdapter? = null
    val conversationProvider = if (serveRemoteConversations) {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val executable = CodexExecutableLocator.locate()
            ?: error("Codex executable not found. Set MASON_CODEX_PATH to an executable Codex CLI path.")
        val transport = ProcessCodexTransport.start(
            executable = executable,
            workingDirectory = workingDirectory,
        )
        val client = CodexAppServerClient(transport)
        try {
            runBlocking {
                withTimeout(CODEX_INITIALIZATION_TIMEOUT_MILLIS) {
                    client.initialize(
                        clientName = "mason_connector",
                        clientTitle = "MASON Connector",
                        clientVersion = "0.2.0",
                    )
                }
            }
        } catch (error: Throwable) {
            client.close()
            throw error
        }
        codexClient = client
        val adapterRegistry = RemoteAgentAdapterRegistry(
            factories = listOf(CodexRemoteAgentAdapterFactory()),
        )
        adapterRegistry.create(
            kind = agentKind,
            context = RemoteAgentAdapterContext(
                store = stateStore,
                workingDirectory = workingDirectory,
                attachmentRoot = stateDirectory.resolve(CONNECTOR_ATTACHMENTS_DIRECTORY),
                codexApi = CodexAppServerApi(client),
            ),
        ).also { adapter ->
            agentAdapter = adapter
            codexNotificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
                if (adapter is CodexRemoteAgentAdapter) {
                    scope.launch {
                        client.notifications.collect(adapter::record)
                    }
                    scope.launch {
                        client.serverRequests.collect(adapter::record)
                    }
                }
            }
        }.conversationProvider
    } else {
        null
    }
    val tlsIdentity = ConnectorTlsIdentityStore(
        stateDirectory.resolve(CONNECTOR_TLS_IDENTITY_FILE),
    ).getOrCreateIdentity()
    val service = PairingAuthService(
        store = stateStore,
        connectorPublicKey = connectorIdentity.publicKey,
    )
    val offer = service.createPairingOffer(
        transportMode = transportMode,
        agentKind = agentKind,
    )
    val conversationController = agentAdapter?.conversationController
    val localEndpoint = "https://$host:$port"
    val tunnel = if (transportMode == TransportMode.CLOUDFLARE_TUNNEL) {
        CloudflareTunnel(
            executable = cloudflaredPath ?: defaultCloudflaredPath(),
            originUrl = localEndpoint,
            publicEndpoint = cloudflareHostname?.let { "https://$it" },
            namedTunnel = cloudflareTunnelName,
            tunnelToken = cloudflareTunnelToken,
        )
    } else {
        null
    }
    val server = ConnectorTlsServer(
        authService = service,
        tlsIdentity = tlsIdentity,
        host = host,
        port = port,
        conversationProvider = conversationProvider,
        conversationController = conversationController,
        transport = transportMode.transportLabel(),
    )
    var loopbackServer: ConnectorLoopbackServer? = null
    var webRtcTransport: WebRtcDesktopTransport? = null
    var webRtcResumeTransport: WebRtcDesktopTransport? = null
    var webRtcResumeScope: CoroutineScope? = null
    val stopped = AtomicBoolean(false)
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        tunnel?.close()
        webRtcResumeScope?.cancel()
        webRtcResumeTransport?.close()
        webRtcTransport?.close()
        loopbackServer?.close()
        server.close()
        codexNotificationScope?.cancel()
        agentAdapter?.close()
        codexClient?.close()
        tlsIdentity.close()
    }

    try {
        server.start()
        val endpoint = tunnel?.start() ?: localEndpoint
        val route = PairingRoute(
            endpoint = endpoint,
            signalingEndpoint = signalingEndpoint,
            cloudflareHostname = runCatching { java.net.URI(endpoint).host }.getOrNull(),
            stunServers = if (transportMode == TransportMode.WEBRTC_DIRECT) {
                configuredWebRtcStunServers()
            } else {
                emptyList()
            },
            turnServers = if (transportMode == TransportMode.WEBRTC_DIRECT) {
                configuredWebRtcTurnServers()
            } else {
                emptyList()
            },
            signalingMode = if (transportMode == TransportMode.WEBRTC_DIRECT) {
                SignalingMode.DESKTOP_OFFER
            } else {
                SignalingMode.CLIENT_OFFER
            },
        )
        if (transportMode == TransportMode.WEBRTC_DIRECT) {
            val loopbackPort = findAvailableLoopbackPort()
            loopbackServer = ConnectorLoopbackServer(
                authService = service,
                port = loopbackPort,
                conversationProvider = conversationProvider,
                conversationController = conversationController,
            ).start()
            val resumeOfferId = stateStore.webRtcResumeOfferId()
            val resumeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            webRtcResumeScope = resumeScope
            val resumeLock = Any()
            fun startResumeTransport() {
                if (stopped.get()) return
                synchronized(resumeLock) {
                    if (stopped.get() || webRtcResumeTransport != null) return
                    val transport = WebRtcDesktopTransport(
                        signalingEndpoint = requireNotNull(signalingEndpoint),
                        offerId = resumeOfferId,
                        expiresAt = System.currentTimeMillis() + RESUME_OFFER_TTL_MILLIS,
                        route = route,
                        loopbackEndpoint = URI("http://127.0.0.1:$loopbackPort/"),
                        deleteAfterPairing = false,
                        onSessionClosed = {
                            resumeScope.launch {
                                delay(250)
                                synchronized(resumeLock) {
                                    webRtcResumeTransport?.close()
                                    webRtcResumeTransport = null
                                }
                                startResumeTransport()
                            }
                        },
                    )
                    webRtcResumeTransport = transport
                    runCatching { transport.start() }
                        .onFailure {
                            synchronized(resumeLock) {
                                if (webRtcResumeTransport === transport) {
                                    webRtcResumeTransport = null
                                }
                            }
                            transport.close()
                            resumeScope.launch {
                                delay(1_000)
                                startResumeTransport()
                            }
                        }
                }
            }
            startResumeTransport()
            webRtcTransport = WebRtcDesktopTransport(
                signalingEndpoint = requireNotNull(signalingEndpoint),
                offerId = offer.pairingId,
                expiresAt = offer.expiresAt,
                route = route,
                loopbackEndpoint = URI("http://127.0.0.1:$loopbackPort/"),
                resumeOfferIdForPairing = resumeOfferId,
            ).also { it.start() }
        }
        val unsignedBootstrap = PairingBootstrap(
            offer = offer,
            endpoint = endpoint,
            tlsCertificateSha256 = if (transportMode == TransportMode.CLOUDFLARE_TUNNEL) {
                ""
            } else {
                tlsIdentity.certificateSha256
            },
            connectorDisplayName = connectorDisplayName(),
            transportMode = transportMode,
            agentKind = offer.agentKind,
            deviceId = stateStore.deviceId,
            offerId = offer.pairingId,
            publicKey = connectorIdentity.publicKey,
            nonce = offer.nonce,
            expiresAt = offer.expiresAt,
            routeBootstrap = route,
        )
        val bootstrap = unsignedBootstrap.copy(
            signature = connectorIdentityStore.sign(unsignedBootstrap.signingPayload()),
        )
        val output = PairingQrCodeWriter.write(bootstrap, qrOutput)
        println(readyMessage)
        println("Endpoint: $endpoint")
        println("Transport: ${transportMode.name}")
        println("QR code: $output")
        openQrPreview(output)
        println("Expires at: ${offer.expiresAt}")
        println("Press Ctrl+C to stop")

        val shutdown = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread { shutdown.countDown() })
        if (serveRemoteConversations) {
            shutdown.await()
        } else {
            val remainingMillis = (offer.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
            shutdown.await(remainingMillis, TimeUnit.MILLISECONDS)
        }
    } finally {
        stop()
    }
}

private fun openQrPreview(output: Path) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(output.toFile())
            println("QR preview: opened")
        }
    }
}

private fun findAvailableLoopbackPort(): Int = ServerSocket(0).use { it.localPort }

private const val RESUME_OFFER_TTL_MILLIS = 5 * 60_000L

internal fun validatePrivatePairingHost(
    value: String,
    isAssignedToDevice: (InetAddress) -> Boolean = ::isAssignedToLocalInterface,
): String {
    val octets = value.split('.')
    require(octets.size == 4) { "Private pairing host must be a literal IPv4 address" }
    val bytes = octets.map { part ->
        require(part.isNotEmpty() && part.all(Char::isDigit)) {
            "Private pairing host must be a literal IPv4 address"
        }
        val octet = part.toIntOrNull()
        require(octet != null && octet in 0..255) {
            "Private pairing host must be a literal IPv4 address"
        }
        octet.toByte()
    }.toByteArray()
    val address = InetAddress.getByAddress(bytes)
    require(address.isSiteLocalAddress || bytes.isCarrierGradeNatAddress()) {
        "Private pairing host must use a private IPv4 range or the Tailscale 100.64/10 range"
    }
    require(!address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress) {
        "Private pairing host must be a non-loopback unicast IPv4 address"
    }
    require(isAssignedToDevice(address)) {
        "Private pairing host is not assigned to this device: $value"
    }
    return address.hostAddress
}

private fun ByteArray.isCarrierGradeNatAddress(): Boolean {
    val firstOctet = this[0].toInt() and 0xff
    val secondOctet = this[1].toInt() and 0xff
    return firstOctet == 100 && secondOctet in 64..127
}

private fun isAssignedToLocalInterface(address: InetAddress): Boolean =
    NetworkInterface.getNetworkInterfaces().toList().any { networkInterface ->
        networkInterface.isUp && networkInterface.inetAddresses.toList().any(address::equals)
    }

private fun defaultConnectorStateDirectory(): Path {
    val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
        ?: error("LOCALAPPDATA is unavailable; pass an explicit state directory")
    return Path.of(localAppData, "MASON", "connector")
}

private fun connectorDisplayName(): String =
    System.getenv("COMPUTERNAME")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: runCatching { InetAddress.getLocalHost().hostName.trim() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
        ?: "电脑"

private fun defaultCloudflaredPath(): Path =
    System.getenv("MASON_CLOUDFLARED_PATH")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: Path.of("cloudflared")

private fun configuredRemoteAgentKind(): RemoteAgentKind =
    System.getenv("MASON_REMOTE_AGENT_KIND")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { value ->
            runCatching { RemoteAgentKind.valueOf(value.uppercase(Locale.ROOT)) }.getOrNull()
        }
        ?: RemoteAgentKind.MASON_CODEX

internal fun validateCloudflareHostname(value: String): String {
    val hostname = value.trim().removeSuffix(".").lowercase(Locale.ROOT)
    require(hostname.length in 1..253 && hostname.matches(CLOUDFLARE_HOSTNAME_PATTERN)) {
        "Cloudflare hostname must be a public DNS hostname"
    }
    return hostname
}

internal fun validateWebRtcSignalingEndpoint(
    value: String,
    allowInsecureLocal: Boolean = System.getenv("MASON_ALLOW_INSECURE_WEBRTC_SIGNALING")
        ?.trim()
        ?.equals("true", ignoreCase = true)
        ?: false,
): String {
    val endpoint = value.trim().removeSuffix("/")
    val uri = runCatching { URI(endpoint) }.getOrElse {
        throw IllegalArgumentException("WebRTC signaling endpoint must be a valid URL", it)
    }
    require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "WebRTC signaling endpoint must include a host and no credentials or query"
    }
    if (uri.scheme.equals("https", ignoreCase = true)) return endpoint
    val localHost = uri.host.equals("127.0.0.1", ignoreCase = true) ||
        uri.host.equals("localhost", ignoreCase = true) ||
        uri.host.equals("10.0.2.2", ignoreCase = true)
    require(
        allowInsecureLocal && uri.scheme.equals("http", ignoreCase = true) && localHost,
    ) {
        "WebRTC signaling endpoint must use HTTPS; insecure HTTP is only allowed for explicit local tests"
    }
    return endpoint
}

private fun configuredWebRtcStunServers(): List<String> = parseWebRtcStunServers(
    System.getenv("MASON_WEBRTC_STUN_SERVERS")
        ?.takeIf(String::isNotBlank)
        ?: DEFAULT_WEBRTC_STUN_SERVERS,
)

private fun configuredWebRtcTurnServers(): List<PairingIceServer> = parseWebRtcTurnServers(
    System.getenv("MASON_WEBRTC_TURN_SERVERS").orEmpty(),
)

internal fun parseWebRtcStunServers(value: String): List<String> = value
    .split(',', ';', '\n', '\r')
    .map(String::trim)
    .filter(String::isNotBlank)
    .onEach { server ->
        require(server.startsWith("stun:", ignoreCase = true) ||
            server.startsWith("stuns:", ignoreCase = true)) {
            "STUN server must use stun: or stuns:"
        }
    }
    .distinct()

/**
 * TURN entries use `url[,url]|username|credential`, separated by semicolons.
 * Credentials are copied into the short-lived QR route and should themselves
 * be short-lived deployment credentials.
 */
internal fun parseWebRtcTurnServers(value: String): List<PairingIceServer> = value
    .split(';')
    .map(String::trim)
    .filter(String::isNotBlank)
    .map { entry ->
        val fields = entry.split('|', limit = 3)
        require(fields.size == 3 && fields[0].isNotBlank() && fields[1].isNotBlank() && fields[2].isNotBlank()) {
            "TURN server must use url|username|credential"
        }
        val urls = fields[0]
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .onEach { url ->
                require(url.startsWith("turn:", ignoreCase = true) ||
                    url.startsWith("turns:", ignoreCase = true)) {
                    "TURN server URL must use turn: or turns:"
                }
            }
            .distinct()
        PairingIceServer(urls = urls, username = fields[1], credential = fields[2])
    }

private fun TransportMode.transportLabel(): String = when (this) {
    TransportMode.LOCAL_TLS -> "tls"
    TransportMode.CLOUDFLARE_TUNNEL -> "cloudflare_tunnel"
    TransportMode.WEBRTC_DIRECT -> "webrtc_direct"
}

private const val CONNECTOR_STATE_FILE = "connector-state.json"
private const val CONNECTOR_IDENTITY_FILE = "connector-identity.json"
private const val CONNECTOR_TLS_IDENTITY_FILE = "connector-tls-identity.json"
private const val CONNECTOR_ATTACHMENTS_DIRECTORY = "attachments"
private const val CODEX_INITIALIZATION_TIMEOUT_MILLIS = 20_000L
private const val DEFAULT_WEBRTC_STUN_SERVERS = "stun:stun.l.google.com:19302"
private val CLOUDFLARE_HOSTNAME_PATTERN = Regex(
    "(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}",
)
