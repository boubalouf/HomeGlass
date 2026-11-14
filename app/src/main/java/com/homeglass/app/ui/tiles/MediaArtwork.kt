package com.homeglass.app.ui.tiles

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.homeglass.app.data.ha.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Adresse de l'instance Home Assistant, nécessaire pour résoudre les chemins de pochettes
 * (`/api/media_player_proxy/...`). Fournie une fois à la racine de l'application.
 */
val LocalMediaArtworkBaseUrl = staticCompositionLocalOf<String?> { null }

/** Pochette de la piste en cours, chargée en tâche de fond puis gardée en mémoire. */
@Composable
fun rememberMediaArtwork(artworkUrl: String?): ImageBitmap? {
    var bitmap by remember(artworkUrl) { mutableStateOf(MediaArtworkCache.get(artworkUrl)) }

    LaunchedEffect(artworkUrl) {
        val url = artworkUrl ?: return@LaunchedEffect
        MediaArtworkCache.get(url)?.let { cached ->
            bitmap = cached
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) { MediaArtworkCache.load(url) }
    }

    return bitmap
}

/**
 * Téléchargement des pochettes avec [okhttp3.OkHttpClient] partagé : pas de dépendance
 * d'affichage d'images supplémentaire, et une pochette identique n'est téléchargée qu'une
 * fois par session (l'URL de Home Assistant change à chaque morceau).
 */
internal object MediaArtworkCache {

    private const val MAX_ENTRIES = 24

    private val entries = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > MAX_ENTRIES
    }

    private val client by lazy { HttpClientFactory.create() }

    @Synchronized
    fun get(url: String?): ImageBitmap? = url?.let(entries::get)

    fun load(url: String): ImageBitmap? {
        val decoded = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull() ?: return null

        synchronized(this) { entries[url] = decoded }
        return decoded
    }
}
