package com.homeglass.app.data.layout

import com.homeglass.app.domain.layout.LayoutIssue
import com.homeglass.app.domain.layout.LayoutParseResult
import com.homeglass.app.domain.layout.LayoutParser
import com.homeglass.app.domain.layout.LayoutState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cache du JSON brut de la mise en page. */
interface LayoutCache {
    suspend fun read(): String?
    suspend fun write(raw: String)
    suspend fun clear()
}

/** Source de JSON de mise en page : fichier embarqué, cache local ou API de l'add-on. */
interface LayoutSource {
    suspend fun load(): String?
}

/**
 * Dépôt de la mise en page.
 *
 * Le JSON brut est la seule vérité conservée en cache (aucun modèle binaire) : un document
 * relu après mise à jour de l'app est réanalysé avec le parseur courant.
 *
 * Ordre de chargement :
 * 1. le cache est analysé en premier et affiché s'il est exploitable → [LayoutState.Stale]
 *    (l'écran n'est jamais vide pendant le rafraîchissement) ;
 * 2. la source distante (API, étape 4), sinon la source locale (assets) fournit un document ;
 * 3. document exploitable → cache mis à jour + [LayoutState.Ready] ;
 * 4. version trop récente → cache conservé + [LayoutIssue.UpdateApp] ;
 * 5. document corrompu → cache conservé si possible, sinon [LayoutState.Error].
 */
class LayoutRepository(
    private val cache: LayoutCache,
    private val local: LayoutSource,
    private val remote: LayoutSource? = null,
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<LayoutState>(LayoutState.Loading)
    val state: StateFlow<LayoutState> = _state.asStateFlow()

    suspend fun refresh(): Unit = mutex.withLock {
        val cached = cache.read()?.let(LayoutParser::parse)

        // Pendant un rafraîchissement lent, on affiche tout de suite ce que le cache contient.
        if (_state.value is LayoutState.Loading && cached is LayoutParseResult.Supported) {
            _state.value = LayoutState.Stale(cached.layout, LayoutIssue.Cached)
        }

        // Copie locale : la source distante n'est pas encore branchée (étape 4).
        val remoteSource = remote
        var remoteFailed = false
        val remoteRaw = if (remoteSource != null) {
            runCatching { remoteSource.load() }.onFailure { remoteFailed = true }.getOrNull()
        } else {
            null
        }
        val fresh = remoteRaw ?: runCatching { local.load() }.getOrNull()

        if (fresh == null) {
            _state.value = when {
                cached is LayoutParseResult.Supported ->
                    LayoutState.Stale(
                        cached.layout,
                        if (remoteFailed) LayoutIssue.Offline else LayoutIssue.NoData,
                    )

                else -> LayoutState.Error(if (remoteFailed) LayoutIssue.Offline else LayoutIssue.NoData)
            }
            return@withLock
        }

        when (val result = LayoutParser.parse(fresh)) {
            is LayoutParseResult.Supported -> {
                cache.write(fresh)
                _state.value = LayoutState.Ready(result.layout)
            }

            is LayoutParseResult.TooNew -> {
                _state.value = cached.asStaleOrError(LayoutIssue.UpdateApp)
            }

            is LayoutParseResult.Invalid -> {
                _state.value = cached.asStaleOrError(LayoutIssue.CorruptedLayout)
            }
        }
    }

    /** Repart en `Loading` : utilisé à la déconnexion, avant d'oublier la session. */
    suspend fun reset(): Unit = mutex.withLock {
        _state.value = LayoutState.Loading
    }

    private fun LayoutParseResult?.asStaleOrError(issue: LayoutIssue): LayoutState =
        when (this) {
            is LayoutParseResult.Supported -> LayoutState.Stale(layout, issue)
            else -> LayoutState.Error(issue)
        }
}
