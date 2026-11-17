package com.homeglass.app.ui.web

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.homeglass.app.R
import com.homeglass.app.ui.components.consumeTouches
import com.homeglass.app.ui.theme.GlassTheme
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/** Page d'accueil du lecteur web : la connexion au compte se fait dans la page. */
private const val SPOTIFY_WEB_URL = "https://open.spotify.com/"

/**
 * Firefox de bureau : la version mobile du Web Player remplace « Bibliothèque » par une
 * invite d'installation. L'User-Agent est écrit dans la préférence moteur (comme Firefox)
 * et non dans la session : l'override de session déclenche un préflight CORS refusé par
 * Spotify (bug GeckoView), qui casse les images et l'API de lecture.
 */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0"

/** Moteur GeckoView partagé par les ouvertures de la fenêtre Spotify. */
private object GeckoEngine {
    @Volatile private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime
                .create(
                    context.applicationContext,
                    GeckoRuntimeSettings.Builder().aboutConfigEnabled(true).build(),
                )
                .also { runtime = it }
        }
}

/** Applique l'User-Agent de bureau puis charge le Web Player. */
private fun loadSpotify(session: GeckoSession) {
    val load = { session.loadUri(SPOTIFY_WEB_URL) }
    GeckoPreferenceController.setGeckoPref(
        "general.useragent.override",
        DESKTOP_USER_AGENT,
        GeckoPreferenceController.PREF_BRANCH_USER,
    ).accept({ load() }, { load() })
}

private val WEB_BACKGROUND = Color(0xFF0B0B0C)

/**
 * Web Player Spotify intégré à l'application, rendu par **GeckoView** (moteur Firefox).
 *
 * Le moteur WebView d'Android ne permet pas la lecture des contenus protégés de Spotify ;
 * GeckoView embarque sa propre chaîne média et sait lire le Web Player, comme Firefox.
 * La session (cookies, connexion) est conservée dans le profil par défaut du moteur.
 */
@Composable
fun SpotifyWebScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    // Le moteur doit exister avant la première session : GeckoView initialise le contexte
    // applicatif dont dépend l'accessibilité des sessions.
    val runtime = remember { GeckoEngine.runtime(context) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    val session = remember {
        GeckoSession().apply {
            settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP

            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    failed = false
                }

                override fun onProgressChange(session: GeckoSession, progress: Int) {
                    loadProgress = progress
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    loadProgress = 0
                }
            }

            navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadError(
                    session: GeckoSession,
                    uri: String?,
                    error: WebRequestError,
                ): GeckoResult<String>? {
                    failed = true
                    loadProgress = 0
                    return null
                }
            }

            permissionDelegate = object : GeckoSession.PermissionDelegate {
                override fun onAndroidPermissionsRequest(
                    session: GeckoSession,
                    permissions: Array<out String>?,
                    callback: GeckoSession.PermissionDelegate.Callback,
                ) {
                    callback.grant()
                }

                /**
                 * Autorise Widevine : sans cette permission, Spotify refuse la lecture
                 * protégée. Les autres permissions de site restent refusées.
                 */
                override fun onContentPermissionRequest(
                    session: GeckoSession,
                    perm: GeckoSession.PermissionDelegate.ContentPermission,
                ): GeckoResult<Int> = GeckoResult.fromValue(
                    if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS) {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    } else {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    },
                )
            }
        }
    }

    LaunchedEffect(session, runtime) {
        session.open(runtime)
        loadSpotify(session)
    }

    DisposableEffect(session) {
        onDispose { session.close() }
    }

    // Le retour ramène au tableau de bord HomeGlass, pas à la page Spotify précédente.
    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .consumeTouches()
            .background(WEB_BACKGROUND)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        SpotifyWebBar(onBack = onClose)

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { GeckoView(context).apply { setSession(session) } },
                modifier = Modifier.fillMaxSize(),
            )

            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = Color(0xFF1DB954),
                    trackColor = Color.Transparent,
                )
            }

            if (failed) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.spotify_web_error),
                        style = GlassTheme.typography.tileStatus,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        text = stringResource(R.string.spotify_web_retry),
                        style = GlassTheme.typography.tileName,
                        color = Color(0xFF1DB954),
                        modifier = Modifier
                            .clip(GlassTheme.shapes.pill)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                failed = false
                                loadSpotify(session)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Barre du haut : retour, titre et fermeture implicite (retour sans historique). */
@Composable
private fun SpotifyWebBar(onBack: () -> Unit) {
    val description = stringResource(R.string.spotify_web_back)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onBack)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.spotify_web_player),
            style = GlassTheme.typography.tileName,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}
