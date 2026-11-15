package com.homeglass.app.domain.dashboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type de case décorative locale, indépendante de Home Assistant : elle n'affiche pas
 * d'entité mais du contenu vivant (heure, photo, date).
 */
@Serializable
enum class WidgetKind {
    @SerialName("clock") Clock,
    @SerialName("photo") Photo,
    @SerialName("calendar") Calendar,
    @SerialName("title") Title;

    /** Thèmes proposés par l'éditeur, dans l'ordre d'affichage. Le premier est le défaut. */
    val themes: List<String>
        get() = when (this) {
            Clock -> listOf(CLOCK_CLASSIC, CLOCK_MINIMAL, CLOCK_BOLD)
            Photo -> listOf(PHOTO_COVER, PHOTO_SOFT)
            Calendar -> listOf(CALENDAR_MINIMAL, CALENDAR_WEEK, CALENDAR_FOCUS)
            Title -> listOf(TITLE_PLAIN)
        }

    val defaultTheme: String get() = themes.first()

    /** Ramène un thème inconnu (ancienne version, donnée corrompue) sur un thème valide. */
    fun coerceTheme(theme: String): String = theme.takeIf { it in themes } ?: defaultTheme

    companion object {
        /** Types proposés dans la palette. `Title` n'est conservé que pour relire
         *  d'anciens tableaux ; il n'est plus créé ni affiché. */
        val addable: List<WidgetKind> get() = listOf(Clock, Photo, Calendar)

        const val CLOCK_CLASSIC = "classic"
        const val CLOCK_MINIMAL = "minimal"
        const val CLOCK_BOLD = "bold"
        const val PHOTO_COVER = "cover"
        const val PHOTO_SOFT = "soft"
        const val CALENDAR_MINIMAL = "minimal"
        const val CALENDAR_WEEK = "week"
        const val CALENDAR_FOCUS = "focus"
        const val TITLE_PLAIN = "plain"
    }
}

/**
 * Une case décorative ajoutée par l'utilisateur : type, thème, texte libre (titre) et
 * identifiant stable.
 */
@Serializable
data class WidgetItem(
    val id: String,
    val kind: WidgetKind,
    val theme: String = "",
    /** Texte affiché par une case titre ; vide pour les autres types. */
    val text: String = "",
) {
    /** Thème toujours valide pour le type, garanti au chargement comme à l'enregistrement. */
    fun normalized(): WidgetItem = copy(theme = kind.coerceTheme(theme))
}
