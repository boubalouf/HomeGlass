package com.homeglass.app.domain.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Demande d'appairage acceptée par l'add-on : le code est affiché dans Home Assistant. */
data class PairingStart(
    val pairingId: String,
    val deviceId: String,
    val expiresInSeconds: Int,
    val codeLength: Int,
)

/** Jeton d'appareil obtenu en échange du code. */
data class PairingClaim(
    val token: String,
    val deviceId: String,
    val deviceName: String?,
)

/**
 * Lecture des réponses d'appairage de HomeGlass Server.
 *
 * Logique pure : aucune dépendance Android, testable directement.
 */
object PairingProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseStart(body: String): PairingStart? {
        val obj = parse(body) ?: return null
        val pairingId = obj.text("pairingId") ?: return null
        return PairingStart(
            pairingId = pairingId,
            deviceId = obj.text("deviceId").orEmpty(),
            expiresInSeconds = obj["expiresIn"]?.jsonPrimitive?.intOrNull ?: 0,
            codeLength = obj["codeLength"]?.jsonPrimitive?.intOrNull ?: DEFAULT_CODE_LENGTH,
        )
    }

    fun parseClaim(body: String): PairingClaim? {
        val obj = parse(body) ?: return null
        val token = obj.text("token") ?: return null
        return PairingClaim(
            token = token,
            deviceId = obj.text("deviceId").orEmpty(),
            deviceName = obj.text("deviceName")?.takeIf { it.isNotBlank() },
        )
    }

    /** Ne garde que les chiffres du code saisi (espaces, tirets) et le borne à sa longueur. */
    fun normalizeCode(raw: String, length: Int = DEFAULT_CODE_LENGTH): String =
        raw.filter { it.isDigit() }.take(length)

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()

    private fun JsonObject.text(key: String): String? =
        (this[key])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    const val DEFAULT_CODE_LENGTH = 6
}
