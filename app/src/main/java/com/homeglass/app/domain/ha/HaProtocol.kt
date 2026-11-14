package com.homeglass.app.domain.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Protocole du WebSocket Home Assistant : construction des commandes et lecture des
 * messages. Pur (aucune dépendance Android/OkHttp) pour être testé directement.
 *
 * Séquence : le serveur envoie `auth_required`, on répond `auth`, on reçoit `auth_ok` puis on
 * envoie des commandes numérotées. Les résultats portent le même `id`, les événements
 * arrivent avec `type: "event"`.
 */
sealed interface HaMessage {
    data class AuthRequired(val haVersion: String?) : HaMessage
    data object AuthOk : HaMessage
    data class AuthInvalid(val message: String?) : HaMessage
    data class Result(
        val id: Int,
        val success: Boolean,
        val result: JsonElement? = null,
        val error: HaError? = null,
    ) : HaMessage

    data class Event(val id: Int, val eventType: String?, val data: JsonObject?) : HaMessage
    data class Pong(val id: Int) : HaMessage
    data class Unknown(val type: String?) : HaMessage
}

data class HaError(val code: String = "unknown", val message: String = "")

object HaProtocol {

    const val TYPE_AUTH = "auth"
    const val TYPE_AUTH_REQUIRED = "auth_required"
    const val TYPE_AUTH_OK = "auth_ok"
    const val TYPE_AUTH_INVALID = "auth_invalid"
    const val TYPE_RESULT = "result"
    const val TYPE_EVENT = "event"
    const val TYPE_PONG = "pong"

    const val CMD_GET_STATES = "get_states"
    const val CMD_SUBSCRIBE_EVENTS = "subscribe_events"
    const val CMD_SUBSCRIBE_ENTITIES = "subscribe_entities"
    const val CMD_AREAS = "config/area_registry/list"
    const val CMD_ENTITIES = "config/entity_registry/list"
    const val CMD_DEVICES = "config/device_registry/list"
    const val CMD_CALL_SERVICE = "call_service"
    const val EVENT_STATE_CHANGED = "state_changed"

    // ---- Commandes sortantes -------------------------------------------------

    /**
     * Message d'authentification.
     *
     * [includeLegacyToken] ajoute le champ `token` à côté de `access_token` : les versions
     * de l'add-on antérieures au correctif ne lisent que `token`. Home Assistant ignore les
     * champs inconnus, l'ajout reste donc sans effet sur une connexion directe.
     */
    fun auth(accessToken: String, includeLegacyToken: Boolean = false): String =
        buildJsonObject {
            put("type", TYPE_AUTH)
            put("access_token", accessToken)
            if (includeLegacyToken) put("token", accessToken)
        }.toString()

    fun request(id: Int, type: String): String = buildJsonObject {
        put("id", id)
        put("type", type)
    }.toString()

    fun getStates(id: Int): String = request(id, CMD_GET_STATES)

    fun subscribeEvents(id: Int, eventType: String = EVENT_STATE_CHANGED): String = buildJsonObject {
        put("id", id)
        put("type", CMD_SUBSCRIBE_EVENTS)
        put("event_type", eventType)
    }.toString()

    fun listAreas(id: Int): String = request(id, CMD_AREAS)

    fun listEntities(id: Int): String = request(id, CMD_ENTITIES)

    fun listDevices(id: Int): String = request(id, CMD_DEVICES)

    fun ping(id: Int): String = request(id, "ping")

    fun callService(
        id: Int,
        domain: String,
        service: String,
        data: JsonObject? = null,
        target: JsonObject? = null,
    ): String = buildJsonObject {
        put("id", id)
        put("type", CMD_CALL_SERVICE)
        put("domain", domain)
        put("service", service)
        if (data != null) put("service_data", data)
        if (target != null) put("target", target)
    }.toString()

    // ---- Messages entrants ---------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): HaMessage {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        val obj = element as? JsonObject ?: return HaMessage.Unknown(null)
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull

        return when (type) {
            TYPE_AUTH_REQUIRED -> HaMessage.AuthRequired(
                (obj["ha_version"] as? JsonPrimitive)?.contentOrNull,
            )

            TYPE_AUTH_OK -> HaMessage.AuthOk

            TYPE_AUTH_INVALID -> HaMessage.AuthInvalid(
                (obj["message"] as? JsonPrimitive)?.contentOrNull,
            )

            TYPE_RESULT -> {
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
                val success = (obj["success"] as? JsonPrimitive)?.contentOrNull?.toBoolean() ?: false
                val error = (obj["error"] as? JsonObject)?.let { err ->
                    HaError(
                        code = (err["code"] as? JsonPrimitive)?.contentOrNull ?: "unknown",
                        message = (err["message"] as? JsonPrimitive)?.contentOrNull ?: "",
                    )
                }
                HaMessage.Result(
                    id = id,
                    success = success,
                    result = obj["result"],
                    error = error,
                )
            }

            TYPE_EVENT -> {
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
                val event = obj["event"] as? JsonObject
                HaMessage.Event(
                    id = id,
                    eventType = (event?.get("event_type") as? JsonPrimitive)?.contentOrNull,
                    data = event?.get("data") as? JsonObject,
                )
            }

            TYPE_PONG -> HaMessage.Pong((obj["id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0)

            else -> HaMessage.Unknown(type)
        }
    }

    /**
     * Extrait `event.data.new_state` d'un `state_changed`.
     * `null` quand l'événement concerne une entité supprimée (`new_state` absent).
     */
    fun newStateFromEvent(message: HaMessage.Event): EntityState? {
        if (message.eventType != EVENT_STATE_CHANGED) return null
        val newState = message.data?.get("new_state") ?: return null
        return EntityState.fromJson(newState)
    }

    /** Identifiant d'entité d'un `state_changed`, même quand le nouvel état est absent. */
    fun entityIdFromEvent(message: HaMessage.Event): String? {
        val data = message.data ?: return null
        val id = data["entity_id"] as? JsonPrimitive
        if (id != null) return id.contentOrNull
        val newState = data["new_state"] as? JsonObject ?: return null
        return (newState["entity_id"] as? JsonPrimitive)?.contentOrNull
    }
}
