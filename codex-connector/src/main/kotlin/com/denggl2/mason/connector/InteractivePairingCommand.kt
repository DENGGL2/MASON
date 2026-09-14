package com.denggl2.mason.connector

import java.nio.file.Path

/**
 * User-facing pairing entry point for deployed Connector packages.
 * The transport is selected before Codex and the network listener start.
 */
internal fun runInteractivePairing(arguments: List<String>) {
    require(arguments.size <= 3) {
        "Usage: mason-codex-connector pair [port] [qr-output.png] [state-directory]"
    }

    println("MASON 远程配对")
    println("请选择连接方式：")
    println("  1. Cloudflare 隧道（无需信令服务器，重启后需重新扫码）")
    println("  2. WebRTC 直连（需要 HTTPS 信令服务器）")
    val mode = readRequired("输入 1 或 2", ::parseInteractivePairingMode)

    val port = arguments.getOrNull(0)
        ?: readWithDefault("服务端口", DEFAULT_PAIRING_PORT.toString())
    require(port.toIntOrNull() in 1..65535) { "服务端口必须是 1 到 65535 之间的数字" }

    val qrOutput = arguments.getOrNull(1)
        ?: readWithDefault("二维码文件", DEFAULT_QR_OUTPUT)
    val stateDirectory = arguments.getOrNull(2)
        ?: readOptional("状态目录（留空使用默认目录）")

    when (mode) {
        InteractivePairingMode.CLOUDFLARE -> {
            runCloudflarePairing(
                buildList {
                    add(port)
                    add(qrOutput)
                    stateDirectory.takeIf(String::isNotBlank)?.let(::add)
                },
            )
        }

        InteractivePairingMode.WEBRTC -> {
            val signalingEndpoint = readRequired("HTTPS 信令服务器地址") { input ->
                validateWebRtcSignalingEndpoint(input)
            }
            runWebRtcPairing(
                buildList {
                    add(port)
                    add(qrOutput)
                    add(signalingEndpoint)
                    stateDirectory.takeIf(String::isNotBlank)?.let(::add)
                },
            )
        }
    }
}

internal enum class InteractivePairingMode {
    CLOUDFLARE,
    WEBRTC,
}

internal fun parseInteractivePairingMode(input: String): InteractivePairingMode = when (input.trim().lowercase()) {
    "1", "cloudflare", "cloudflare tunnel" -> InteractivePairingMode.CLOUDFLARE
    "2", "webrtc", "webrtc direct" -> InteractivePairingMode.WEBRTC
    else -> throw IllegalArgumentException("请输入 1 或 2")
}

private fun <T> readRequired(label: String, parse: (String) -> T): T {
    while (true) {
        print("$label：")
        val input = readLine()?.trim().orEmpty()
        if (input.isBlank()) {
            println("该项不能为空")
            continue
        }
        runCatching { parse(input) }
            .onSuccess { return it }
            .onFailure { println(it.message ?: "输入无效") }
    }
}

private fun readWithDefault(label: String, defaultValue: String): String {
    print("$label（默认 $defaultValue）：")
    return readLine()?.trim().orEmpty().ifBlank { defaultValue }
}

private fun readOptional(label: String): String {
    print("$label：")
    return readLine()?.trim().orEmpty()
}

private const val DEFAULT_PAIRING_PORT = 48623
private val DEFAULT_QR_OUTPUT = Path.of(System.getProperty("user.dir"), "mason-pairing.png").toString()
