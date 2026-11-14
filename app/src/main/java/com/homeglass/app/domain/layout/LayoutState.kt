package com.homeglass.app.domain.layout

/** Raison d'un état dégradé, traduite en message par l'interface. */
enum class LayoutIssue {
    /** Document affiché depuis le cache pendant qu'un rafraîchissement est en cours. */
    Cached,
    /** La source distante est configurée mais injoignable : le cache prend le relais. */
    Offline,

    /** `schemaVersion` reçue supérieure à ce que l'app gère. */
    UpdateApp,

    /** Document illisible ou structure invalide. */
    CorruptedLayout,

    /** Aucune donnée, ni fraîche ni en cache. */
    NoData,
}

/**
 * État de la mise en page exposé à l'interface.
 *
 * [Stale] conserve toujours un document affichable : c'est le cas « la nouvelle version
 * n'est pas exploitable, on garde ce qu'on avait » (hors ligne, mise à jour requise,
 * document corrompu).
 */
sealed interface LayoutState {
    data object Loading : LayoutState

    data class Ready(val layout: Layout) : LayoutState

    data class Stale(val layout: Layout, val issue: LayoutIssue) : LayoutState

    data class Error(val issue: LayoutIssue) : LayoutState
}
