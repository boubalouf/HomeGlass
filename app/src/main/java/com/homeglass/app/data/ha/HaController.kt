package com.homeglass.app.data.ha

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.homeglass.app.data.layout.LayoutCache
import com.homeglass.app.data.layout.LayoutRepository
import com.homeglass.app.domain.ha.BaseUrlChooser
import com.homeglass.app.domain.ha.CleartextPolicy
import com.homeglass.app.domain.ha.ConnectionFailure
import com.homeglass.app.domain.ha.ConnectionStatus
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.HaCredentials
import com.homeglass.app.domain.ha.HaInstance
import com.homeglass.app.domain.ha.HaSession
import com.homeglass.app.domain.ha.HaTransport
import com.homeglass.app.domain.ha.PairingProtocol
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Erreur de commande, traduite en message discret par l'UI. */
enum class CommandError { Network, Unauthorized, Server, EntityMissing }

/** Résultat d'une tentative de connexion. */
sealed interface LoginResult {
    data object Connected : LoginResult
    data object InvalidToken : LoginResult
    data object Unreachable : LoginResult
    data object CleartextRefused : LoginResult

    /** OAuth non configuré (client_id / redirect_uri absents). */
    data object OAuthUnavailable : LoginResult

    /** Code d'appairage refusé par l'add-on. */
    data object CodeWrong : LoginResult
    data object CodeExpired : LoginResult
    data object TooManyAttempts : LoginResult

    /** Session appairée refusée par l'add-on (appareil révoqué, données réinitialisées). */
    data object SessionExpired : LoginResult

    /** L'adresse répond, mais ce n'est pas un HomeGlass Server. */
    data object NotAddonServer : LoginResult
    data class Failed(val error: CommandError) : LoginResult
}

/**
 * Point central côté application : session, liaison temps réel, commandes, génération de la
 * mise en page. Créé une fois par activité et conservé par `remember`.
 */
