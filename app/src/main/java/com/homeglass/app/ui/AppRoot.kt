package com.homeglass.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.data.custom.CustomDashboardStore
import com.homeglass.app.data.ha.CommandError
import com.homeglass.app.data.ha.HaController
import com.homeglass.app.data.ha.LoginResult
import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.domain.ha.ConnectionStatus
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.HaSession
import com.homeglass.app.domain.layout.Layout
import com.homeglass.app.domain.layout.LayoutState
import com.homeglass.app.domain.layout.TabType
import com.homeglass.app.ui.components.GlassBackground
import com.homeglass.app.ui.components.GlassHazeHost
import com.homeglass.app.ui.components.KeepScreenOn
import com.homeglass.app.ui.components.LockLandscape
import com.homeglass.app.ui.components.glassHazeSource
import com.homeglass.app.ui.custom.CustomDashboardScreen
import com.homeglass.app.ui.detail.MoreInfoSheet
import com.homeglass.app.ui.home.HomeScreen
import com.homeglass.app.ui.home.HomeSectionKind
import com.homeglass.app.ui.home.RoomUi
import com.homeglass.app.ui.home.SceneCardUi
import com.homeglass.app.ui.home.allEntityTiles
import com.homeglass.app.ui.home.toHomeUiWithStates
import com.homeglass.app.ui.home.toRoomsUi
import com.homeglass.app.ui.home.toThemeConfig
import com.homeglass.app.ui.home.withCustomLayout
import com.homeglass.app.ui.media.NowPlayingScreen
import com.homeglass.app.ui.media.startedPlayback
import com.homeglass.app.ui.media.staysOnScreen
import com.homeglass.app.ui.navigation.GlassBottomNavBar
import com.homeglass.app.ui.navigation.NavTabUi
import com.homeglass.app.ui.rooms.RoomsScreen
import com.homeglass.app.ui.settings.SettingsScreen
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.theme.HomeGlassTheme
import com.homeglass.app.ui.theme.ThemeConfig
import com.homeglass.app.ui.tiles.LocalMediaArtworkBaseUrl
import com.homeglass.app.ui.tiles.LocalMediaControls
import com.homeglass.app.ui.tiles.MediaControlHandlers
import com.homeglass.app.ui.tiles.TileAction
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileUiModel
import com.homeglass.app.ui.welcome.WelcomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Racine de l'application : thème issu du layout, écran de bienvenue ou coquille principale.
 *
 * La coquille garde **une** couche de flou (Haze) pour le fond, la barre de navigation et le
 * panneau d'info : le rendu du fond n'est calculé qu'une fois.
 */
