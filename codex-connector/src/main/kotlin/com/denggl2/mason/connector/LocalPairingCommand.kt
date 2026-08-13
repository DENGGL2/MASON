package com.denggl2.mason.connector

import com.denggl2.mason.protocol.PairingBootstrap
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    )
}

private fun runPairing(
    host: String,
    arguments: List<String>,
    usage: String,
    readyMessage: String,
    serveRemoteConversations: Boolean,
) {
    require(arguments.size in 2..3) { usage }
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
        RemoteConversationService(
            api = CodexAppServerApi(client),
            store = stateStore,
            attachmentRoot = stateDirectory.resolve(CONNECTOR_ATTACHMENTS_DIRECTORY),
            workingDirectory = workingDirectory,
        ).also { service ->
            codexNotificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
                scope.launch {
                    client.notifications.collect(service::record)
                }
            }
        }
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
    val offer = service.createPairingOffer()
    val endpoint = "https://$host:$port"
    val bootstrap = PairingBootstrap(
        offer = offer,
        endpoint = endpoint,
        tlsCertificateSha256 = tlsIdentity.certificateSha256,
        connectorDisplayName = connectorDisplayName(),
    )
    val server = ConnectorTlsServer(
        authService = service,
        tlsIdentity = tlsIdentity,
        host = host,
        port = port,
        conversationProvider = conversationProvider,
        conversationController = conversationProvider,
    )
    val stopped = AtomicBoolean(false)
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        server.close()
        codexNotificationScope?.cancel()
        codexClient?.close()
        tlsIdentity.close()
    }

    try {
        server.start()
        val output = PairingQrCodeWriter.write(bootstrap, qrOutput)
        println(readyMessage)
        println("Endpoint: $endpoint")
        println("QR code: $output")
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

private const val CONNECTOR_STATE_FILE = "connector-state.json"
private const val CONNECTOR_IDENTITY_FILE = "connector-identity.json"
private const val CONNECTOR_TLS_IDENTITY_FILE = "connector-tls-identity.json"
private const val CONNECTOR_ATTACHMENTS_DIRECTORY = "attachments"
private const val CODEX_INITIALIZATION_TIMEOUT_MILLIS = 20_000L
