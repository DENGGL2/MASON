package com.denggl2.mason.data

import kotlinx.serialization.Serializable

@Serializable
data class ModelReference(
    val connectionId: String,
    val modelId: String,
) {
    val isValid: Boolean
        get() = connectionId.isNotBlank() && modelId.isNotBlank()
}

@Serializable
data class ApiConnection(
    val id: String,
    val providerId: String,
    val name: String,
    val apiUrl: String,
    val apiKey: String = "",
    val modelIds: List<String> = emptyList(),
    val toolsSupported: Boolean = true,
    val verifiedSignature: String = "",
    val workspaceId: String = "",
) {
    fun supportsModel(modelId: String): Boolean = modelId in modelIds
}

data class ApiConfig(
    val providerId: String = AiProviderCatalog.DEFAULT_PROVIDER_ID,
    val apiUrl: String = AiProviderCatalog.defaultProvider.apiUrl,
    val apiKey: String = "",
    val model: String = AiProviderCatalog.defaultProvider.defaultModel,
    val visionModel: String = "",
    val imageModel: String = "",
    val localModel: String = "",
    val localModelDirectEnabled: Boolean = false,
    val offlineFallbackEnabled: Boolean = false,
    val toolsEnabled: Boolean = AiProviderCatalog.defaultProvider.toolsEnabledByDefault,
    val requireToolConfirmation: Boolean = true,
    val verifiedSignature: String = "",
    val connections: List<ApiConnection> = emptyList(),
    val chatModelRef: ModelReference? = null,
    val visionModelRef: ModelReference? = null,
    val imageModelRef: ModelReference? = null,
    val dynamicLocalRoutingEnabled: Boolean = false,
    val phoneToolsEnabled: Boolean = true,
)

fun ApiConfig.resolvedConnections(): List<ApiConnection> {
    val legacy = legacyConnection()
    if (connections.isEmpty()) return listOf(legacy)
    return if (connections.any { it.id == legacy.id }) {
        connections
    } else {
        connections + legacy
    }
}

fun ApiConfig.connection(connectionId: String): ApiConnection? =
    resolvedConnections().firstOrNull { it.id == connectionId }

fun ApiConfig.connectionForProvider(providerId: String): ApiConnection? =
    resolvedConnections().firstOrNull { it.providerId == providerId }

fun ApiConfig.resolvedChatModelRef(): ModelReference =
    chatModelRef?.takeIf { it.isValid }
        ?: ModelReference(connectionIdForProvider(providerId), model)

fun ApiConfig.resolvedVisionModelRef(): ModelReference? =
    visionModelRef?.takeIf { it.isValid }
        ?: visionModel.takeIf(String::isNotBlank)?.let {
            ModelReference(resolvedChatModelRef().connectionId, it)
        }
        ?: resolvedChatModelRef().takeIf { ref ->
            val provider = connection(ref.connectionId)?.providerId.orEmpty()
            provider == AiProviderCatalog.CUSTOM_PROVIDER_ID ||
                AiProviderCatalog.getModel(provider, ref.modelId)?.supportsVision == true
        }

fun ApiConfig.resolvedImageModelRef(): ModelReference? =
    imageModelRef?.takeIf { it.isValid }
        ?: imageModel.takeIf(String::isNotBlank)?.let {
            ModelReference(resolvedChatModelRef().connectionId, it)
        }

fun ApiConfig.upsertConnection(connection: ApiConnection): ApiConfig = copy(
    connections = resolvedConnections()
        .filterNot { it.id == connection.id }
        .plus(connection),
)

fun ApiConfig.saveConnection(connection: ApiConnection): ApiConfig {
    val updated = upsertConnection(connection)
    if (resolvedChatModelRef().connectionId != connection.id) return updated
    return updated.copy(
        providerId = connection.providerId,
        apiUrl = connection.apiUrl,
        apiKey = connection.apiKey,
        toolsEnabled = connection.toolsSupported,
        verifiedSignature = connection.verifiedSignature,
    )
}

fun ApiConfig.removeConnection(connectionId: String): ApiConfig = copy(
    connections = resolvedConnections().filterNot { it.id == connectionId },
    chatModelRef = chatModelRef?.takeUnless { it.connectionId == connectionId },
    visionModelRef = visionModelRef?.takeUnless { it.connectionId == connectionId },
    imageModelRef = imageModelRef?.takeUnless { it.connectionId == connectionId },
)

fun ApiConfig.selectChatModel(reference: ModelReference): ApiConfig {
    val selectedConnection = connection(reference.connectionId) ?: return this
    return copy(
        providerId = selectedConnection.providerId,
        apiUrl = selectedConnection.apiUrl,
        apiKey = selectedConnection.apiKey,
        model = reference.modelId,
        toolsEnabled = selectedConnection.toolsSupported,
        verifiedSignature = selectedConnection.verifiedSignature,
        chatModelRef = reference,
    )
}

fun ApiConfig.withActiveConnection(): ApiConfig {
    val activeId = connectionIdForProvider(providerId)
    val existing = connections.firstOrNull { it.id == activeId }
    val active = ApiConnection(
        id = activeId,
        providerId = providerId,
        name = existing?.name
            ?: AiProviderCatalog.getProvider(providerId)?.name
            ?: providerId,
        apiUrl = apiUrl,
        apiKey = apiKey,
        modelIds = buildList {
            addAll(existing?.modelIds.orEmpty())
            listOf(model, visionModel, imageModel).filterTo(this) { it.isNotBlank() }
        }.distinct(),
        toolsSupported = toolsEnabled,
        verifiedSignature = verifiedSignature,
        workspaceId = existing?.workspaceId.orEmpty(),
    )
    val selectedRef = chatModelRef?.takeIf { it.isValid }
        ?: ModelReference(activeId, model)
    return upsertConnection(active).copy(chatModelRef = selectedRef)
}

fun ApiConfig.configuredConnections(): List<ApiConnection> = resolvedConnections().filter { item ->
    item.apiUrl.isNotBlank() && item.modelIds.isNotEmpty() &&
        (!AiProviderCatalog.requiresApiKey(item.providerId, item.apiUrl, item.modelIds.first()) ||
            item.apiKey.isNotBlank())
}

fun connectionIdForProvider(providerId: String): String = providerId

private fun ApiConfig.legacyConnection(): ApiConnection {
    val id = connectionIdForProvider(providerId)
    return ApiConnection(
        id = id,
        providerId = providerId,
        name = AiProviderCatalog.getProvider(providerId)?.name ?: providerId,
        apiUrl = apiUrl,
        apiKey = apiKey,
        modelIds = listOf(model, visionModel, imageModel).filter(String::isNotBlank).distinct(),
        toolsSupported = toolsEnabled,
        verifiedSignature = verifiedSignature,
    )
}
