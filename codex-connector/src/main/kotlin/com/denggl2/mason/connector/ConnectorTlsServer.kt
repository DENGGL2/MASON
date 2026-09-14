package com.denggl2.mason.connector

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import java.net.InetAddress
import java.net.NetworkInterface

class ConnectorTlsServer(
    private val authService: PairingAuthService,
    private val tlsIdentity: ConnectorTlsIdentity,
    val host: String,
    val port: Int,
    private val conversationProvider: RemoteConversationProvider? = null,
    private val conversationController: RemoteConversationController? =
        conversationProvider as? RemoteConversationController,
    private val transport: String = "tls",
) : AutoCloseable {
    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    init {
        require(port in 1..65535) { "TLS server port must be between 1 and 65535" }
        val address = runCatching { InetAddress.getByName(host) }.getOrElse {
            throw IllegalArgumentException("TLS server host cannot be resolved: $host", it)
        }
        require(!address.isAnyLocalAddress) { "TLS server cannot bind a wildcard address: $host" }
        require(!address.isMulticastAddress) { "TLS server cannot bind a multicast address: $host" }
        require(address.isLoopbackAddress || isLocalInterfaceAddress(address)) {
            "TLS server host is not assigned to this device: $host"
        }

        engine = embeddedServer(
            factory = Netty,
            environment = applicationEnvironment(),
            configure = {
                sslConnector(
                    keyStore = tlsIdentity.keyStore,
                    keyAlias = tlsIdentity.keyAlias,
                    keyStorePassword = { tlsIdentity.password },
                    privateKeyPassword = { tlsIdentity.password },
                ) {
                    this.host = this@ConnectorTlsServer.host
                    this.port = this@ConnectorTlsServer.port
                }
            },
            module = {
                configurePairingHttpApi(
                    authService = authService,
                    transport = transport,
                    conversationProvider = conversationProvider,
                    conversationController = conversationController,
                )
            },
        )
    }

    fun start(): ConnectorTlsServer = apply { engine.start(wait = false) }

    override fun close() {
        engine.stop(gracePeriodMillis = 250, timeoutMillis = 2_000)
    }
}

private fun isLocalInterfaceAddress(address: InetAddress): Boolean =
    NetworkInterface.getNetworkInterfaces().toList().any { networkInterface ->
        networkInterface.inetAddresses.toList().any(address::equals)
    }
