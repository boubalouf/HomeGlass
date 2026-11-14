package com.homeglass.app.domain.ha

import java.net.URI
import java.net.URISyntaxException

/**
 * Choix de l'URL à utiliser pour joindre l'instance.
 *
 * Règle : sur le réseau local on privilégie l'URL interne (elle évite le détour par
 * l'extérieur et fonctionne sans internet) ; sinon l'URL externe. Chaque valeur peut manquer,
 * on retombe donc sur ce qui est disponible. Le choix est réévalué à chaque changement de
 * réseau par le contrôleur.
 */
object BaseUrlChooser {

    fun choose(
        internalUrl: String?,
        externalUrl: String?,
        fallbackUrl: String,
        onLocalNetwork: Boolean,
    ): String = when {
        onLocalNetwork && !internalUrl.isNullOrBlank() -> internalUrl.trimEnd('/')
        !externalUrl.isNullOrBlank() -> externalUrl.trimEnd('/')
        !internalUrl.isNullOrBlank() -> internalUrl.trimEnd('/')
        else -> fallbackUrl.trimEnd('/')
    }

    /**
     * Complète une saisie utilisateur : schéma déduit de l'adresse (HTTP pour le réseau local,
     * HTTPS sinon), barre finale retirée, suffixes `/api` tolérés.
     */
    fun normalize(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val host = trimmed.substringBefore('/').substringBefore(':')
            val scheme = if (CleartextPolicy.isPrivateHost(host)) "http://" else "https://"
            trimmed = scheme + trimmed
        }

        trimmed = trimmed.trimEnd('/')
        // « http://maison:8123/api » ou « /api/websocket » collés par erreur.
        listOf("/api/websocket", "/api").forEach { suffix ->
            if (trimmed.endsWith(suffix)) trimmed = trimmed.removeSuffix(suffix)
        }
        return trimmed.trimEnd('/')
    }

    /** Hôte d'une URL, ou la chaîne d'origine si elle n'est pas analysable. */
    fun hostOf(url: String): String = host(url) ?: url

    fun host(url: String): String? = try {
        URI(url).host?.takeIf { it.isNotBlank() }
    } catch (error: URISyntaxException) {
        null
    }
}

/**
 * HTTP en clair n'est accepté que vers une adresse privée (LAN, mDNS, boucle locale).
 *
 * Android ne sait pas exprimer un CIDR dans `network_security_config` : le fichier autorise
 * donc le trafic clair et c'est cette politique, appliquée à chaque requête, qui refuse le
 * HTTP vers internet (voir `HttpClientFactory`).
 */
object CleartextPolicy {

    /** Vrai si l'URL peut être utilisée telle quelle. */
    fun isAllowed(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return when (scheme) {
            "https" -> true
            "http" -> BaseUrlChooser.host(url)?.let(::isPrivateHost) == true
            else -> false
        }
    }

    fun isPrivateHost(host: String): Boolean {
        val normalized = host.trim().removeSurrounding("[", "]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".local") || normalized.endsWith(".lan")) return true
        if (normalized == "::1") return true
        if (normalized.startsWith("fe80:") || normalized.startsWith("fc") || normalized.startsWith("fd")) return true

        val octets = normalized.split('.')
        if (octets.size != 4) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        return when {
            numbers[0] == 127 -> true
            numbers[0] == 10 -> true
            numbers[0] == 192 && numbers[1] == 168 -> true
            numbers[0] == 172 && numbers[1] in 16..31 -> true
            numbers[0] == 169 && numbers[1] == 254 -> true
            else -> false
        }
    }
}
