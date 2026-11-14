package com.homeglass.app.data.custom

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.homeglass.app.domain.dashboard.CustomDashboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.customDashboardDataStore by preferencesDataStore(name = "homeglass_dashboard")

/**
 * Tableau de bord personnel : zones nommées et appareils, dans l'ordre choisi.
 *
 * Le format courant est un document JSON ; l'ancien format (simple liste d'appareils) reste
 * lu puis migré au premier enregistrement, pour ne pas perdre la sélection existante. Une
 * valeur illisible retombe sur « aucun tableau personnalisé » plutôt que de casser l'écran.
 */
class CustomDashboardStore(private val context: Context) {

    val dashboard: Flow<CustomDashboard> = context.customDashboardDataStore.data.map { preferences ->
        val current: CustomDashboard? = preferences[KEY]?.let(::decode)
        when {
            current != null -> current
            else -> preferences[LEGACY_KEY]?.let(::decodeLegacy)?.let(CustomDashboard::single)
                ?: CustomDashboard()
        }
    }

    suspend fun save(dashboard: CustomDashboard) = withContext(Dispatchers.IO) {
        val cleaned = dashboard.normalized()
        context.customDashboardDataStore.edit { preferences ->
            preferences.remove(LEGACY_KEY)
            if (cleaned.isConfigured) {
                preferences[KEY] = Json.encodeToString(CustomDashboard.serializer(), cleaned)
            } else {
                preferences.remove(KEY)
            }
        }
    }

    private fun decode(raw: String): CustomDashboard? =
        runCatching { Json.decodeFromString(CustomDashboard.serializer(), raw) }.getOrNull()

    private fun decodeLegacy(raw: String): List<String>? =
        runCatching { Json.decodeFromString(legacySerializer, raw) }.getOrNull()

    private companion object {
        val KEY = stringPreferencesKey("dashboard")
        val LEGACY_KEY = stringPreferencesKey("entity_ids")
        val legacySerializer = ListSerializer(String.serializer())
    }
}
