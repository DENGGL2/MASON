package com.denggl2.mason.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.modelCapabilityHealthStore by preferencesDataStore(name = "model_capability_health")

data class ModelCapabilityHealth(
    val available: Boolean,
    val detail: String? = null,
)

data class ModelCapabilityHealthSnapshot(
    val signature: String = "",
    val checkedAtMillis: Long = 0L,
    val capabilities: Map<String, ModelCapabilityHealth> = emptyMap(),
)

@Singleton
class ModelCapabilityHealthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keys = mapOf(
        "聊天" to CapabilityKeys("chat"),
        "识图" to CapabilityKeys("vision"),
        "生图" to CapabilityKeys("image"),
        "工具调用" to CapabilityKeys("tools"),
    )

    val snapshot: Flow<ModelCapabilityHealthSnapshot> = context.modelCapabilityHealthStore.data.map { prefs ->
        ModelCapabilityHealthSnapshot(
            signature = prefs[KEY_SIGNATURE].orEmpty(),
            checkedAtMillis = prefs[KEY_CHECKED_AT] ?: 0L,
            capabilities = keys.mapNotNull { (label, key) ->
                prefs[key.available]?.let { available ->
                    label to ModelCapabilityHealth(available, prefs[key.detail]?.takeIf(String::isNotBlank))
                }
            }.toMap(),
        )
    }

    suspend fun save(config: ApiConfig, capabilities: Map<String, ModelCapabilityHealth>) {
        context.modelCapabilityHealthStore.edit { prefs ->
            prefs[KEY_SIGNATURE] = signatureFor(config)
            prefs[KEY_CHECKED_AT] = System.currentTimeMillis()
            keys.forEach { (label, key) ->
                val capability = capabilities[label]
                if (capability == null) {
                    prefs.remove(key.available)
                    prefs.remove(key.detail)
                } else {
                    prefs[key.available] = capability.available
                    capability.detail?.takeIf(String::isNotBlank)?.let { prefs[key.detail] = it } ?: prefs.remove(key.detail)
                }
            }
        }
    }

    fun isCurrent(config: ApiConfig, snapshot: ModelCapabilityHealthSnapshot): Boolean =
        snapshot.checkedAtMillis > 0L && snapshot.signature == signatureFor(config)

    private fun signatureFor(config: ApiConfig): String = listOf(
        AiProviderCatalog.verificationSignature(config),
        config.visionModel,
        config.imageModel,
        config.toolsEnabled,
    ).joinToString("|")

    private class CapabilityKeys(prefix: String) {
        val available: Preferences.Key<Boolean> = booleanPreferencesKey("${prefix}_available")
        val detail: Preferences.Key<String> = stringPreferencesKey("${prefix}_detail")
    }

    private companion object {
        val KEY_SIGNATURE = stringPreferencesKey("signature")
        val KEY_CHECKED_AT = longPreferencesKey("checked_at")
    }
}
