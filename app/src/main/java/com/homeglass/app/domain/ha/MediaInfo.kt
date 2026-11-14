package com.homeglass.app.domain.ha

/**
 * Piste en cours de lecture d'une enceinte, telle que Home Assistant la publie dans les
 * attributs de l'entité. Aucune valeur n'est inventée : un attribut absent reste `null` et
 * l'interface omet la ligne correspondante.
 */
data class MediaInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** `entity_picture` : URL absolue, ou chemin relatif à l'instance Home Assistant. */
    val artworkPath: String? = null,
) {
    /** URL absolue de la pochette, `null` si elle est absente ou non résoluble. */
    fun artworkUrl(baseUrl: String?): String? {
        val raw = artworkPath?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        return "$base/${raw.trimStart('/')}"
    }
}

/**
 * Lecture des informations de lecture d'une enceinte (`media_player`).
 *
 * `entity_picture_local` est préféré en dernier recours : c'est une adresse interne à Home
 * Assistant, inutilisable depuis la tablette.
 */
fun EntityState.mediaInfo(): MediaInfo? {
    if (domain != "media_player") return null

    fun attribute(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        stringAttribute(key)?.takeIf { it.isNotBlank() }
    }

    return MediaInfo(
        title = attribute("media_title"),
        artist = attribute("media_artist", "media_album_artist"),
        album = attribute("media_album_name", "media_series_title"),
        artworkPath = attribute("entity_picture", "entity_picture_local"),
    )
}
