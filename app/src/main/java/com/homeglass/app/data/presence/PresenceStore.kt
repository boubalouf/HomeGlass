package com.homeglass.app.data.presence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.homeglass.app.domain.presence.PresenceEntry
import com.homeglass.app.domain.presence.PresenceSource
import com.homeglass.app.domain.presence.PresenceTuning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.presenceDataStore by preferencesDataStore(name = "homeglass_presence")

/**
 * Réglages locaux du réveil de présence : activation, déclencheur, seuil de distance,
 * anti-rebond et journal des derniers passages.
 *
 * Les valeurs relues sont toujours ramenées dans les bornes de [PresenceTuning], pour qu'un
 * réglage corrompu ne puisse pas rendre la détection inutilisable. Le journal est borné :
 * il sert au diagnostic, pas d'historique.
 */
class PresenceStore(private val context: Context) {

    val enabled: Flow<Boolean> = context.presenceDataStore.data.map { preferences ->
        preferences[ENABLED] ?: false
    }

    val source: Flow<PresenceSource> = context.presenceDataStore.data.map { preferences ->
        val stored = preferences[SOURCE]
        PresenceSource.entries.firstOrNull { it.name == stored } ?: PresenceSource.Auto
    }

    val thresholdCm: Flow<Float> = context.presenceDataStore.data.map { preferences ->
        PresenceTuning.clampThreshold(preferences[THRESHOLD] ?: PresenceTuning.DEFAULT_THRESHOLD_CM)
    }

    val cooldownMs: Flow<Long> = context.presenceDataStore.data.map { preferences ->
        PresenceTuning.clampCooldown(preferences[COOLDOWN] ?: PresenceTuning.DEFAULT_COOLDOWN_MS)
    }

    /** Du plus récent au plus ancien. */
    val log: Flow<List<PresenceEntry>> = context.presenceDataStore.data.map { preferences ->
        preferences[LOG]?.let(::decodeLog).orEmpty()
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.presenceDataStore.edit { preferences ->
            if (enabled) preferences[ENABLED] = true else preferences.remove(ENABLED)
        }
    }

    suspend fun setSource(source: PresenceSource) {
        context.presenceDataStore.edit { preferences ->
            preferences[SOURCE] = source.name
        }
    }

    suspend fun setThresholdCm(value: Float) {
        context.presenceDataStore.edit { preferences ->
            preferences[THRESHOLD] = PresenceTuning.clampThreshold(value)
        }
    }

    suspend fun setCooldownMs(value: Long) {
        context.presenceDataStore.edit { preferences ->
            preferences[COOLDOWN] = PresenceTuning.clampCooldown(value)
        }
    }

    suspend fun record(entry: PresenceEntry) {
        context.presenceDataStore.edit { preferences ->
            val current = preferences[LOG]?.let(::decodeLog).orEmpty()
            val updated = (listOf(entry) + current).take(MAX_ENTRIES)
            preferences[LOG] = Json.encodeToString(logSerializer, updated)
        }
    }

    suspend fun clearLog() {
        context.presenceDataStore.edit { preferences -> preferences.remove(LOG) }
    }

    private fun decodeLog(raw: String): List<PresenceEntry>? =
        runCatching { Json.decodeFromString(logSerializer, raw) }.getOrNull()

    private companion object {
        val ENABLED = booleanPreferencesKey("presence_wake_up")
        val SOURCE = stringPreferencesKey("presence_source")
        val THRESHOLD = floatPreferencesKey("presence_threshold_cm")
        val COOLDOWN = longPreferencesKey("presence_cooldown_ms")
        val LOG = stringPreferencesKey("presence_log")

        val logSerializer = ListSerializer(PresenceEntry.serializer())
        const val MAX_ENTRIES = 20
    }
}
