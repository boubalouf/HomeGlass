package com.homeglass.app.data.widgets

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.homeglass.app.data.ha.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/** Photo du jour retenue pour la tuile : URL absolue et crédits affichables. */
data class DailyPhoto(
    val url: String,
    val title: String,
    val copyright: String,
)

/**
 * Photos d'ambiance pour la case photo.
 *
 * Source : fonds d'écran du jour publiés par Bing (le fond de la page d'accueil Edge), sans
 * clé d'API. L'archive expose les huit derniers jours ; une photo est tirée au hasard pour
 * varier d'un rafraîchissement à l'autre. Aucune dépendance d'affichage d'images
 * supplémentaire : le décodage passe par [BitmapFactory] et un client OkHttp partagé.
 */
object BingPhotoRepository {

    private const val BASE_URL = "https://www.bing.com"
    private const val ARCHIVE =
        "$BASE_URL/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=fr-FR"

    /** Largeur cible du bitmap : une tuile n'a jamais besoin de l'image pleine résolution. */
    private const val TARGET_WIDTH = 1280
    private const val MAX_CACHE_ENTRIES = 8

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy { HttpClientFactory.create() }

    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /** Tirage aléatoire parmi les photos récentes ; `null` hors ligne ou réponse illisible. */
    suspend fun randomPhoto(): DailyPhoto? = withContext(Dispatchers.IO) {
        val body = runCatching {
            client.newCall(Request.Builder().url(ARCHIVE).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        val images = runCatching {
            json.decodeFromString(Archive.serializer(), body).images
        }.getOrNull().orEmpty()

        images.filter { it.url.isNotBlank() }
            .shuffled()
            .firstOrNull()
            ?.let { image ->
                DailyPhoto(
                    url = if (image.url.startsWith("http")) image.url else BASE_URL + image.url,
                    title = image.title,
                    copyright = image.copyright,
                )
            }
    }

    /** Bitmap décodé et mis en cache ; `null` si le téléchargement échoue. */
    suspend fun bitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[url] }?.let { return@withContext it }

        val decoded = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                decodeDownsampled(bytes)
            }
        }.getOrNull() ?: return@withContext null

        synchronized(cache) { cache[url] = decoded }
        decoded
    }

    private fun decodeDownsampled(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    @Serializable
    private data class Archive(val images: List<ArchiveImage> = emptyList())

    @Serializable
    private data class ArchiveImage(
        val url: String = "",
        val title: String = "",
        val copyright: String = "",
    )
}
