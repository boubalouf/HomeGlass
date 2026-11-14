package com.homeglass.app.data.ha

import com.homeglass.app.domain.ha.HaCredentials
import com.homeglass.app.domain.ha.HaTransport
import com.homeglass.app.domain.ha.PairingClaim
import com.homeglass.app.domain.ha.PairingProtocol
import com.homeglass.app.domain.ha.PairingStart
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Résultat d'un test de joignabilité de `/api/`. */
sealed interface ProbeResult {
    data class Reachable(val requiresAuth: Boolean) : ProbeResult
    data object Unreachable : ProbeResult
}

/** Métadonnées d'une instance, lues après authentification. */
data class InstanceConfig(
    val locationName: String?,
    val version: String?,
    val internalUrl: String?,
    val externalUrl: String?,
)

sealed interface ConfigResult {
    data class Ok(val config: InstanceConfig) : ConfigResult
    data object Unauthorized : ConfigResult
    data object Unreachable : ConfigResult
}

class HaHttpException(val code: Int, message: String) : IOException(message)

/** HomeGlass Server repéré par son `GET /api/ping`. */
data class AddonInfo(val version: String, val haUuid: String?)

/** Résultat d'une demande d'appairage. */
sealed interface PairingStartResult {
    data class Ok(val start: PairingStart) : PairingStartResult

    /** L'adresse répond, mais ce n'est pas un HomeGlass Server. */
    data object NotAddon : PairingStartResult
    data object Unreachable : PairingStartResult
}

/** Résultat de l'échange du code contre un jeton d'appareil. */
sealed interface PairingClaimResult {
    data class Ok(val claim: PairingClaim) : PairingClaimResult
    data object WrongCode : PairingClaimResult
    data object Expired : PairingClaimResult
    data object TooManyAttempts : PairingClaimResult
    data object Unreachable : PairingClaimResult
}

/**
 * Appels REST Home Assistant : joignabilité, `/api/config`, services, jetons OAuth.
 * Toutes les méthodes sont sûres : elles renvoient un résultat, jamais une exception non
 * gérée (le contrôleur décide quoi montrer).
 */
