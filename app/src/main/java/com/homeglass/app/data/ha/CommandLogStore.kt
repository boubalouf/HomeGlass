package com.homeglass.app.data.ha

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.homeglass.app.domain.ha.CommandLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.commandsDataStore by preferencesDataStore(name = "homeglass_commands")

/**
 * Journal local des commandes Home Assistant, du plus récent au plus ancien.
 *
 * Les commandes identiques envoyées à moins d'une seconde d'intervalle sont fusionnées :
 * le glissement d'un curseur de luminosité produit une rafale de `light/turn_on` sans
 * intérêt pour le diagnostic. Le journal est borné et effaçable depuis les Réglages.
 */
class CommandLogStore(private val context: Context) {

    val entries: Flow<List<CommandLogEntry>> = context.commandsDataStore.data.map { preferences ->
        preferences[LOG]?.let(::decode).orEmpty()
    }

    suspend fun record(entry: CommandLogEntry) {
        context.commandsDataStore.edit { preferences ->
            val current = preferences[LOG]?.let(::decode).orEmpty()
            val last = current.firstOrNull()
            val merged = last != null &&
                last.service == entry.service &&
                last.target == entry.target &&
                entry.at - last.at in 0..MERGE_WINDOW_MILLIS

            val updated = if (merged) {
                listOf(entry) + current.drop(1)
            } else {
                listOf(entry) + current
            }
            preferences[LOG] = Json.encodeToString(serializer, updated.take(MAX_ENTRIES))
        }
    }

    suspend fun clear() {
        context.commandsDataStore.edit { preferences -> preferences.remove(LOG) }
    }

    private fun decode(raw: String): List<CommandLogEntry>? =
        runCatching { Json.decodeFromString(serializer, raw) }.getOrNull()

    private companion object {
        val LOG = stringPreferencesKey("command_log")
        val serializer = ListSerializer(CommandLogEntry.serializer())
        const val MAX_ENTRIES = 50
        const val MERGE_WINDOW_MILLIS = 1_000L
    }
}
