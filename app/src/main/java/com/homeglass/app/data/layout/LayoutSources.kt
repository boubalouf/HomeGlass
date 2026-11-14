package com.homeglass.app.data.layout

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.layoutDataStore by preferencesDataStore(name = "homeglass_layout")

/** Cache DataStore : le JSON brut de la dernière mise en page exploitable. */
class DataStoreLayoutCache(private val context: Context) : LayoutCache {

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            context.layoutDataStore.data.first()[KEY]
        } catch (error: IOException) {
            // Cache illisible : on repart de la source distante, sans faire échouer l'écran.
            null
        }
    }

    override suspend fun write(raw: String) {
        withContext(Dispatchers.IO) {
            context.layoutDataStore.edit { preferences -> preferences[KEY] = raw }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            context.layoutDataStore.edit { preferences -> preferences.remove(KEY) }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("layout_json")
    }
}

/**
 * Source locale de développement : le fichier `assets/layout_sample.json` reproduit la
 * réponse de l'add-on, ce qui permet de travailler sans Home Assistant ni add-on.
 */
class AssetLayoutSource(
    private val context: Context,
    private val assetName: String = "layout_sample.json",
) : LayoutSource {

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}