class HaHttpClient(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun probe(baseUrl: String): ProbeResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/api/").get().build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> ProbeResult.Reachable(requiresAuth = false)
                    response.code == 401 || response.code == 403 -> ProbeResult.Reachable(requiresAuth = true)
                    else -> ProbeResult.Unreachable
                }
            }
        } catch (error: IOException) {
            ProbeResult.Unreachable
        }
    }

    /** Vrai si l'URL répond, même par un 401 (c'est le cas normal sans jeton). */
    suspend fun isReachable(baseUrl: String): Boolean = probe(baseUrl) is ProbeResult.Reachable

    suspend fun fetchConfig(
        baseUrl: String,
        accessToken: String,
        transport: HaTransport = HaTransport.Direct,
    ): ConfigResult =
        withContext(Dispatchers.IO) {
            val request = authorized(
                "${baseUrl.trimEnd('/')}${configPath(transport)}",
                accessToken,
            ).build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 -> ConfigResult.Unauthorized
                        !response.isSuccessful -> ConfigResult.Unreachable
                        else -> {
                            val body = response.body?.string().orEmpty()
                            val obj = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
                                ?: return@use ConfigResult.Unreachable
                            ConfigResult.Ok(
                                InstanceConfig(
                                    locationName = obj.string("location_name"),
                                    version = obj.string("version"),
                                    internalUrl = obj.string("internal_url"),
                                    externalUrl = obj.string("external_url"),
                                ),
                            )
                        }
                    }
                }
            } catch (error: IOException) {
                ConfigResult.Unreachable
            }
        }

    suspend fun callService(
        baseUrl: String,
        accessToken: String,
        call: ServiceCall,
        transport: HaTransport = HaTransport.Direct,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                call.data.forEach { (key, value) -> put(key, value) }
                call.target["entity_id"]?.let { put("entity_id", it) }
            }
            val request = authorized(
                url = "${baseUrl.trimEnd('/')}${servicesPath(transport)}/${call.domain}/${call.service}",
                accessToken = accessToken,
            )
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HaHttpException(response.code, "Service ${call.path} : ${response.code}")
            }
        }
    }

    // ---- HomeGlass Server : découverte et appairage --------------------------

    /**
     * Vrai si l'URL répond comme un HomeGlass Server.
     *
     * `GET /api/ping` est public et renvoie `app: "homeglass"` : c'est le test qui distingue
     * l'add-on d'une autre page du réseau.
     */
    suspend fun probeAddon(baseUrl: String): AddonInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/api/ping").get().build()
        try {
            client.newCall(request).execute().use { response ->
                val obj = if (response.isSuccessful) {
                    runCatching {
                        json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject
                    }.getOrNull()
                } else {
                    null
                }
                when {
                    obj == null -> null
                    obj.string("app") != "homeglass" -> null
                    else -> AddonInfo(
                        version = obj.string("version").orEmpty(),
                        haUuid = obj.string("ha_uuid"),
                    )
                }
            }
        } catch (error: IOException) {
            null
        }
    }

    /** Annonce l'application : l'add-on affiche alors un code dans Home Assistant. */
    suspend fun requestPairing(
        baseUrl: String,
        deviceName: String,
        model: String,
        appVersion: String,
    ): PairingStartResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("deviceName", deviceName)
            put("model", model)
            put("appVersion", appVersion)
        }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/pair/request")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> PairingStartResult.NotAddon
                    !response.isSuccessful -> PairingStartResult.Unreachable
                    else -> PairingProtocol.parseStart(body)
                        ?.let { start -> PairingStartResult.Ok(start) }
                        ?: PairingStartResult.NotAddon
                }
            }
        } catch (error: IOException) {
            PairingStartResult.Unreachable
        }
    }

    /** Échange le code lu dans Home Assistant contre un jeton d'appareil. */
    suspend fun claimPairing(
        baseUrl: String,
        pairingId: String,
        code: String,
    ): PairingClaimResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("pairingId", pairingId)
            put("code", code)
        }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/pair/claim")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> PairingProtocol.parseClaim(body)
                        ?.let { claim -> PairingClaimResult.Ok(claim) }
                        ?: PairingClaimResult.Unreachable
                    response.code == 403 -> PairingClaimResult.WrongCode
                    response.code == 404 -> PairingClaimResult.Expired
                    response.code == 429 -> PairingClaimResult.TooManyAttempts
                    else -> PairingClaimResult.Unreachable
                }
            }
        } catch (error: IOException) {
            PairingClaimResult.Unreachable
        }
    }

    private fun configPath(transport: HaTransport): String =
        if (transport == HaTransport.Addon) "/api/ha/config" else "/api/config"

    private fun servicesPath(transport: HaTransport): String =
        if (transport == HaTransport.Addon) "/api/ha/services" else "/api/services"

    // ---- OAuth ---------------------------------------------------------------

    suspend fun exchangeCode(
        baseUrl: String,
        code: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String,
    ): Result<HaCredentials> = tokenRequest(
        baseUrl = baseUrl,
        form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build(),
        clientId = clientId,
    )

    suspend fun refresh(
        baseUrl: String,
        refreshToken: String,
        clientId: String?,
    ): Result<HaCredentials> = tokenRequest(
        baseUrl = baseUrl,
        form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .apply { if (!clientId.isNullOrBlank()) add("client_id", clientId) }
            .build(),
        clientId = clientId,
        fallbackRefreshToken = refreshToken,
    )

    suspend fun revoke(baseUrl: String, refreshToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/auth/revoke")
                .post(FormBody.Builder().add("token", refreshToken).build())
                .build()
            client.newCall(request).execute().close()
        }
    }

    private suspend fun tokenRequest(
        baseUrl: String,
        form: FormBody,
        clientId: String?,
        fallbackRefreshToken: String? = null,
    ): Result<HaCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/auth/token")
                .post(form)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw HaHttpException(response.code, "Jeton refusé (${response.code})")
                }
                val obj = (runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull())
                    ?: throw HaHttpException(response.code, "Réponse de jeton illisible")

                val accessToken = obj.string("access_token")
                    ?: throw HaHttpException(response.code, "access_token absent")
                val expiresIn = obj.string("expires_in")?.toLongOrNull() ?: 0L
                HaCredentials(
                    accessToken = accessToken,
                    refreshToken = obj.string("refresh_token") ?: fallbackRefreshToken,
                    expiresAtMillis = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L,
                    clientId = clientId,
                    authProvider = obj.string("ha_auth_provider"),
                )
            }
        }
    }

    private fun authorized(url: String, accessToken: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

    private fun JsonObject.string(key: String): String? =
        (this[key])?.jsonPrimitive?.contentOrNull
}
