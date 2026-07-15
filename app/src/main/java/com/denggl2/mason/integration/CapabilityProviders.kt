package com.denggl2.mason.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CapabilityProtocol {
    A2A,
    MCP,
}

enum class CapabilityConnectionStatus {
    NotInstalled,
    NeedsAuthorization,
    Connected,
    WaitingForOfficialAccess,
    Unavailable,
}

data class AppAuthorizationSpec(
    val action: String,
    val uri: String,
)

data class CapabilityProvider(
    val id: String,
    val protocol: CapabilityProtocol,
    val displayName: String,
    val packageNames: List<String>,
    val capabilities: List<String>,
    val authorization: AppAuthorizationSpec? = null,
    val officialAccessAvailable: Boolean = false,
)

data class CapabilityProviderState(
    val provider: CapabilityProvider,
    val status: CapabilityConnectionStatus,
    val detail: String,
)

object CapabilityProviderCatalog {
    val appCollaborations = listOf(
        CapabilityProvider(
            id = "wechat",
            protocol = CapabilityProtocol.A2A,
            displayName = "微信",
            packageNames = listOf("com.tencent.mm"),
            capabilities = listOf("发送消息", "语音通话", "视频通话"),
        ),
        CapabilityProvider(
            id = "alipay",
            protocol = CapabilityProtocol.A2A,
            displayName = "支付宝",
            packageNames = listOf("com.eg.android.AlipayGphone"),
            capabilities = listOf("付款准备", "账单查询", "生活服务"),
        ),
        CapabilityProvider(
            id = "meituan",
            protocol = CapabilityProtocol.A2A,
            displayName = "美团",
            packageNames = listOf("com.sankuai.meituan"),
            capabilities = listOf("外卖", "酒店", "出行服务"),
        ),
    )

    val mcpPurposes = listOf("搜索", "文件", "地图", "GitHub", "办公系统")
}

@Singleton
class CapabilityAuthorizationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("mason_capability_authorizations", Context.MODE_PRIVATE)

    fun isAuthorized(providerId: String): Boolean =
        preferences.getStringSet(KEY_AUTHORIZED, emptySet()).orEmpty().contains(providerId)

    fun markAuthorized(providerId: String) {
        val next = preferences.getStringSet(KEY_AUTHORIZED, emptySet()).orEmpty() + providerId
        preferences.edit().putStringSet(KEY_AUTHORIZED, next).apply()
    }

    private companion object {
        const val KEY_AUTHORIZED = "authorized_providers"
    }
}

@Singleton
class AppCapabilityAuthorization @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authorizationStore: CapabilityAuthorizationStore,
) {
    fun state(provider: CapabilityProvider): CapabilityProviderState {
        val installed = provider.packageNames.any(::isPackageInstalled)
        return when {
            !installed -> CapabilityProviderState(
                provider = provider,
                status = CapabilityConnectionStatus.NotInstalled,
                detail = if (provider.officialAccessAvailable) "未安装" else "未安装；官方接入尚未开放",
            )
            !provider.officialAccessAvailable -> CapabilityProviderState(
                provider = provider,
                status = CapabilityConnectionStatus.WaitingForOfficialAccess,
                detail = "已安装，等待官方开放接入",
            )
            authorizationStore.isAuthorized(provider.id) ->
                CapabilityProviderState(provider, CapabilityConnectionStatus.Connected, "已连接")
            provider.authorization != null ->
                CapabilityProviderState(provider, CapabilityConnectionStatus.NeedsAuthorization, "待授权")
            else -> CapabilityProviderState(provider, CapabilityConnectionStatus.Unavailable, "当前不可用")
        }
    }

    fun createAuthorizationIntent(provider: CapabilityProvider): Intent? {
        if (state(provider).status != CapabilityConnectionStatus.NeedsAuthorization) return null
        val spec = provider.authorization ?: return null
        val targetPackage = provider.packageNames.firstOrNull(::isPackageInstalled) ?: return null
        return Intent(spec.action, Uri.parse(spec.uri)).apply {
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun markAuthorized(providerId: String) = authorizationStore.markAuthorized(providerId)

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
    }.isSuccess
}
