package com.denggl2.mason.connector

import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "probe" -> runProbe(args.drop(1))
        "recover" -> runRecovery(args.drop(1))
        "pair-local" -> runLocalPairing(args.drop(1))
        "pair-private" -> runPrivatePairing(args.drop(1))
        "pair" -> runInteractivePairing(args.drop(1))
        "pair-cloudflare" -> runCloudflarePairing(args.drop(1))
        "pair-cloudflare-named" -> runCloudflareNamedPairing(args.drop(1))
        "pair-webrtc" -> runWebRtcPairing(args.drop(1))
        else -> printUsage()
    }
}

private fun runProbe(arguments: List<String>) = runBlocking {
    val executable = CodexExecutableLocator.locate()
        ?: error("Codex executable not found. Set MASON_CODEX_PATH to an executable Codex CLI path.")
    val workingDirectory = arguments.firstOrNull()?.let(Path::of) ?: Path.of(System.getProperty("user.dir"))
    val transport = ProcessCodexTransport.start(executable, workingDirectory)
    val client = CodexAppServerClient(transport)
    try {
        val summary = withTimeout(20_000) {
            val initialized = client.initialize(
                clientName = "mason_connector",
                clientTitle = "MASON Connector",
                clientVersion = "0.1.0",
            )
            val threads = CodexAppServerApi(client).listThreads(limit = 5).jsonObject
            buildJsonObject {
                put("status", "ok")
                put("codexExecutable", executable.toString())
                put("codexHome", initialized["codexHome"]?.jsonPrimitive?.contentOrNull.orEmpty())
                put("platform", initialized["platformOs"]?.jsonPrimitive?.contentOrNull.orEmpty())
                put("threadCount", threads["data"]?.jsonArray?.size ?: 0)
            }
        }
        println(summary)
    } finally {
        client.close()
    }
}

private fun runRecovery(arguments: List<String>) = runBlocking {
    val statePath = arguments.firstOrNull()?.let(Path::of)
        ?: error("State file is required")
    val workingDirectory = arguments.getOrNull(1)?.let(Path::of)
        ?: Path.of(System.getProperty("user.dir"))
    val executable = CodexExecutableLocator.locate()
        ?: error("Codex executable not found. Set MASON_CODEX_PATH to an executable Codex CLI path.")
    val transport = ProcessCodexTransport.start(executable, workingDirectory)
    val client = CodexAppServerClient(transport)
    try {
        val summary = withTimeout(20_000) {
            client.initialize(
                clientName = "mason_connector",
                clientTitle = "MASON Connector",
                clientVersion = "0.1.0",
            )
            val store = ConnectorStateStore(statePath)
            val api = CodexAppServerApi(client)
            val results = ManagedSessionRecovery(
                store = store,
                readThread = { threadId -> api.readThread(threadId) },
            ).recoverAll()
            val recoveredCount = results.count { it.status == SessionRecoveryStatus.RECOVERED }
            buildJsonObject {
                put("status", if (recoveredCount == results.size) "ok" else "degraded")
                put("deviceId", store.deviceId)
                put("registeredCount", results.size)
                put("recoveredCount", recoveredCount)
                put("failedCount", results.size - recoveredCount)
                put("results", JsonArray(results.map { result ->
                    buildJsonObject {
                        put("conversationId", result.binding.conversationId)
                        put("codexThreadId", result.binding.codexThreadId)
                        put("status", result.status.name.lowercase())
                        result.errorMessage?.let { put("error", it) }
                    }
                }))
            }
        }
        println(summary)
    } finally {
        client.close()
    }
}

private fun printUsage() {
    println("Usage:")
    println("  mason-codex-connector probe [working-directory]")
    println("  mason-codex-connector recover <state-file> [working-directory]")
    println("  mason-codex-connector pair-local <port> <qr-output.png> [state-directory]")
    println("  mason-codex-connector pair-private <private-ipv4> <port> <qr-output.png> [state-directory]")
    println("  mason-codex-connector pair [port] [qr-output.png] [state-directory]")
    println("  mason-codex-connector pair-cloudflare <port> <qr-output.png> [state-directory] [cloudflared-path]")
    println("  mason-codex-connector pair-cloudflare-named <port> <hostname> <tunnel-name-or-uuid> <qr-output.png> [state-directory] [cloudflared-path]")
    println("  mason-codex-connector pair-webrtc <port> <qr-output.png> <signaling-endpoint> [state-directory]")
}
