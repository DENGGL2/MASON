package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "api_settings")

@Singleton
class ApiConfigDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialVault: ApiCredentialVault,
) {
    companion object {
        private val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        private val KEY_API_URL = stringPreferencesKey("api_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_VISION_MODEL = stringPreferencesKey("vision_model")
        private val KEY_IMAGE_MODEL = stringPreferencesKey("image_model")
        private val KEY_LOCAL_MODEL = stringPreferencesKey("local_model")
        private val KEY_LOCAL_MODEL_DIRECT_ENABLED = booleanPreferencesKey("local_model_direct_enabled")
        private val KEY_OFFLINE_FALLBACK_ENABLED = booleanPreferencesKey("offline_fallback_enabled")
        private val KEY_TOOLS_ENABLED = booleanPreferencesKey("tools_enabled")
        private val KEY_REQUIRE_TOOL_CONFIRMATION = booleanPreferencesKey("require_tool_confirmation")
        private val KEY_VERIFIED_SIGNATURE = stringPreferencesKey("verified_signature")
        private val KEY_CONNECTIONS = stringPreferencesKey("connections_v2")
        private val KEY_CONNECTION_SECRETS = stringPreferencesKey("connection_secrets_v2")
        private val KEY_CHAT_MODEL_REF = stringPreferencesKey("chat_model_ref_v2")
        private val KEY_VISION_MODEL_REF = stringPreferencesKey("vision_model_ref_v2")
        private val KEY_IMAGE_MODEL_REF = stringPreferencesKey("image_model_ref_v2")
        private val KEY_DYNAMIC_LOCAL_ROUTING = booleanPreferencesKey("dynamic_local_routing_enabled")
        private val KEY_PHONE_TOOLS_ENABLED = booleanPreferencesKey("phone_tools_enabled")

        val DEFAULT_PROVIDER = AiProviderCatalog.defaultProvider
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val config: Flow<ApiConfig> = context.dataStore.data
        .onStart { migrateLegacyApiKey() }
        .map { prefs ->
        val storedUrl = prefs[KEY_API_URL]?.takeIf(String::isNotBlank)
        val providerId = prefs[KEY_PROVIDER_ID]
            ?.takeIf(String::isNotBlank)
            ?: storedUrl?.let(AiProviderCatalog::inferProviderId)
        val provider = providerId?.let(AiProviderCatalog::getProvider)
        val storedModel = prefs[KEY_MODEL]
        val secrets = decodeSecrets(prefs[KEY_CONNECTION_SECRETS].orEmpty())
        val storedConnections = splitModelScopedConnections(
            decodeConnections(prefs[KEY_CONNECTIONS].orEmpty()).map { item ->
                item.copy(apiKey = secrets[item.id].orEmpty())
            },
        )
        val activeConnectionId = provider?.id?.let {
            connectionIdForModel(it, storedUrl.orEmpty(), storedModel.orEmpty())
        }
        val rawChatModelRef = decodeModelReference(prefs[KEY_CHAT_MODEL_REF])
        val rawVisionModelRef = decodeModelReference(prefs[KEY_VISION_MODEL_REF])
        val rawImageModelRef = decodeModelReference(prefs[KEY_IMAGE_MODEL_REF])

        fun remapReference(reference: ModelReference?): ModelReference? {
            if (reference == null) return null
            storedConnections.firstOrNull { connection ->
                connection.id == reference.connectionId && reference.modelId in connection.modelIds
            }?.let { return reference }
            return storedConnections.firstOrNull { connection ->
                reference.modelId in connection.modelIds
            }?.let { ModelReference(it.id, reference.modelId) } ?: reference
        }

        val chatModelRef = remapReference(rawChatModelRef)
        val activeChatConnection = chatModelRef?.let { reference ->
            storedConnections.firstOrNull { connection ->
                connection.id == reference.connectionId && reference.modelId in connection.modelIds
            }
        } ?: storedModel?.let { modelId ->
            storedConnections.firstOrNull { connection -> modelId in connection.modelIds }
        }
        val activeChatCapabilities = activeChatConnection?.capabilitiesFor(
            chatModelRef?.modelId ?: storedModel.orEmpty(),
        )

        ApiConfig(
            providerId = activeChatConnection?.providerId ?: provider?.id.orEmpty(),
            apiUrl = activeChatConnection?.apiUrl ?: storedUrl ?: provider?.apiUrl.orEmpty(),
            apiKey = activeChatConnection?.apiKey
                ?: activeConnectionId?.let(secrets::get)
                ?: providerId?.let { provider -> secrets[connectionIdForProvider(provider)] }
                ?: prefs[KEY_API_KEY].orEmpty(),
            model = when (chatModelRef?.modelId ?: storedModel) {
                "openrouter/free" -> provider?.defaultModel.orEmpty()
                null -> provider?.defaultModel.orEmpty()
                else -> chatModelRef?.modelId ?: storedModel.orEmpty()
            },
            visionModel = prefs[KEY_VISION_MODEL] ?: "",
            imageModel = prefs[KEY_IMAGE_MODEL] ?: "",
            localModel = prefs[KEY_LOCAL_MODEL] ?: "",
            localModelDirectEnabled = prefs[KEY_LOCAL_MODEL_DIRECT_ENABLED] ?: false,
            offlineFallbackEnabled = prefs[KEY_OFFLINE_FALLBACK_ENABLED] ?: false,
            toolsEnabled = activeChatCapabilities?.supportsTools
                ?: prefs[KEY_TOOLS_ENABLED]
                ?: provider?.toolsEnabledByDefault
                ?: false,
            requireToolConfirmation = prefs[KEY_REQUIRE_TOOL_CONFIRMATION] ?: true,
            verifiedSignature = activeChatConnection?.verifiedModelSignatures
                ?.get(chatModelRef?.modelId ?: storedModel.orEmpty())
                ?: activeChatConnection?.verifiedSignature
                ?: prefs[KEY_VERIFIED_SIGNATURE]
                ?: "",
            connections = storedConnections,
            chatModelRef = chatModelRef,
            visionModelRef = remapReference(rawVisionModelRef),
            imageModelRef = remapReference(rawImageModelRef),
            dynamicLocalRoutingEnabled = prefs[KEY_DYNAMIC_LOCAL_ROUTING] ?: false,
            phoneToolsEnabled = prefs[KEY_PHONE_TOOLS_ENABLED] ?: false,
        )
    }

    suspend fun updateConfig(config: ApiConfig) {
        val active = config.withActiveConnection()
        val normalizedConnections = splitModelScopedConnections(active.resolvedConnections())

        fun remapReference(reference: ModelReference?): ModelReference? {
            if (reference == null) return null
            normalizedConnections.firstOrNull { connection ->
                connection.id == reference.connectionId && reference.modelId in connection.modelIds
            }?.let { return reference }
            return normalizedConnections.firstOrNull { connection ->
                reference.modelId in connection.modelIds
            }?.let { connection -> ModelReference(connection.id, reference.modelId) } ?: reference
        }

        val normalized = active.copy(
            connections = normalizedConnections,
            chatModelRef = remapReference(active.resolvedChatModelRef()),
            visionModelRef = remapReference(active.resolvedVisionModelRef()),
            imageModelRef = remapReference(active.resolvedImageModelRef()),
        )
        val connectionsWithoutSecrets = normalized.resolvedConnections().map { it.copy(apiKey = "") }
        val encryptedSecrets = credentialVault.encrypt(
            json.encodeToString(
                PersistedApiSecrets.serializer(),
                PersistedApiSecrets(
                    normalized.resolvedConnections().associate { it.id to it.apiKey },
                ),
            ),
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ID] = normalized.providerId
            prefs[KEY_API_URL] = normalized.apiUrl
            prefs.remove(KEY_API_KEY)
            prefs[KEY_MODEL] = normalized.model
            prefs[KEY_VISION_MODEL] = normalized.visionModel
            prefs[KEY_IMAGE_MODEL] = normalized.imageModel
            prefs[KEY_LOCAL_MODEL] = normalized.localModel
            prefs[KEY_LOCAL_MODEL_DIRECT_ENABLED] = normalized.localModelDirectEnabled
            prefs[KEY_OFFLINE_FALLBACK_ENABLED] = normalized.offlineFallbackEnabled
            prefs[KEY_TOOLS_ENABLED] = normalized.toolsEnabled
            prefs[KEY_REQUIRE_TOOL_CONFIRMATION] = normalized.requireToolConfirmation
            prefs[KEY_VERIFIED_SIGNATURE] = normalized.verifiedSignature
            prefs[KEY_CONNECTIONS] = json.encodeToString(
                PersistedApiConnections.serializer(),
                PersistedApiConnections(connectionsWithoutSecrets),
            )
            prefs[KEY_CONNECTION_SECRETS] = encryptedSecrets
            writeModelReference(prefs, KEY_CHAT_MODEL_REF, normalized.resolvedChatModelRef())
            writeModelReference(prefs, KEY_VISION_MODEL_REF, normalized.resolvedVisionModelRef())
            writeModelReference(prefs, KEY_IMAGE_MODEL_REF, normalized.resolvedImageModelRef())
            prefs[KEY_DYNAMIC_LOCAL_ROUTING] = normalized.dynamicLocalRoutingEnabled
            prefs[KEY_PHONE_TOOLS_ENABLED] = normalized.phoneToolsEnabled
        }
    }

    private suspend fun migrateLegacyApiKey() {
        context.dataStore.edit { prefs ->
            val legacyKey = prefs[KEY_API_KEY].orEmpty()
            if (legacyKey.isBlank() || !prefs[KEY_CONNECTION_SECRETS].isNullOrBlank()) return@edit
            val providerId = prefs[KEY_PROVIDER_ID]
                ?: prefs[KEY_API_URL]?.let(AiProviderCatalog::inferProviderId)
                ?: AiProviderCatalog.DEFAULT_PROVIDER_ID
            val modelId = prefs[KEY_MODEL].orEmpty()
            val legacySecretIds = buildMap {
                put(connectionIdForProvider(providerId), legacyKey)
                if (modelId.isNotBlank()) {
                    put(
                        connectionIdForModel(
                            providerId,
                            prefs[KEY_API_URL].orEmpty(),
                            modelId,
                        ),
                        legacyKey,
                    )
                }
            }
            val payload = json.encodeToString(
                PersistedApiSecrets.serializer(),
                PersistedApiSecrets(legacySecretIds),
            )
            runCatching { credentialVault.encrypt(payload) }
                .onSuccess { encrypted ->
                    prefs[KEY_CONNECTION_SECRETS] = encrypted
                    prefs.remove(KEY_API_KEY)
                }
        }
    }

    private fun decodeConnections(encoded: String): List<ApiConnection> = runCatching {
        json.decodeFromString(PersistedApiConnections.serializer(), encoded).items
    }.getOrDefault(emptyList())

    private fun decodeSecrets(encoded: String): Map<String, String> = runCatching {
        val decrypted = credentialVault.decrypt(encoded)
        json.decodeFromString(PersistedApiSecrets.serializer(), decrypted).values
    }.getOrDefault(emptyMap())

    private fun decodeModelReference(encoded: String?): ModelReference? = encoded
        ?.takeIf(String::isNotBlank)
        ?.let { value ->
            runCatching { json.decodeFromString(ModelReference.serializer(), value) }.getOrNull()
        }

    private fun writeModelReference(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        reference: ModelReference?,
    ) {
        if (reference?.isValid == true) {
            prefs[key] = json.encodeToString(ModelReference.serializer(), reference)
        } else {
            prefs.remove(key)
        }
    }
}

@Serializable
private data class PersistedApiConnections(
    val items: List<ApiConnection>,
)

@Serializable
private data class PersistedApiSecrets(
    val values: Map<String, String>,
)