class HaController(
    context: Context,
    private val store: SessionStore,
    private val http: HaHttpClient,
    private val auth: HaAuthManager,
    private val networkMonitor: NetworkMonitor,
    private val discovery: HaDiscovery,
    cache: LayoutCache,
    private val scope: CoroutineScope,
) {

    private val appContext = context.applicationContext

    private val realtime = HaRealtime(
        client = HttpClientFactory.create(),
        scope = scope,
        baseUrlProvider = { _session.value?.instance?.baseUrl },
        credentialsProvider = { force -> refreshCredentials(force = force) },
        transportProvider = { _session.value?.transport ?: HaTransport.Direct },
    )

    private val generatedSource = GeneratedLayoutSource(appContext, realtime)

    /** Pipeline inchangé : cache DataStore, puis layout généré depuis Home Assistant. */
    val layoutRepository = LayoutRepository(cache = cache, local = generatedSource)

    private val _session = MutableStateFlow(store.read())
    val session: StateFlow<HaSession?> = _session.asStateFlow()

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()

    private val _lastError = MutableStateFlow<CommandError?>(null)
    val lastError: StateFlow<CommandError?> = _lastError.asStateFlow()

    /** Dernier échec de connexion OAuth (affiché par l'écran de bienvenue). */
    private val _loginResult = MutableStateFlow<LoginResult?>(null)
    val loginResult: StateFlow<LoginResult?> = _loginResult.asStateFlow()

    fun consumeLoginResult() {
        _loginResult.value = null
    }

    private var pendingOAuth: PendingOAuth? = null

    val oauthAvailable: Boolean get() = auth.isConfigured
    val oauthRedirectUri: String get() = auth.redirectUri

    /** Version de l'application, envoyée à l'add-on lors de l'appairage. */
    private val appVersion: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private data class PendingOAuth(val instance: HaInstance, val pkce: Pkce)

    init {
        scope.launch { realtime.states.collect { _states.value = it } }
        scope.launch {
            realtime.status.collect { status ->
                _status.value = status
                // Jeton refusé et aucun rafraîchissement possible : la session ne peut pas
                // revivre. On revient à l'appairage au lieu de laisser l'écran en chargement.
                if (status is ConnectionStatus.Failed &&
                    status.failure == ConnectionFailure.Unauthorized
                ) {
                    expireSession()
                }
            }
        }
        scope.launch {
            realtime.registry
                .map { it.entities.isNotEmpty() || it.areas.isNotEmpty() }
                .distinctUntilChanged()
                .collect { ready -> if (ready) layoutRepository.refresh() }
        }
        scope.launch {
            networkMonitor.changes.collect {
                if (_session.value != null) {
                    ensureBaseUrl()
                    realtime.restart()
                }
            }
        }
    }

    /** Démarre la session mémorisée, s'il y en a une. */
    fun start() {
        if (_session.value == null) return
        scope.launch {
            ensureBaseUrl()
            realtime.start()
            // La mise en page est générée dès que les registres arrivent (collecteur dédié) :
            // on reste en « Chargement » plutôt que d'afficher un écran d'erreur vide.
        }
    }

    fun discoverInstances() = discovery.discover()

    fun clearError() {
        _lastError.value = null
    }

    // ---- Connexion -----------------------------------------------------------

    suspend fun loginWithToken(rawUrl: String, token: String): LoginResult {
        val baseUrl = BaseUrlChooser.normalize(rawUrl)
        if (baseUrl.isBlank()) return LoginResult.Unreachable
        if (!CleartextPolicy.isAllowed(baseUrl)) return LoginResult.CleartextRefused
        return login(HaInstance(baseUrl = baseUrl), token)
    }

    suspend fun login(instance: HaInstance, token: String): LoginResult {
        val baseUrl = BaseUrlChooser.normalize(instance.baseUrl)
        if (baseUrl.isBlank()) return LoginResult.Unreachable
        if (!CleartextPolicy.isAllowed(baseUrl)) return LoginResult.CleartextRefused
        if (http.probe(baseUrl) !is ProbeResult.Reachable) return LoginResult.Unreachable

        return when (val config = http.fetchConfig(baseUrl, token)) {
            is ConfigResult.Ok -> {
                val resolved = instance.copy(
                    baseUrl = baseUrl,
                    internalUrl = config.config.internalUrl?.trimEnd('/') ?: instance.internalUrl,
                    externalUrl = config.config.externalUrl?.trimEnd('/') ?: instance.externalUrl,
                    locationName = config.config.locationName ?: instance.locationName,
                    version = config.config.version ?: instance.version,
                )
                applySession(HaSession(resolved, HaCredentials(accessToken = token)))
                LoginResult.Connected
            }

            ConfigResult.Unauthorized -> LoginResult.InvalidToken
            ConfigResult.Unreachable -> LoginResult.Unreachable
        }
    }

    /** URL d'autorisation OAuth à ouvrir dans un Custom Tab, ou `null` si non configuré. */
    fun startOAuth(instance: HaInstance): String? {
        if (!auth.isConfigured) return null
        val pkce = auth.createPkce()
        pendingOAuth = PendingOAuth(instance, pkce)
        return auth.authorizeUrl(BaseUrlChooser.normalize(instance.baseUrl), pkce)
    }

    suspend fun completeOAuth(redirect: Uri): LoginResult {
        val result = completeOAuthInternal(redirect)
        if (result != LoginResult.Connected) _loginResult.value = result
        return result
    }

    private suspend fun completeOAuthInternal(redirect: Uri): LoginResult {
        val pending = pendingOAuth ?: return LoginResult.Failed(CommandError.Unauthorized)
        pendingOAuth = null

        val code = redirect.getQueryParameter("code")
            ?: return LoginResult.Failed(CommandError.Unauthorized)
        val state = redirect.getQueryParameter("state")
        if (state != null && state != pending.pkce.state) {
            return LoginResult.Failed(CommandError.Unauthorized)
        }

        val baseUrl = BaseUrlChooser.normalize(pending.instance.baseUrl)
        val credentials = auth.exchange(baseUrl, code, pending.pkce)
            .getOrElse { return LoginResult.Failed(CommandError.Unauthorized) }

        val config = http.fetchConfig(baseUrl, credentials.accessToken)
        val instance = if (config is ConfigResult.Ok) {
            pending.instance.copy(
                baseUrl = baseUrl,
                internalUrl = config.config.internalUrl?.trimEnd('/') ?: pending.instance.internalUrl,
                externalUrl = config.config.externalUrl?.trimEnd('/') ?: pending.instance.externalUrl,
                locationName = config.config.locationName ?: pending.instance.locationName,
                version = config.config.version ?: pending.instance.version,
            )
        } else {
            pending.instance.copy(baseUrl = baseUrl)
        }

        applySession(HaSession(instance, credentials))
        return LoginResult.Connected
    }

    fun disconnect() {
        val session = _session.value
        realtime.stop()
        store.clear()
        _session.value = null
        _states.value = emptyMap()
        _status.value = ConnectionStatus.Idle
        pendingOAuth = null
        scope.launch {
            session?.credentials?.refreshToken?.let { refresh ->
                http.revoke(session.instance.baseUrl, refresh)
            }
            layoutRepository.reset()
        }
    }

    /**
     * La session mémorisée est refusée par le serveur : on l'oublie et on propose un nouvel
     * appairage. Sans cela, l'écran restait indéfiniment sur « Chargement de la mise en page ».
     */
    private fun expireSession() {
        val session = _session.value ?: return
        realtime.stop()
        store.clear()
        _session.value = null
        _states.value = emptyMap()
        _status.value = ConnectionStatus.Idle
        pendingOAuth = null
        _loginResult.value = if (session.transport == HaTransport.Addon) {
            LoginResult.SessionExpired
        } else {
            LoginResult.InvalidToken
        }
        scope.launch { layoutRepository.reset() }
    }

    fun reconnect() {
        start()
    }

    fun refreshLayout() {
        scope.launch { layoutRepository.refresh() }
    }

    private suspend fun applySession(session: HaSession) {
        _session.value = session
        store.save(session)
        ensureBaseUrl()
        realtime.start()
    }

    private suspend fun refreshCredentials(force: Boolean): HaCredentials? {
        val session = _session.value ?: return null
        val credentials = session.credentials
        if (!force && !credentials.isExpiring()) return credentials
        val refreshToken = credentials.refreshToken ?: return credentials

        val refreshed = http.refresh(
            baseUrl = session.instance.baseUrl,
            refreshToken = refreshToken,
            clientId = credentials.clientId,
        ).getOrNull() ?: return if (force) null else credentials

        val updated = session.copy(credentials = refreshed)
        _session.value = updated
        store.save(updated)
        return refreshed
    }

    /** Choisit l'URL interne sur le réseau local (si elle répond), sinon l'externe. */
    private suspend fun ensureBaseUrl() {
        val session = _session.value ?: return
        // Avec l'add-on, l'adresse est celle du serveur : aucune bascule interne/externe.
        if (session.transport == HaTransport.Addon) return

        val internal = session.instance.internalUrl?.trimEnd('/')
        val external = session.instance.externalUrl?.trimEnd('/')
        val onLocalNetwork = networkMonitor.isOnLocalNetwork()

        var candidate = BaseUrlChooser.choose(
            internalUrl = internal,
            externalUrl = external,
            fallbackUrl = session.instance.baseUrl,
            onLocalNetwork = onLocalNetwork,
        )

        if (onLocalNetwork && !internal.isNullOrBlank() && candidate == internal && !http.isReachable(internal)) {
            if (!external.isNullOrBlank()) candidate = external
        }

        if (candidate != session.instance.baseUrl) {
            val updated = session.copy(instance = session.instance.withBaseUrl(candidate))
            _session.value = updated
            store.save(updated)
        }
    }

    // ---- HomeGlass Server : appairage ---------------------------------------

    /**
     * Serveurs HomeGlass joignables parmi les instances Home Assistant découvertes.
     *
     * L'add-on tourne sur la machine de Home Assistant : on teste son port d'API sur chaque
     * hôte repéré en mDNS. Aucun scan supplémentaire n'est lancé (Android n'en autorise qu'un
     * à la fois pour un même type de service).
     */
    suspend fun probeServers(instances: List<DiscoveredInstance>): List<DiscoveredServer> =
        instances.mapNotNull { instance ->
            val url = AddonCandidates.forInstance(instance) ?: return@mapNotNull null
            http.probeAddon(url)?.let { info ->
                DiscoveredServer(baseUrl = url, version = info.version, haUuid = info.haUuid)
            }
        }.distinctBy { it.baseUrl }

    /**
     * Annonce l'application à l'add-on : celui-ci affiche un code dans Home Assistant.
     *
     * Le code n'est jamais renvoyé par le serveur : c'est ce qui rend l'appairage sûr, il faut
     * le lire dans Home Assistant (donc être déjà autorisé sur l'instance).
     */
    suspend fun startPairing(
        baseUrl: String,
        deviceName: String,
        model: String,
    ): PairingStartResult {
        val normalized = BaseUrlChooser.normalize(baseUrl)
        if (normalized.isBlank() || !CleartextPolicy.isAllowed(normalized)) {
            return PairingStartResult.Unreachable
        }
        return http.requestPairing(normalized, deviceName, model, appVersion)
    }

    /** Échange le code contre un jeton d'appareil, puis ouvre la session via l'add-on. */
    suspend fun claimPairing(baseUrl: String, pairingId: String, code: String): LoginResult {
        val normalized = BaseUrlChooser.normalize(baseUrl)
        if (!CleartextPolicy.isAllowed(normalized)) return LoginResult.CleartextRefused

        val codeDigits = PairingProtocol.normalizeCode(code)
        return when (val result = http.claimPairing(normalized, pairingId, codeDigits)) {
            PairingClaimResult.WrongCode -> LoginResult.CodeWrong
            PairingClaimResult.Expired -> LoginResult.CodeExpired
            PairingClaimResult.TooManyAttempts -> LoginResult.TooManyAttempts
            PairingClaimResult.Unreachable -> LoginResult.Unreachable
            is PairingClaimResult.Ok -> {
                val claim = result.claim
                val config = http.fetchConfig(normalized, claim.token, HaTransport.Addon)
                val resolved = config as? ConfigResult.Ok
                applySession(
                    HaSession(
                        instance = HaInstance(
                            baseUrl = normalized,
                            locationName = resolved?.config?.locationName ?: claim.deviceName,
                            version = resolved?.config?.version,
                        ),
                        credentials = HaCredentials(
                            accessToken = claim.token,
                            authProvider = "pairing",
                        ),
                        transport = HaTransport.Addon,
                    ),
                )
                LoginResult.Connected
            }
        }
    }

    // ---- Commandes -----------------------------------------------------------

    /** Bascule une entité ; renvoie `false` si le domaine n'a pas de bascule. */
    fun toggle(entityId: String): Boolean {
        val state = _states.value[entityId] ?: return false
        val call = TileCommands.toggle(state) ?: return false

        val optimistic = state.copyWithState(optimisticStateFor(state))
        _states.value = _states.value + (entityId to optimistic)

        execute(call) {
            // Retour à l'état réel : la prochaine trame du serveur fera foi.
            _states.value = _states.value + (entityId to state)
        }
        return true
    }

    fun callService(service: String, data: Map<String, JsonElement>? = null, target: Map<String, JsonElement>? = null) {
        val call = TileCommands.fromLayout(service, data, target) ?: return
        execute(call)
    }

    fun setBrightness(entityId: String, percent: Int, finished: Boolean = true) {
        setLevel(entityId, percent, cover = false, finished = finished)
    }

    /** Panneau de contrôle : allumage ou extinction explicite de l'appareil. */
    fun setPower(entityId: String, on: Boolean) {
        val call = TileCommands.power(entityId, on) ?: return
        execute(call)
    }

    /** Panneau de contrôle : teinte RGB d'une lumière. */
    fun setLightColor(entityId: String, red: Int, green: Int, blue: Int) {
        execute(TileCommands.lightColor(entityId, red, green, blue))
    }

    /** Panneau de contrôle : température de couleur en kelvins. */
    fun setColorTemperature(entityId: String, kelvin: Int) {
        execute(TileCommands.lightColorTemperature(entityId, kelvin))
    }

    /** Enceinte : lecture ou pause, sans couper l'alimentation de l'appareil. */
    fun mediaPlayPause(entityId: String) {
        execute(TileCommands.mediaPlayPause(entityId))
    }

    fun mediaNext(entityId: String) {
        execute(TileCommands.mediaNext(entityId))
    }

    fun mediaPrevious(entityId: String) {
        execute(TileCommands.mediaPrevious(entityId))
    }

    /** Panneau de contrôle : volume d'une enceinte, 0,0 à 1,0. */
    fun setVolume(entityId: String, level: Float) {
        execute(TileCommands.mediaVolume(entityId, level.toDouble()))
    }

    fun setCoverPosition(entityId: String, percent: Int, finished: Boolean = true) {
        setLevel(entityId, percent, cover = true, finished = finished)
    }

    private fun setLevel(entityId: String, percent: Int, cover: Boolean, finished: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val last = lastLevelSentAt[entityId] ?: 0L

        fun send(value: Int) {
            lastLevelSentAt[entityId] = SystemClock.elapsedRealtime()
            execute(if (cover) TileCommands.coverPosition(entityId, value) else TileCommands.brightness(entityId, value))
        }

        if (finished) {
            levelJobs.remove(entityId)?.cancel()
            pendingLevel.remove(entityId)
            send(percent)
            return
        }

        if (now - last >= LEVEL_THROTTLE_MILLIS) {
            pendingLevel.remove(entityId)
            send(percent)
        } else {
            pendingLevel[entityId] = percent
            if (levelJobs[entityId] == null) {
                levelJobs[entityId] = scope.launch {
                    delay(LEVEL_THROTTLE_MILLIS)
                    levelJobs.remove(entityId)
                    pendingLevel.remove(entityId)?.let { value -> send(value) }
                }
            }
        }
    }

    private val lastLevelSentAt = mutableMapOf<String, Long>()
    private val pendingLevel = mutableMapOf<String, Int>()
    private val levelJobs = mutableMapOf<String, Job>()

    private fun execute(call: ServiceCall, onFailure: () -> Unit = {}) {
        val session = _session.value ?: return
        scope.launch {
            val result = http.callService(
                session.instance.baseUrl,
                session.credentials.accessToken,
                call,
                session.transport,
            )
            if (result.isSuccess) return@launch

            val error = result.exceptionOrNull()
            if (error is HaHttpException && error.code == 401) {
                val refreshed = refreshCredentials(force = true)
                if (refreshed == null) {
                    _lastError.value = CommandError.Unauthorized
                    onFailure()
                    return@launch
                }
                val retry = http.callService(
                    session.instance.baseUrl,
                    refreshed.accessToken,
                    call,
                    session.transport,
                )
                if (retry.isFailure) {
                    _lastError.value = CommandError.Server
                    onFailure()
                }
                return@launch
            }

            _lastError.value = when (error) {
                is HaHttpException -> CommandError.Server
                is IOException -> CommandError.Network
                else -> CommandError.Server
            }
            onFailure()
        }
    }

    private fun optimisticStateFor(state: EntityState): String = when (state.domain) {
        "cover" -> if (state.state == "open") "closed" else "open"
        "lock" -> if (state.state == "locked") "unlocked" else "locked"
        "vacuum" -> if (state.state == "cleaning") "docked" else "cleaning"
        "scene", "script" -> state.state
        else -> if (state.state == "on") "off" else "on"
    }

    private companion object {
        /** ~150 ms : fluide au glissement, sans noyer Home Assistant. */
        const val LEVEL_THROTTLE_MILLIS = 150L
    }
}
