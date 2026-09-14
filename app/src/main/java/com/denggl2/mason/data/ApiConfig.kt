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
    val modelTestErrors: Map<String, String> = emptyMap(),
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

fun ApiConnection.modelForReference(reference: ModelReference?): String =
    reference?.modelId?.takeIf { it in modelIds }
        ?: modelIds.firstOrNull().orEmpty()

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
    return if (legacy == null || connections.any { candidate ->
            candidate.id == legacy.id ||
                candidate.modelIds.any(legacy.modelIds::contains)
        }) {
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
        ?: ModelReference(connectionIdForModel(providerId, apiUrl, model), model)

fun ApiConfig.configuredChatModelRef(): ModelReference? {
    return configuredModelReference(resolvedChatModelRef(), ApiModelCapabilities::supportsChatModel)
}

fun ApiConfig.configuredChatModelRefs(): List<ModelReference> {
    val selected = configuredChatModelRef()
    val candidates = configuredConnections().flatMap { connection ->
        connection.modelIds.mapNotNull { modelId ->
            val capabilities = connection.modelCapabilities[modelId] ?: return@mapNotNull null
            ModelReference(connection.id, modelId).takeIf {
                modelId !in connection.modelTestErrors && capabilities.supportsChatModel()
            }
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
            modelId !in connection.modelTestErrors &&
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
    if (reference.modelId in connection.modelTestErrors) return null
    val capabilities = connection.modelCapabilities[reference.modelId] ?: return null
    return reference.takeIf {
        connection.supportsModel(it.modelId) && supportsPurpose(capabilities)
    }
}

fun ApiModelCapabilities.supportsChatModel(): Boolean =
    supportsChat || supportsTools || supportsVision || !supportsImageGeneration

fun ApiConfig.resolvedVisionModelRef(): ModelReference? =
    visionModelRef?.takeIf { it.isValid }
        ?: visionModel.takeIf(String::isNotBlank)?.let { modelId ->
            resolvedConnections().firstOrNull { modelId in it.modelIds }
                ?.let { connection -> ModelReference(connection.id, modelId) }
        }
        ?: resolvedChatModelRef().takeIf { ref ->
            val provider = connection(ref.connectionId)?.providerId.orEmpty()
            provider == AiProviderCatalog.CUSTOM_PROVIDER_ID ||
                AiProviderCatalog.getModel(provider, ref.modelId)?.supportsVision == true
        }

fun ApiConfig.resolvedImageModelRef(): ModelReference? =
    imageModelRef?.takeIf { it.isValid }
        ?: imageModel.takeIf(String::isNotBlank)?.let { modelId ->
            resolvedConnections().firstOrNull { modelId in it.modelIds }
                ?.let { connection -> ModelReference(connection.id, modelId) }
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
        candidate !in connection.modelTestErrors &&
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
    val modelScopedConnections = splitModelScopedConnections(resolvedConnections())
    val requestedChatRef = chatModelRef?.takeIf { it.isValid }
    val activeModelId = requestedChatRef?.modelId ?: model
    val existing = modelScopedConnections.firstOrNull { connection ->
        requestedChatRef?.let { reference ->
            connection.id == reference.connectionId && activeModelId in connection.modelIds
        } == true
    } ?: modelScopedConnections.firstOrNull { connection ->
        connection.providerId == providerId &&
            activeModelId in connection.modelIds &&
            connection.apiUrl.trim().trimEnd('/') == apiUrl.trim().trimEnd('/')
    }
    val activeId = existing?.id
        ?: requestedChatRef?.connectionId
        ?: connectionIdForModel(providerId, apiUrl, activeModelId)
    val active = ApiConnection(
        id = activeId,
        providerId = providerId,
        name = existing?.name
            ?: AiProviderCatalog.getProvider(providerId)?.name
            ?: providerId,
        apiUrl = apiUrl,
        apiKey = apiKey,
        modelIds = listOf(activeModelId),
        toolsSupported = existing?.modelCapabilities?.get(activeModelId)?.supportsTools
            ?: toolsEnabled,
        verifiedSignature = existing?.verifiedModelSignatures?.get(activeModelId)
            ?: verifiedSignature,
        workspaceId = existing?.workspaceId.orEmpty(),
        modelCapabilities = existing?.modelCapabilities
            ?.filterKeys { it == activeModelId }
            .orEmpty(),
        verifiedModelSignatures = existing?.verifiedModelSignatures
            ?.filterKeys { it == activeModelId }
            .orEmpty(),
        modelTestErrors = existing?.modelTestErrors
            ?.filterKeys { it == activeModelId }
            .orEmpty(),
    )
    return copy(connections = modelScopedConnections)
        .upsertConnection(active)
        .copy(chatModelRef = ModelReference(activeId, activeModelId))
}

fun ApiConfig.configuredConnections(): List<ApiConnection> = resolvedConnections().filter { item ->
    item.apiUrl.isNotBlank() && item.modelIds.isNotEmpty() &&
        (!AiProviderCatalog.requiresApiKey(item.providerId, item.apiUrl, item.modelIds.first()) ||
            item.apiKey.isNotBlank())
}

fun connectionIdForProvider(providerId: String): String = providerId

/** Keep custom models on different OpenAI-compatible endpoints isolated. */
fun connectionIdForEndpoint(providerId: String, apiUrl: String): String {
    if (providerId != AiProviderCatalog.CUSTOM_PROVIDER_ID) return connectionIdForProvider(providerId)
    val normalizedUrl = apiUrl.trim().trimEnd('/').lowercase()
    if (normalizedUrl.isBlank()) return connectionIdForProvider(providerId)
    return "$providerId:${Integer.toHexString(normalizedUrl.hashCode())}"
}

/** Keep each model's endpoint, key, capabilities, and verification state isolated. */
fun connectionIdForModel(providerId: String, apiUrl: String, modelId: String): String {
    val normalizedUrl = apiUrl.trim().trimEnd('/').lowercase()
    val normalizedModel = modelId.trim()
    if (normalizedModel.isBlank()) return connectionIdForEndpoint(providerId, normalizedUrl)
    val fingerprint = "$normalizedUrl::$normalizedModel".hashCode()
    return "$providerId:model:${Integer.toHexString(fingerprint)}"
}

/** Split legacy connections that stored multiple Model IDs together. */
fun splitModelScopedConnections(connections: List<ApiConnection>): List<ApiConnection> {
    val result = linkedMapOf<String, ApiConnection>()
    connections.forEach { connection ->
        if (connection.providerId != AiProviderCatalog.CUSTOM_PROVIDER_ID || connection.modelIds.isEmpty()) {
            if (connection.modelIds.size <= 1) {
                val modelId = connection.modelIds.firstOrNull()
                val id = modelId?.let {
                    connectionIdForModel(connection.providerId, connection.apiUrl, it)
                } ?: connection.id
                result[id] = connection.copy(id = id)
            } else {
                connection.modelIds.forEach { modelId ->
                    val id = connectionIdForModel(connection.providerId, connection.apiUrl, modelId)
                    result[id] = connection.copy(
                        id = id,
                        modelIds = listOf(modelId),
                        modelCapabilities = connection.modelCapabilities.filterKeys { it == modelId },
                        verifiedModelSignatures = connection.verifiedModelSignatures.filterKeys { it == modelId },
                        modelTestErrors = connection.modelTestErrors.filterKeys { it == modelId },
                        toolsSupported = connection.modelCapabilities[modelId]?.supportsTools
                            ?: connection.toolsSupported,
                    )
                }
            }
            return@forEach
        }
        connection.modelIds.forEach { modelId ->
            val endpoint = signatureEndpoint(connection.verifiedModelSignatures[modelId], connection.providerId)
                ?: connection.apiUrl.trim().trimEnd('/')
            val id = connectionIdForModel(connection.providerId, endpoint, modelId)
            val capabilities = connection.modelCapabilities.filterKeys { it == modelId }
            val signatures = connection.verifiedModelSignatures.filterKeys { it == modelId }
            val errors = connection.modelTestErrors.filterKeys { it == modelId }
            val split = connection.copy(
                id = id,
                apiUrl = endpoint,
                modelIds = listOf(modelId),
                toolsSupported = capabilities.values.any(ApiModelCapabilities::supportsTools),
                verifiedSignature = signatures[modelId].orEmpty(),
                modelCapabilities = capabilities,
                verifiedModelSignatures = signatures,
                modelTestErrors = errors,
            )
            val previous = result[id]
            result[id] = if (previous == null) {
                split
            } else {
                split.copy(
                    name = previous.name,
                    modelIds = listOf(modelId),
                    toolsSupported = previous.toolsSupported || split.toolsSupported,
                    modelCapabilities = previous.modelCapabilities + split.modelCapabilities,
                    verifiedModelSignatures = previous.verifiedModelSignatures + split.verifiedModelSignatures,
                    modelTestErrors = previous.modelTestErrors + split.modelTestErrors,
                    verifiedSignature = split.verifiedSignature.ifBlank { previous.verifiedSignature },
                )
            }
        }
    }
    return result.values.toList()
}

/** Compatibility name for callers and older tests. */
fun splitEndpointScopedConnections(connections: List<ApiConnection>): List<ApiConnection> =
    splitModelScopedConnections(connections)

private fun signatureEndpoint(signature: String?, providerId: String): String? =
    signature?.split('|', limit = 4)
        ?.takeIf { parts -> parts.size == 4 && parts[0] == providerId }
        ?.get(1)
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf(String::isNotBlank)

private fun ApiConfig.legacyConnection(): ApiConnection {
    val id = connectionIdForModel(providerId, apiUrl, model)
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
