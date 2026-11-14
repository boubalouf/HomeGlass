package com.homeglass.app.domain.ha

import java.util.Locale
import kotlin.math.roundToInt

/** Nature de la valeur affichée sous le nom d'une tuile. */
enum class TileValueKind {
    None,
    On,
    Off,
    Percent,
    Measurement,
    Open,
    Closed,
    Locked,
    Unlocked,
    Playing,
    Paused,
    Text,
}

/**
 * Lecture d'affichage d'un état Home Assistant, indépendante d'Android : l'UI traduit
 * [TileValueKind] avec `strings.xml`. C'est ce qui rend le mapping testable sans appareil.
 *
 * @param level niveau 0..1 exploité par la tuile (luminosité, position du volet).
 * @param text valeur numérique déjà formatée, ou état brut quand aucune traduction n'existe.
 * @param unit unité fournie par l'entité (`°C`, `%`, `lx`…), jamais codée en dur.
 */
data class EntityPresentation(
    val available: Boolean,
    val active: Boolean,
    val level: Float? = null,
    val kind: TileValueKind = TileValueKind.None,
    val text: String? = null,
    val detail: String? = null,
    val unit: String? = null,
    val percent: Int? = null,
)

/** Domaines dont l'état « on » allume la tuile. */
private val toggleDomains = setOf("light", "switch", "input_boolean", "fan", "automation", "script", "scene")

fun EntityState.toPresentation(locale: Locale = Locale.getDefault()): EntityPresentation {
    if (!isAvailable) {
        return EntityPresentation(available = false, active = false)
    }

    return when (domain) {
        "light" -> {
            val on = state == "on"
            val percent = if (!on) {
                null
            } else {
                numberAttribute("brightness")
                    ?.let { (it * 100.0 / 255.0).roundToInt().coerceIn(0, 100) }
                    ?: 100
            }
            EntityPresentation(
                available = true,
                active = on,
                level = if (on) (percent ?: 100) / 100f else 0f,
                kind = if (on) TileValueKind.Percent else TileValueKind.Off,
                percent = percent,
            )
        }

        "switch", "input_boolean" -> EntityPresentation(
            available = true,
            active = state == "on",
            level = if (state == "on") 1f else 0f,
            kind = if (state == "on") TileValueKind.On else TileValueKind.Off,
        )

        "fan" -> {
            val percent = numberAttribute("percentage")?.roundToInt()
            EntityPresentation(
                available = true,
                active = state == "on",
                level = percent?.div(100f) ?: if (state == "on") 1f else 0f,
                kind = if (state == "on") TileValueKind.Percent else TileValueKind.Off,
                percent = percent,
            )
        }

        "cover" -> {
            val position = numberAttribute("current_position")?.roundToInt()?.coerceIn(0, 100)
            val open = state == "open" || state == "opening" || (position ?: 0) > 0
            EntityPresentation(
                available = true,
                active = open,
                level = position?.div(100f) ?: if (open) 1f else 0f,
                kind = if (open) TileValueKind.Open else TileValueKind.Closed,
                percent = position,
            )
        }

        "lock" -> {
            val locked = state == "locked"
            EntityPresentation(
                available = true,
                active = locked,
                kind = if (locked) TileValueKind.Locked else TileValueKind.Unlocked,
            )
        }

        "alarm_control_panel" -> EntityPresentation(
            available = true,
            active = state.startsWith("armed"),
            kind = TileValueKind.Text,
            text = state,
        )

        "climate", "water_heater" -> {
            val current = numberAttribute("current_temperature")
            val unit = stringAttribute("temperature_unit") ?: unit
            EntityPresentation(
                available = true,
                active = state != "off" && state != "unavailable",
                level = numberAttribute("temperature")?.let { it / 30.0 }?.toFloat()?.coerceIn(0f, 1f),
                kind = if (current != null) TileValueKind.Measurement else TileValueKind.Text,
                text = current?.let { formatNumber(it, locale) } ?: state,
                unit = unit,
            )
        }

        "media_player" -> {
            val kind = when (state) {
                "playing" -> TileValueKind.Playing
                "paused" -> TileValueKind.Paused
                "off" -> TileValueKind.Off
                else -> TileValueKind.On
            }
            EntityPresentation(
                available = true,
                active = state == "playing" || state == "paused" || state == "idle",
                level = numberAttribute("volume_level")?.toFloat(),
                kind = kind,
                detail = stringAttribute("media_title")
                    ?: stringAttribute("media_artist")
                    ?: stringAttribute("friendly_name"),
            )
        }

        "vacuum" -> EntityPresentation(
            available = true,
            active = state == "cleaning" || state == "returning",
            kind = TileValueKind.Text,
            text = state,
        )

        "sensor", "number", "input_number" -> {
            val numeric = state.toDoubleOrNull()
            if (numeric != null && !unit.isNullOrBlank()) {
                EntityPresentation(
                    available = true,
                    active = true,
                    kind = TileValueKind.Measurement,
                    text = formatNumber(numeric, locale),
                    unit = unit,
                )
            } else {
                EntityPresentation(available = true, active = true, kind = TileValueKind.Text, text = state)
            }
        }

        "binary_sensor" -> EntityPresentation(
            available = true,
            active = state == "on",
            kind = if (state == "on") TileValueKind.On else TileValueKind.Off,
        )

        else -> when {
            domain in toggleDomains -> EntityPresentation(
                available = true,
                active = state == "on",
                level = if (state == "on") 1f else 0f,
                kind = if (state == "on") TileValueKind.On else TileValueKind.Off,
            )

            state == "on" -> EntityPresentation(available = true, active = true, kind = TileValueKind.On)
            state == "off" -> EntityPresentation(available = true, active = false, kind = TileValueKind.Off)
            else -> EntityPresentation(
                available = true,
                active = false,
                kind = TileValueKind.Text,
                text = state,
            )
        }
    }
}

/** 21,5 en français / 21.5 en anglais, sans zéro inutile. */
internal fun formatNumber(value: Double, locale: Locale): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(locale, "%.1f", value)
