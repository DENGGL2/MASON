package com.denggl2.mason.tool

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.net.InetAddress

@Singleton
class DnsLookupTool @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) : Tool {
    override val name = "dns_lookup"
    override val description = "DNS 查询：解析域名，获取所有 IP 地址并检测可达性"
    override val parameters = mapOf(
        "hostname" to ParameterDef(
            type = "string",
            description = "要查询的域名，如 example.com",
            required = true,
        ),
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val hostname = args["hostname"] ?: return ToolResult(
            success = false,
            error = "缺少 hostname 参数",
        )

        return withContext(Dispatchers.IO) {
            try {
                val addresses = InetAddress.getAllByName(hostname)
                if (addresses.isEmpty()) {
                    return@withContext ToolResult(
                        success = false,
                        error = "未解析到 IP 地址",
                    )
                }

                val info = mutableMapOf<String, String>()
                info["hostname"] = hostname
                info["address_count"] = addresses.size.toString()

                val ipList = mutableListOf<String>()
                val reachableList = mutableListOf<String>()

                for ((index, addr) in addresses.withIndex()) {
                    val hostAddr = addr.hostAddress ?: "未知"
                    ipList.add(hostAddr)

                    // 检测可达性（超时 3000ms）
                    val reachable = try {
                        addr.isReachable(3000)
                    } catch (e: Exception) {
                        false
                    }
                    val reachableStr = if (reachable) "可达" else "不可达"
                    reachableList.add("$hostAddr -> $reachableStr")

                    val prefix = if (addresses.size > 1) "address_${index + 1}_" else ""
                    info["${prefix}ip"] = hostAddr
                    info["${prefix}canonical_hostname"] = addr.canonicalHostName ?: hostAddr
                    info["${prefix}host_address"] = hostAddr
                    info["${prefix}reachable"] = reachableStr
                }

                info["ip_list"] = ipList.joinToString(", ")
                info["reachable_summary"] = reachableList.joinToString("; ")

                ToolResult(success = true, data = info)
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    error = "DNS 查询失败: ${e.message}",
                )
            }
        }
    }
}
