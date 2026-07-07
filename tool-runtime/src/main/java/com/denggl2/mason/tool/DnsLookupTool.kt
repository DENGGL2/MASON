package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class DnsLookupTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "dns_lookup"
    override val description = "对域名执行 DNS 查询"
    override val parameters: Map<String, ParameterDef> = mapOf(        "host" to ParameterDef("string", "要查询的域名", required = true))

    override suspend fun execute(args: Map<String, String>): ToolResult {
                val host = args["host"] ?: return ToolResult.error("Missing host")
        val addr = java.net.InetAddress.getByName(host)
        return ToolResult.success(mapOf("host" to host, "address" to addr.hostAddress))
    }
}
