package com.homeglass.app.data.ha

import com.homeglass.app.domain.ha.EntityState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Appel de service Home Assistant, prêt à être envoyé sur `POST /api/services/...`. */
data class ServiceCall(
    val domain: String,
    val service: String,
    val data: Map<String, JsonElement> = emptyMap(),
    val target: Map<String, JsonElement> = emptyMap(),
) {
    /** `light/turn_on`, utile pour les logs et les messages d'erreur. */
    val path: String get() = "$domain/$service"
}

/**
 * Traduction d'un geste de tuile en appel de service, par domaine.
 *
 * `toggle` couvre la plupart des domaines (light, switch, fan, input_boolean, automation) ;
 * les autres ont des services dédiés (cover, lock, scene, script, vacuum). Un domaine sans
 * bascule (climat, média, capteur) renvoie `null` : l'UI ouvre alors le panneau d'info.
 */
object TileCommands {

    fun toggle(state: EntityState?): ServiceCall? {
        val entityId = state?.entityId ?: return null
        val target = entityTarget(entityId)
        return when (state.domain) {
            "light", "switch", "fan", "input_boolean", "automation", "media_player" ->
                ServiceCall(state.domain, "toggle", target = target)

            "cover" -> {
                val open = state.state == "open" || state.state == "opening"
                ServiceCall("cover", if (open) "close_cover" else "open_cover", target = target)
            }

            "lock" -> ServiceCall(
                "lock",
                if (state.state == "locked") "unlock" else "lock",
                target = target,
            )

            "scene" -> ServiceCall("scene", "turn_on", target = target)
            "script" -> ServiceCall("script", "turn_on", target = target)
            "vacuum" -> ServiceCall(
                "vacuum",
                if (state.state == "cleaning") "return_to_base" else "start",
                target = target,
            )

            else -> null
        }
    }

    /** Luminosité 1..100 % ; 0 éteint la lumière (`brightness_pct = 0` n'existe pas). */
    fun brightness(entityId: String, percent: Int, transitionSeconds: Int = 0): ServiceCall {
        val clamped = percent.coerceIn(0, 100)
        return if (clamped == 0) {
            ServiceCall("light", "turn_off", target = entityTarget(entityId))
        } else {
            ServiceCall(
                domain = "light",
                service = "turn_on",
                data = buildMap {
                    put("brightness_pct", JsonPrimitive(clamped))
                    if (transitionSeconds > 0) put("transition", JsonPrimitive(transitionSeconds))
                },
                target = entityTarget(entityId),
            )
        }
    }

    /** Allumage ou extinction explicite, choisi par le panneau de contrôle. */
    fun power(entityId: String, on: Boolean): ServiceCall? {
        val domain = entityId.substringBefore('.', missingDelimiterValue = "")
        if (domain !in setOf("light", "switch", "fan", "input_boolean")) return null
        return ServiceCall(
            domain = domain,
            service = if (on) "turn_on" else "turn_off",
            target = entityTarget(entityId),
        )
    }

    /** Teinte d'une lumière : `light.turn_on` avec `rgb_color`. */
    fun lightColor(entityId: String, red: Int, green: Int, blue: Int): ServiceCall = ServiceCall(
        domain = "light",
        service = "turn_on",
        data = mapOf(
            "rgb_color" to JsonArray(
                listOf(red, green, blue).map { component ->
                    JsonPrimitive(component.coerceIn(0, 255))
                },
            ),
        ),
        target = entityTarget(entityId),
    )

    /** Température de couleur en kelvins (`color_temp_kelvin`, Home Assistant ≥ 2023). */
    fun lightColorTemperature(entityId: String, kelvin: Int): ServiceCall = ServiceCall(
        domain = "light",
        service = "turn_on",
        data = mapOf("color_temp_kelvin" to JsonPrimitive(kelvin.coerceIn(1000, 10_000))),
        target = entityTarget(entityId),
    )

    /** Lecture / pause d'une enceinte : un appui sur la tuile ne coupe pas l'alimentation. */
    fun mediaPlayPause(entityId: String): ServiceCall =
        ServiceCall("media_player", "media_play_pause", target = entityTarget(entityId))

    /** Piste suivante (`media_next_track` : nom réel du service dans Home Assistant). */
    fun mediaNext(entityId: String): ServiceCall =
        ServiceCall("media_player", "media_next_track", target = entityTarget(entityId))

    /** Piste précédente. */
    fun mediaPrevious(entityId: String): ServiceCall =
        ServiceCall("media_player", "media_previous_track", target = entityTarget(entityId))

    /** Volume d'une enceinte : 0,0 (muet) à 1,0 (maximum). */
    fun mediaVolume(entityId: String, level: Double): ServiceCall = ServiceCall(
        domain = "media_player",
        service = "volume_set",
        data = mapOf("volume_level" to JsonPrimitive(level.coerceIn(0.0, 1.0))),
        target = entityTarget(entityId),
    )

    /** Position d'un volet : 0 = fermé, 100 = ouvert. */
    fun coverPosition(entityId: String, percent: Int): ServiceCall = ServiceCall(
        domain = "cover",
        service = "set_cover_position",
        data = mapOf("position" to JsonPrimitive(percent.coerceIn(0, 100))),
        target = entityTarget(entityId),
    )

    /** Action `call_service` du layout : service, `data` et `target` transmis tels quels. */
    fun fromLayout(service: String, data: Map<String, JsonElement>?, target: Map<String, JsonElement>?): ServiceCall? {
        val domain = service.substringBefore('.', missingDelimiterValue = "")
        val name = service.substringAfter('.', missingDelimiterValue = "")
        if (domain.isBlank() || name.isBlank()) return null
        return ServiceCall(domain, name, data.orEmpty(), target.orEmpty())
    }

    fun entityTarget(entityId: String): Map<String, JsonElement> =
        mapOf("entity_id" to JsonPrimitive(entityId))
}
