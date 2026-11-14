package com.homeglass.app.data.ha

import com.homeglass.app.domain.ha.ConnectionFailure
import com.homeglass.app.domain.ha.ConnectionStatus
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.ExponentialBackoff
import com.homeglass.app.domain.ha.HaCredentials
import com.homeglass.app.domain.ha.HaMessage
import com.homeglass.app.domain.ha.HaProtocol
import com.homeglass.app.domain.ha.HaRegistry
import com.homeglass.app.domain.ha.HaTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Liaison temps réel avec Home Assistant.
 *
 * Une seule boucle : se connecte, s'authentifie, charge les états et les registres, puis
 * consomme les événements `state_changed`. Coupure → reconnexion avec backoff exponentiel,
 * abonnements rejoués à chaque connexion. Le flux [status] est exposé tel quel à l'UI.
 */
class HaRealtime(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val baseUrlProvider: () -> String?,
    private val credentialsProvider: suspend (forceRefresh: Boolean) -> HaCredentials?,
    private val transportProvider: () -> HaTransport = { HaTransport.Direct },
) {

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()

    private val _registry = MutableStateFlow(HaRegistry())
    val registry: StateFlow<HaRegistry> = _registry.asStateFlow()

    private var loop: Job? = null

    @Synchronized
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch { runLoop() }
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
        _status.value = ConnectionStatus.Idle
    }

    @Synchronized
    fun restart() {
        stop()
        start()
    }

    private suspend fun runLoop() {
        val backoff = ExponentialBackoff()
        var forceRefresh = false

        while (currentCoroutineContext().isActive) {
            val baseUrl = baseUrlProvider()
            if (baseUrl.isNullOrBlank()) {
                _status.value = ConnectionStatus.Idle
                return
            }

            _status.value = ConnectionStatus.Connecting(backoff.attempt)
            val credentials = credentialsProvider(forceRefresh)
            if (credentials == null) {
                _status.value = ConnectionStatus.Failed(ConnectionFailure.Unauthorized)
                return
            }

            val outcome = runConnection(baseUrl, credentials) {
                backoff.reset()
                _status.value = ConnectionStatus.Connected
            }

            when (outcome) {
                SessionOutcome.InvalidToken -> {
                    if (!forceRefresh) {
                        // Un rafraîchissement du jeton peut suffire : on retente une fois.
                        // Le drapeau n'est remis à zéro qu'après une issue différente, sinon
                        // un jeton définitivement refusé relançait la tentative sans fin.
                        forceRefresh = true
                        continue
                    }
                    _status.value = ConnectionStatus.Failed(ConnectionFailure.Unauthorized)
                    return
                }

                else -> forceRefresh = false
            }

            if (!currentCoroutineContext().isActive) return
            val delayMillis = backoff.nextDelayMillis()
            _status.value = ConnectionStatus.Reconnecting(backoff.attempt, delayMillis)
            delay(delayMillis)
        }
    }

    private suspend fun runConnection(
        baseUrl: String,
        credentials: HaCredentials,
        onAuthorized: () -> Unit,
    ): SessionOutcome {
        val url = webSocketUrl(baseUrl) ?: return SessionOutcome.Error(ConnectionFailure.Unreachable)
        val events = Channel<SocketEvent>(Channel.UNLIMITED)
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            listenerFor(events),
        )

        try {
            val authStep = withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) {
                awaitAuth(events, socket, credentials.accessToken)
            } ?: return SessionOutcome.Error(ConnectionFailure.Unreachable)

            when (authStep) {
                AuthStep.Invalid -> return SessionOutcome.InvalidToken
                AuthStep.Cancelled -> return SessionOutcome.Closed
                AuthStep.Failed -> return SessionOutcome.Error(ConnectionFailure.Unreachable)
                AuthStep.Ok -> Unit
            }
            onAuthorized()

            socket.send(HaProtocol.getStates(ID_STATES))
            socket.send(HaProtocol.subscribeEvents(ID_SUBSCRIPTION))
            socket.send(HaProtocol.listAreas(ID_AREAS))
            socket.send(HaProtocol.listDevices(ID_DEVICES))
            socket.send(HaProtocol.listEntities(ID_ENTITIES))

            return consume(events)
        } finally {
            runCatching { socket.close(1000, null) }
            events.close()
        }
    }

    private suspend fun awaitAuth(
        events: Channel<SocketEvent>,
        socket: WebSocket,
        accessToken: String,
    ): AuthStep {
        while (true) {
            when (val event = events.receive()) {
                is SocketEvent.Text -> when (val message = HaProtocol.parse(event.payload)) {
                    is HaMessage.AuthRequired -> socket.send(
                        HaProtocol.auth(
                            accessToken = accessToken,
                            // L'add-on renseigne son jeton dans `token` : on l'envoie aussi pour
                            // rester compatible avec les versions déployées qui l'exigent.
                            includeLegacyToken = transportProvider() == HaTransport.Addon,
                        ),
                    )
                    HaMessage.AuthOk -> return AuthStep.Ok
                    is HaMessage.AuthInvalid -> return AuthStep.Invalid
                    else -> Unit
                }

                is SocketEvent.Closed -> return AuthStep.Cancelled
                is SocketEvent.Failure -> return AuthStep.Failed
                SocketEvent.Open -> Unit
            }
        }
    }

    private suspend fun consume(events: Channel<SocketEvent>): SessionOutcome {
        while (true) {
            when (val event = events.receive()) {
                is SocketEvent.Text -> handleMessage(event.payload)
                is SocketEvent.Closed -> return SessionOutcome.Closed
                is SocketEvent.Failure -> return SessionOutcome.Error(ConnectionFailure.Unreachable)
                SocketEvent.Open -> Unit
            }
        }
    }

    private fun handleMessage(raw: String) {
        when (val message = HaProtocol.parse(raw)) {
            is HaMessage.Result -> if (message.success) applyResult(message)
            is HaMessage.Event -> {
                val newState = HaProtocol.newStateFromEvent(message)
                if (newState != null) {
                    _states.value = _states.value + (newState.entityId to newState)
                } else {
                    // Entité supprimée : on la retire pour ne pas afficher une tuile morte.
                    val entityId = HaProtocol.entityIdFromEvent(message) ?: return
                    _states.value = _states.value - entityId
                }
            }

            else -> Unit
        }
    }

    private fun applyResult(message: HaMessage.Result) {
        val result = message.result ?: return
        when (message.id) {
            ID_STATES -> _states.value = EntityState.listFromJson(result).associateBy { it.entityId }
            ID_AREAS -> _registry.value = _registry.value.copy(areas = HaRegistry.areasFromJson(result))
            ID_DEVICES -> _registry.value = _registry.value.copy(devices = HaRegistry.devicesFromJson(result))
            ID_ENTITIES -> _registry.value = _registry.value.copy(entities = HaRegistry.entitiesFromJson(result))
        }
    }

    private fun webSocketUrl(baseUrl: String): String? {
        val path =
            if (transportProvider() == HaTransport.Addon) "/api/ha/ws" else "/api/websocket"
        // OkHttp bascule lui-même en WebSocket : l'URL doit rester en http(s), un schéma « ws »
        // serait rejeté par HttpUrl.
        return "${baseUrl.trimEnd('/')}$path".toHttpUrlOrNull()?.toString()
    }

    private fun listenerFor(events: Channel<SocketEvent>): WebSocketListener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                events.trySend(SocketEvent.Open)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                events.trySend(SocketEvent.Text(text))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(SocketEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.trySend(SocketEvent.Failure(t))
            }
        }

    private sealed interface SocketEvent {
        data object Open : SocketEvent
        data class Text(val payload: String) : SocketEvent
        data class Closed(val code: Int, val reason: String) : SocketEvent
        data class Failure(val error: Throwable) : SocketEvent
    }

    private enum class AuthStep { Ok, Invalid, Failed, Cancelled }

    private sealed interface SessionOutcome {
        data object Closed : SessionOutcome
        data object InvalidToken : SessionOutcome
        data class Error(val failure: ConnectionFailure) : SessionOutcome
    }

    private companion object {
        const val AUTH_TIMEOUT_MILLIS = 20_000L
        const val ID_STATES = 1
        const val ID_SUBSCRIPTION = 2
        const val ID_AREAS = 3
        const val ID_DEVICES = 4
        const val ID_ENTITIES = 5
    }
}
