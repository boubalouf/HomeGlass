package com.homeglass.app.domain.ha

import kotlinx.serialization.Serializable

/**
 * Instance Home Assistant connue de l'appareil.
 *
 * Les URL internes et externes sont mémorisées : le client choisit la plus adaptée à chaque
 * changement de réseau (voir [BaseUrlChooser]).
 */
@Serializable
data class HaInstance(
    val baseUrl: String,
    val internalUrl: String? = null,
    val externalUrl: String? = null,
    val locationName: String? = null,
    val version: String? = null,
    val uuid: String? = null,
) {
    /** Nom affiché : nom de l'installation, sinon l'hôte. */
    val displayName: String
        get() = locationName?.takeIf { it.isNotBlank() } ?: BaseUrlChooser.hostOf(baseUrl)

    fun withBaseUrl(url: String): HaInstance = copy(baseUrl = url)
}

/** Jetons d'accès Home Assistant. */
@Serializable
data class HaCredentials(
    val accessToken: String,
    /** Présent pour les jetons OAuth ; `null` pour un jeton longue durée. */
    val refreshToken: String? = null,
    /** Échéance du jeton d'accès en millisecondes epoch ; 0 pour un jeton longue durée. */
    val expiresAtMillis: Long = 0L,
    /** `client_id` utilisé pour l'échange OAuth (nécessaire au rafraîchissement). */
    val clientId: String? = null,
    val authProvider: String? = null,
) {
    val isLongLived: Boolean get() = refreshToken == null

    /** Vrai quand le jeton d'accès OAuth arrive à échéance (marge de 60 s). */
    fun isExpiring(nowMillis: Long = System.currentTimeMillis(), marginMillis: Long = 60_000L): Boolean =
        refreshToken != null && expiresAtMillis > 0L && nowMillis >= expiresAtMillis - marginMillis
}

/**
 * Destination des requêtes.
 *
 * `Direct` parle à Home Assistant avec un jeton longue durée ou OAuth ; `Addon` parle à
 * HomeGlass Server avec un jeton d'appareil obtenu par appairage, l'add-on se chargeant de
 * Home Assistant.
 */
@Serializable
enum class HaTransport {
    Direct,
    Addon,
}

/** Session complète : l'instance, les jetons et la destination des requêtes. */
@Serializable
data class HaSession(
    val instance: HaInstance,
    val credentials: HaCredentials,
    val transport: HaTransport = HaTransport.Direct,
)
