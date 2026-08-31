package com.denggl2.mason.connector

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.Locale
import kotlin.concurrent.thread

/** Owns a Cloudflare Quick Tunnel or a named tunnel with a stable hostname. */
class CloudflareTunnel(
    private val executable: Path,
    private val originUrl: String,
    private val publicEndpoint: String? = null,
    private val namedTunnel: String? = null,
    private val tunnelToken: String? = null,
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val processRef = AtomicReference<Process?>()
    private val output = StringBuilder()
    private var supervisor: Thread? = null

    fun start(timeoutMillis: Long = DEFAULT_START_TIMEOUT_MILLIS): String {
        check(publicEndpoint == null == (namedTunnel == null)) {
            "Named Cloudflare Tunnel requires both a hostname and a tunnel name"
        }
        check(started.compareAndSet(false, true)) { "Cloudflare tunnel has already started" }
        val url = AtomicReference(publicEndpoint)
        val ready = CountDownLatch(1)
        supervisor = thread(isDaemon = true, name = "mason-cloudflare-supervisor") {
            var firstProcess = true
            while (!closed.get()) {
                val process = runCatching {
                    ProcessBuilder(
                        buildCloudflareCommand(
                            executable = executable,
                            originUrl = originUrl,
                            namedTunnel = namedTunnel,
                            hasTunnelToken = !tunnelToken.isNullOrBlank(),
                        ),
                    ).apply {
                        tunnelToken?.takeIf(String::isNotBlank)?.let { token ->
                            environment()["TUNNEL_TOKEN"] = token
                        }
                    }.redirectErrorStream(true).start()
                }.getOrElse { error ->
                    synchronized(output) { output.appendLine(error.message.orEmpty()) }
                    if (firstProcess) ready.countDown()
                    if (!sleepBeforeRestart()) break
                    continue
                }
                firstProcess = false
                processRef.set(process)
                if (closed.get()) {
                    process.destroy()
                    processRef.compareAndSet(process, null)
                    break
                }
                thread(isDaemon = true, name = "mason-cloudflare-output") {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(output) { output.appendLine(line) }
                            URL_PATTERN.find(line)?.value?.let { discovered ->
                                url.compareAndSet(null, discovered.trimEnd('/'))
                                ready.countDown()
                            }
                            if (namedTunnel != null && NAMED_READY_PATTERN.containsMatchIn(line)) {
                                ready.countDown()
                            }
                        }
                    }
                }
                val exitCode = runCatching { process.waitFor() }
                    .getOrElse { error ->
                        if (!closed.get()) {
                            synchronized(output) { output.appendLine(error.message.orEmpty()) }
                        }
                        -1
                    }
                if (exitCode != 0 && firstProcess) ready.countDown()
                processRef.compareAndSet(process, null)
                if (!closed.get()) sleepBeforeRestart()
            }
        }

        if (!ready.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            val details = synchronized(output) { output.toString().trim() }
            close()
            val mode = if (namedTunnel == null) "Quick Tunnel" else "named Tunnel"
            error("Cloudflare $mode did not become ready${if (details.isBlank()) "" else ": $details"}")
        }
        return requireNotNull(url.get()) { "Cloudflare named tunnel did not expose a public endpoint" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        processRef.getAndSet(null)?.let { running ->
            if (running.isAlive) {
                running.destroy()
                if (!running.waitFor(2, TimeUnit.SECONDS)) running.destroyForcibly()
            }
        }
        supervisor?.interrupt()
        supervisor = null
    }

    private fun sleepBeforeRestart(): Boolean =
        runCatching {
            Thread.sleep(RESTART_DELAY_MILLIS)
            true
        }.getOrDefault(false)

    companion object {
        private const val DEFAULT_START_TIMEOUT_MILLIS = 30_000L
        private const val RESTART_DELAY_MILLIS = 1_000L
        private val URL_PATTERN = Regex("https://[a-z0-9-]+\\.trycloudflare\\.com")
        private val NAMED_READY_PATTERN = Regex("Registered tunnel connection")

        internal fun buildCloudflareCommand(
            executable: Path,
            originUrl: String,
            namedTunnel: String? = null,
            hasTunnelToken: Boolean = false,
        ): List<String> = buildList {
            add(executable.toString())
            add("tunnel")
            add("--no-autoupdate")
            if (namedTunnel == null) {
                add("--url")
                add(originUrl)
                add("--no-tls-verify")
            } else {
                add("run")
                add("--url")
                add(originUrl)
                add("--no-tls-verify")
                if (!hasTunnelToken) {
                    add(namedTunnel)
                }
            }
        }
    }
}
