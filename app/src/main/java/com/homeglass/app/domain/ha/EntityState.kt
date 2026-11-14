package com.homeglass.app.domain.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * État d'une entité Home Assistant, tel que renvoyé par `get_states` puis par les événements
 * `state_changed`. Les attributs restent bruts ([JsonElement]) : ils varient par intégration.
 */
@Serializable
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val lastChanged: String? = null,
    val lastUpdated: String? = null,
) {
    val domain: String get() = entityId.substringBefore('.', missingDelimiterValue = "")

    val isAvailable: Boolean
        get() = state != UNAVAILABLE && state != UNKNOWN

    val friendlyName: String? get() = stringAttribute("friendly_name")

    val unit: String? get() = stringAttribute("unit_of_measurement")

    fun stringAttribute(key: String): String? =
        (attributes[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    fun numberAttribute(key: String): Double? =
        (attributes[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    fun booleanAttribute(key: String): Boolean? =
        (attributes[key] as? JsonPrimitive)?.booleanOrNull

    fun stringListAttribute(key: String): List<String> =
        (attributes[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

    fun copyWithState(newState: String): EntityState = copy(state = newState)

    companion object {
        const val UNAVAILABLE = "unavailable"
        const val UNKNOWN = "unknown"

        /** Décode un objet `{"entity_id": ..., "state": ..., "attributes": {...}}`. */
        fun fromJson(element: JsonElement): EntityState? {
            val obj = element as? JsonObject ?: return null
            val entityId = (obj["entity_id"] as? JsonPrimitive)?.contentOrNull ?: return null
            val state = (obj["state"] as? JsonPrimitive)?.contentOrNull ?: return null
            val attributes = (obj["attributes"] as? JsonObject)
                ?.mapValues { it.value }
                .orEmpty()
            return EntityState(
                entityId = entityId,
                state = state,
                attributes = attributes,
                lastChanged = (obj["last_changed"] as? JsonPrimitive)?.contentOrNull,
                lastUpdated = (obj["last_updated"] as? JsonPrimitive)?.contentOrNull,
            )
        }

        /** Décode une liste d'états (`get_states`), en ignorant les entrées illisibles. */
        fun listFromJson(element: JsonElement): List<EntityState> {
            val array = element as? JsonArray ?: return emptyList()
            return array.mapNotNull(::fromJson)
        }

        /** Vrai si `config/entity_registry/list` marque l'entité comme masquée. */
        internal val Json: Json = Json { ignoreUnknownKeys = true }
    }
}
