package com.homeglass.app.domain.ha

/** Cause d'un échec de connexion, traduite en message par l'interface. */
enum class ConnectionFailure {
    /** Aucun réseau disponible, ou l'instance ne répond pas. */
    Unreachable,

    /** Jeton invalide ou expiré, et pas de rafraîchissement possible. */
    Unauthorized,

    /** Le serveur a répondu, mais la poignée de main WebSocket a échoué. */
    Protocol,

    Unknown,
}

/**
 * État de la liaison temps réel, exposé en Flow à l'interface.
 * `Connecting` / `Reconnecting` sont « orange », `Connected` « vert », `Failed` « rouge ».
 */
sealed interface ConnectionStatus {
    /** Aucune session : rien à connecter. */
    data object Idle : ConnectionStatus

    data class Connecting(val attempt: Int = 0) : ConnectionStatus

    data object Connected : ConnectionStatus

    /** Coupure : nouvelle tentative planifiée dans [nextRetryMillis]. */
    data class Reconnecting(val attempt: Int, val nextRetryMillis: Long) : ConnectionStatus

    data class Failed(val failure: ConnectionFailure) : ConnectionStatus
}
