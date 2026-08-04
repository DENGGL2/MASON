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
data class ApiModelCapabilities(
    val supportsChat: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsImageGeneration: Boolean = false,
)

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
    val modelCapabilities: Map<String, ApiModelCapabilities> = emptyMap(),
    val verifiedModelSignatures: Map<String, String> = emptyMap(),
) {
    fun supportsModel(modelId: String): Boolean = modelId in modelIds

    fun capabilitiesFor(modelId: String): ApiModelCapabilities {
        return modelCapabilities[modelId] ?: ApiModelCapabilities(
            supportsChat = true,
            supportsTools = toolsSupported,
            supportsVision = AiProviderCatalog.getModel(providerId, modelId)?.supportsVision == true,
            supportsImageGeneration = AiProviderCatalog.getModel(providerId, modelId)
                ?.supportsImageGeneration == true,
        )
    }
}

data class ApiConfig(
    val providerId: String = "",
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val visionModel: String = "",
    val imageModel: String = "",
    val localModel: String = "",
    val localModelDirectEnabled: Boolean = false,
    val offlineFallbackEnabled: Boolean = false,
    val toolsEnabled: Boolean = false,
    val requireToolConfirmation: Boolean = true,
    val verifiedSignature: String = "",
    val connections: List<ApiConnection> = emptyList(),
    val chatModelRef: ModelReference? = null,
    val visionModelRef: ModelReference? = null,
    val imageModelRef: ModelReference? = null,
    val dynamicLocalRoutingEnabled: Boolean = false,
    val phoneToolsEnabled: Boolean = false,
)

fun ApiConfig.resolvedConnections(): List<ApiConnection> {
    val legacy = legacyConnection().takeIf {
        providerId.isNotBlank() && apiUrl.isNotBlank() && model.isNotBlank()
    }
    if (connections.isEmpty()) return listOfNotNull(legacy)
    return if (legacy == null || connections.any { it.id == legacy.id }) {
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

fun ApiConfig.configuredChatModelRef(): ModelReference? {
    return configuredModelReference(resolvedChatModelRef(), ApiModelCapabilities::supportsChatModel)
}

fun ApiConfig.configuredChatModelRefs(): List<ModelReference> {
    val selected = configuredChatModelRef()
    val candidates = configuredConnections().flatMap { connection ->
        connection.modelIds.mapNotNull { modelId ->
            val capabilities = connection.modelCapabilities[modelId] ?: return@mapNotNull null
            ModelReference(connection.id, modelId).takeIf { capabilities.supportsChatModel() }
        }
    }
    return (listOfNotNull(selected) + candidates).distinct()
}

fun ApiConfig.configuredVisionModelRef(): ModelReference? =
    resolvedVisionModelRef()?.let { reference ->
        configuredModelReference(reference, ApiModelCapabilities::supportsVision)
    }

fun ApiConfig.configuredImageModelRef(): ModelReference? {
    val selected = resolvedImageModelRef()
    if (selected != null) {
        return configuredModelReference(selected, ApiModelCapabilities::supportsImageGeneration)
    }
    return configuredConnections().firstNotNullOfOrNull { connection ->
        connection.modelIds.firstOrNull { modelId ->
            connection.modelCapabilities[modelId]?.supportsImageGeneration == true
        }?.let { modelId -> ModelReference(connection.id, modelId) }
    }
}

private fun ApiConfig.configuredModelReference(
    reference: ModelReference,
    supportsPurpose: (ApiModelCapabilities) -> Boolean,
): ModelReference? {
    if (!reference.isValid) return null
    val connection = configuredConnections().firstOrNull { it.id == reference.connectionId }
        ?: return null
    val capabilities = connection.modelCapabilities[reference.modelId] ?: return null
    return reference.takeIf {
        connection.supportsModel(it.modelId) && supportsPurpose(capabilities)
    }
}

fun ApiModelCapabilities.supportsChatModel(): Boolean =
    supportsChat || supportsTools || supportsVision || !supportsImageGeneration

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
    val activeChatReference = resolvedChatModelRef()
    if (activeChatReference.connectionId != connection.id) return updated
    val activeChatSignature = connection.verifiedModelSignatures[activeChatReference.modelId]
        ?: connection.verifiedSignature.takeIf {
            connection.modelIds.singleOrNull() == activeChatReference.modelId
        }
        ?: verifiedSignature.takeIf {
            providerId == connection.providerId &&
                apiUrl.trimEnd('/') == connection.apiUrl.trimEnd('/') &&
                apiKey == connection.apiKey &&
                model == activeChatReference.modelId
        }
        .orEmpty()
    return updated.copy(
        providerId = connection.providerId,
        apiUrl = connection.apiUrl,
        apiKey = connection.apiKey,
        toolsEnabled = connection.toolsSupported,
        verifiedSignature = activeChatSignature,
    )
}

fun ApiConfig.selectInitialImageModel(connection: ApiConnection): ApiConfig {
    if (resolvedImageModelRef() != null) return this
    val modelId = connection.modelIds.firstOrNull { candidate ->
        connection.modelCapabilities[candidate]?.supportsImageGeneration == true
    } ?: return this
    return copy(
        imageModel = modelId,
        imageModelRef = ModelReference(connection.id, modelId),
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
    val capabilities = selectedConnection.capabilitiesFor(reference.modelId)
    return copy(
        providerId = selectedConnection.providerId,
        apiUrl = selectedConnection.apiUrl,
        apiKey = selectedConnection.apiKey,
        model = reference.modelId,
        toolsEnabled = capabilities.supportsTools,
        verifiedSignature = selectedConnection.verifiedModelSignatures[reference.modelId]
            ?: selectedConnection.verifiedSignature,
        chatModelRef = reference,
    )
}

fun ApiConfig.withActiveConnection(): ApiConfig {
    if (providerId.isBlank() || apiUrl.isBlank() || model.isBlank()) return this
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
        modelCapabilities = existing?.modelCapabilities.orEmpty(),
        verifiedModelSignatures = existing?.verifiedModelSignatures.orEmpty(),
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