@Composable
fun HomeGlassApp(
    controller: HaController,
    redirect: Uri?,
    onRedirectConsumed: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val session by controller.session.collectAsState()
    val status by controller.status.collectAsState()
    val layoutState by controller.layoutRepository.state.collectAsState()
    val states by controller.states.collectAsState()
    val commandError by controller.lastError.collectAsState()
    val loginResult by controller.loginResult.collectAsState()

    val layout = when (val current = layoutState) {
        is LayoutState.Ready -> current.layout
        is LayoutState.Stale -> current.layout
        else -> null
    }
    val themeConfig = remember(layout) { layout?.theme?.toThemeConfig() ?: ThemeConfig() }

    val context = LocalContext.current
    val customStore = remember { CustomDashboardStore(context.applicationContext) }
    val customDashboard by customStore.dashboard.collectAsState(initial = CustomDashboard())
    var showCustomizer by remember { mutableStateOf(false) }
    // Instantané pris à l'ouverture de l'éditeur : les mises à jour d'état Home Assistant
    // ne doivent pas réinitialiser le canevas en cours de modification.
    var editorSeed by remember { mutableStateOf<List<TileUiModel>>(emptyList()) }
    val customScope = rememberCoroutineScope()

    // Pochette d'album : Home Assistant la publie sous forme de chemin relatif. L'URL interne
    // est prioritaire, car l'add-on ne sert pas les images des entités.
    val artworkBaseUrl = session?.instance?.let { instance ->
        instance.internalUrl?.takeIf(String::isNotBlank) ?: instance.baseUrl
    }

    // Modèle de l'accueil tel qu'affiché, et appareils proposés par l'éditeur : les tuiles
    // d'entités de l'accueil et des pièces. Une entité n'apparaît qu'une fois dans la palette.
    val roomsUi = layout?.toRoomsUi(states).orEmpty()
    val roomTiles = roomsUi.flatMap { room -> room.sections }
        .filter { section -> section.kind == HomeSectionKind.Tiles }
        .flatMap { section -> section.tiles }
    val baseHome = layout?.toHomeUiWithStates(states)
    val homeModel = baseHome?.withCustomLayout(customDashboard, roomTiles)
    val editorTiles = homeModel?.sections.orEmpty()
        .filter { section -> section.kind == HomeSectionKind.Tiles }
        .flatMap { section -> section.tiles }
    // Noms d'origine Home Assistant : la palette ne doit pas contenir les renommages, sinon
    // l'éditeur croit que le nom affiché est celui d'origine et efface l'étiquette au
    // enregistrement (les pseudos disparaissaient).
    val availableTiles = (baseHome?.allEntityTiles().orEmpty() + roomTiles)
        .distinctBy { tile -> tile.entityId ?: tile.id }

    LaunchedEffect(redirect) {
        if (redirect != null) {
            controller.completeOAuth(redirect)
            onRedirectConsumed()
        }
    }

    HomeGlassTheme(config = themeConfig) {
        CompositionLocalProvider(
            LocalMediaArtworkBaseUrl provides artworkBaseUrl,
            LocalMediaControls provides MediaControlHandlers(
                playPause = controller::mediaPlayPause,
                next = controller::mediaNext,
                previous = controller::mediaPrevious,
            ),
        ) {
            val currentSession = session
            if (currentSession == null) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackground()
                    WelcomeScreen(
                        controller = controller,
                        message = loginMessage(loginResult),
                        onOpenUrl = onOpenUrl,
                    )
                }
            } else {
                MainShell(
                    controller = controller,
                    session = currentSession,
                    layout = layout,
                    layoutState = layoutState,
                    status = status,
                    states = states,
                    commandError = commandError,
                    onDismissError = controller::clearError,
                    customDashboard = customDashboard,
                    onCustomize = {
                        // Sans layout chargé, l'éditeur n'aurait rien à montrer : on évite
                        // d'ouvrir un canevas vide qu'un enregistrement viderait le tableau.
                        if (editorTiles.isNotEmpty()) {
                            editorSeed = editorTiles
                            showCustomizer = true
                        }
                    },
                )

                if (showCustomizer) {
                    CustomDashboardScreen(
                        initialTiles = editorSeed,
                        availableTiles = availableTiles,
                        onSave = { tiles ->
                            customScope.launch { customStore.save(CustomDashboard(tiles = tiles)) }
                            showCustomizer = false
                        },
                        onDismiss = { showCustomizer = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    controller: HaController,
    session: HaSession,
    layout: Layout?,
    layoutState: LayoutState,
    status: ConnectionStatus,
    states: Map<String, EntityState>,
    commandError: CommandError?,
    onDismissError: () -> Unit,
    customDashboard: CustomDashboard,
    onCustomize: () -> Unit,
) {
    val rooms: List<RoomUi> = layout?.toRoomsUi(states).orEmpty()
    // L'éditeur propose toutes les entités : les tuiles des pièces complètent celles de
    // l'accueil pour qu'une entité choisie trouve toujours son modèle d'affichage.
    val roomTiles = rooms
        .flatMap { room -> room.sections }
        .filter { section -> section.kind == HomeSectionKind.Tiles }
        .flatMap { section -> section.tiles }
    val home = layout?.toHomeUiWithStates(states)?.withCustomLayout(customDashboard, roomTiles)
    val tabs: List<NavTabUi> = home?.tabs.orEmpty()

    var selectedTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoEntityId by remember { mutableStateOf<String?>(null) }
    var infoTitle by remember { mutableStateOf("") }

    // Lecteur plein écran : ouvert tout seul quand une lecture démarre, ou par un appui long
    // sur une tuile d'enceinte.
    var nowPlayingId by remember { mutableStateOf<String?>(null) }

    // Vrai quand l'utilisateur a ouvert le lecteur lui-même : l'écran reste alors affiché
    // même si l'appareil est arrêté, jusqu'à ce qu'il le referme.
    var nowPlayingManual by remember { mutableStateOf(false) }

    // Relevé précédent des états : sert uniquement à repérer le début d'une lecture.
    var previousStates by remember { mutableStateOf<Map<String, EntityState>?>(null) }

    val selectedTab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.firstOrNull()

    val openInfo: (TileUiModel) -> Unit = { tile ->
        val entityId = tile.entityId
        if (entityId != null) {
            infoEntityId = entityId
            infoTitle = tile.name
        } else {
            infoTitle = tile.name
        }
    }

    val onTileTap: (TileUiModel) -> Unit = { tile ->
        when (val action = tile.tap) {
            TileAction.Toggle -> {
                // Une enceinte ne s'éteint pas quand on la touche : la tuile met en pause.
                if (tile.category == TileCategory.Media) {
                    controller.mediaPlayPause(tile.entityId.orEmpty())
                } else if (!controller.toggle(tile.entityId.orEmpty())) {
                    openInfo(tile)
                }
            }

            TileAction.MoreInfo, TileAction.None -> openInfo(tile)
            is TileAction.CallService -> controller.callService(action.service, action.data, action.target)
            is TileAction.Navigate -> selectedTabId = tabs.firstOrNull { it.type == TabType.Rooms }?.id
        }
    }

    val onTileLongPress: (TileUiModel) -> Unit = { tile ->
        val action = tile.longPress
        val entityId = tile.entityId
        if (action is TileAction.CallService) {
            controller.callService(action.service, action.data, action.target)
        } else if (tile.category == TileCategory.Media && entityId != null) {
            nowPlayingManual = true
            nowPlayingId = entityId
        } else {
            openInfo(tile)
        }
    }

    val onTileLevelChange: (TileUiModel, Float) -> Unit = { tile, level ->
        val entityId = tile.entityId
        if (entityId != null) {
            val percent = (level * 100f).toInt().coerceIn(0, 100)
            // Le contrôleur limite le débit (~150 ms) et envoie toujours la dernière valeur.
            if (tile.category == TileCategory.Cover) {
                controller.setCoverPosition(entityId, percent, finished = false)
            } else {
                controller.setBrightness(entityId, percent, finished = false)
            }
        }
    }

    val onSceneTap: (SceneCardUi) -> Unit = { scene ->
        val action = scene.action
        if (action is TileAction.CallService) {
            controller.callService(action.service, action.data, action.target)
        }
    }

    // Un appareil qui commence à jouer ouvre le lecteur. Le tout premier relevé sert
    // seulement de référence : au lancement de l'application, de la musique déjà en cours
    // n'ouvre rien.
    LaunchedEffect(states) {
        val previous = previousStates
        previousStates = states
        startedPlayback(states, previous)?.let { started ->
            nowPlayingManual = false
            nowPlayingId = started
        }
    }

    // L'écran se referme quand la lecture s'arrête, après un court délai : au démarrage d'une
    // piste, certains appareils passent une seconde par « idle » ou disparaissent le temps
    // d'un relevé, et l'écran ne doit pas clignoter pour autant. Une ouverture manuelle reste
    // affichée jusqu'à ce que l'utilisateur la ferme.
    val latestStates by rememberUpdatedState(states)
    LaunchedEffect(nowPlayingId, nowPlayingManual, nowPlayingId?.let { id -> states[id]?.state }) {
        if (nowPlayingManual) return@LaunchedEffect
        val current = nowPlayingId ?: return@LaunchedEffect
        if (staysOnScreen(latestStates[current])) return@LaunchedEffect

        delay(2_500)
        if (!staysOnScreen(nowPlayingId?.let { id -> latestStates[id] })) nowPlayingId = null
    }

    LaunchedEffect(commandError) {
        if (commandError != null) {
            delay(2_500)
            onDismissError()
        }
    }

    // Retour Android : referme le lecteur au lieu de quitter l'application.
    BackHandler(enabled = nowPlayingId != null) { nowPlayingId = null }

    val nowPlaying = nowPlayingId?.let { states[it] }
    val infoState = infoEntityId?.let { states[it] }

    // La tablette reste allumée tant qu'une enceinte joue, et le mode vinyle passe en paysage.
    KeepScreenOn(enabled = states.values.any { it.state == "playing" })
    LockLandscape(enabled = nowPlaying != null)

    GlassHazeHost {
        Box(Modifier.fillMaxSize()) {
            if (nowPlaying != null) {
                // Le lecteur remplace l'interface : aucun bouton de personnalisation n'est
                // composé dessous, donc rien ne peut être touché par erreur.
                NowPlayingScreen(
                    state = nowPlaying,
                    title = nowPlaying.friendlyName ?: nowPlaying.entityId,
                    onDismiss = { nowPlayingId = null },
                    onPlayPause = { controller.mediaPlayPause(nowPlaying.entityId) },
                    onNext = { controller.mediaNext(nowPlaying.entityId) },
                    onPrevious = { controller.mediaPrevious(nowPlaying.entityId) },
                    onVolume = { level -> controller.setVolume(nowPlaying.entityId, level) },
                )
            } else {
                Box(Modifier.fillMaxSize().glassHazeSource()) {
                    GlassBackground()

                    when (selectedTab?.type) {
                        TabType.Rooms -> RoomsScreen(
                            rooms = rooms,
                            onTileTap = onTileTap,
                            onTileLongPress = onTileLongPress,
                            onTileLevelChange = onTileLevelChange,
                            onSceneTap = onSceneTap,
                        )

                        TabType.Settings -> SettingsScreen(
                            session = session,
                            status = status,
                            onDisconnect = controller::disconnect,
                            onReconnect = controller::reconnect,
                            onCustomize = onCustomize,
                        )

                        else -> HomeScreen(
                            home = home,
                            state = layoutState,
                            onRetry = controller::refreshLayout,
                            onTileTap = onTileTap,
                            onTileLongPress = onTileLongPress,
                            onTileLevelChange = onTileLevelChange,
                            onSceneTap = onSceneTap,
                            onCustomize = onCustomize,
                        )
                    }
                }

                if (tabs.isNotEmpty()) {
                    GlassBottomNavBar(
                        tabs = tabs,
                        selectedId = selectedTab?.id.orEmpty(),
                        onSelect = { tab -> selectedTabId = tab.id },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (infoState != null) {
                    MoreInfoSheet(
                        state = infoState,
                        title = infoTitle.ifBlank { infoState.friendlyName ?: infoState.entityId },
                        onDismiss = { infoEntityId = null },
                        onToggle = {
                            controller.setPower(infoState.entityId, infoState.state != "on")
                        },
                        onBrightness = { percent, finished ->
                            controller.setBrightness(infoState.entityId, percent, finished)
                        },
                        onColor = { red, green, blue ->
                            controller.setLightColor(infoState.entityId, red, green, blue)
                        },
                        onColorTemperature = { kelvin ->
                            controller.setColorTemperature(infoState.entityId, kelvin)
                        },
                        onMediaPlayPause = { controller.mediaPlayPause(infoState.entityId) },
                        onMediaNext = { controller.mediaNext(infoState.entityId) },
                        onMediaPrevious = { controller.mediaPrevious(infoState.entityId) },
                        onVolume = { level -> controller.setVolume(infoState.entityId, level) },
                    )
                }
            }

            if (commandError != null) {
                CommandErrorToast(
                    error = commandError,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun CommandErrorToast(error: CommandError, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val message = stringResource(
        when (error) {
            CommandError.Network -> R.string.error_network
            CommandError.Unauthorized -> R.string.error_unauthorized
            CommandError.Server -> R.string.error_command
            CommandError.EntityMissing -> R.string.error_entity_missing
        },
    )

    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
            .clip(shapes.pill)
            .background(colors.chipBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = GlassTheme.typography.caption, color = colors.screenTextPrimary)
    }
}

@Composable
private fun loginMessage(result: LoginResult?): String? = when (result) {
    null, LoginResult.Connected -> null
    LoginResult.InvalidToken -> stringResource(R.string.auth_token_invalid)
    LoginResult.Unreachable -> stringResource(R.string.auth_error_unreachable)
    LoginResult.CleartextRefused -> stringResource(R.string.welcome_manual_cleartext)
    LoginResult.OAuthUnavailable -> stringResource(R.string.auth_oauth_unavailable)
    LoginResult.CodeWrong -> stringResource(R.string.pair_code_invalid)
    LoginResult.CodeExpired -> stringResource(R.string.pair_code_expired)
    LoginResult.TooManyAttempts -> stringResource(R.string.pair_code_too_many)
    LoginResult.SessionExpired -> stringResource(R.string.auth_session_expired)
    LoginResult.NotAddonServer -> stringResource(R.string.pair_not_server)
    is LoginResult.Failed -> stringResource(R.string.error_command)
}
