package com.homeglass.app.data.ha

import com.homeglass.app.BuildConfig
import com.homeglass.app.domain.ha.HaCredentials
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/** Verifier / challenge PKCE + `state` anti-CSRF. */
data class Pkce(val verifier: String, val challenge: String, val state: String)

/**
 * Authentification OAuth2 / IndieAuth de Home Assistant.
 *
 * Home Assistant identifie l'app par l'URL de son site (`client_id`). Un `redirect_uri` sur
 * un autre hôte — indispensable pour un schéma d'application — doit être déclaré sur cette
 * page (`<link rel="redirect_uri" href="...">`) ou dans un document de métadonnées JSON.
 * Les deux valeurs viennent de `gradle.properties` (voir README), jamais du code : sans
 * configuration, l'écran de bienvenue n'affiche que le jeton longue durée.
 */
class HaAuthManager(private val http: HaHttpClient) {

    val clientId: String = BuildConfig.HA_OAUTH_CLIENT_ID
    val redirectUri: String = BuildConfig.HA_OAUTH_REDIRECT_URI

    val isConfigured: Boolean
        get() = clientId.startsWith("https://") && redirectUri.isNotBlank()

    fun createPkce(random: SecureRandom = SecureRandom()): Pkce {
        val bytes = ByteArray(32).also(random::nextBytes)
        val verifier = bytes.encodeBase64Url()
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Pkce(
            verifier = verifier,
            challenge = digest.encodeBase64Url(),
            state = ByteArray(16).also(random::nextBytes).encodeBase64Url(),
        )
    }

    /** URL d'autorisation à ouvrir dans un Custom Tab. */
    fun authorizeUrl(baseUrl: String, pkce: Pkce): String {
        val query = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "state" to pkce.state,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
        ).joinToString("&") { (key, value) -> "$key=${value.urlEncode()}" }
        return "${baseUrl.trimEnd('/')}/auth/authorize?$query"
    }

    suspend fun exchange(baseUrl: String, code: String, pkce: Pkce): Result<HaCredentials> =
        http.exchangeCode(
            baseUrl = baseUrl,
            code = code,
            codeVerifier = pkce.verifier,
            clientId = clientId,
            redirectUri = redirectUri,
        )

    private fun ByteArray.encodeBase64Url(): String =
        Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}
