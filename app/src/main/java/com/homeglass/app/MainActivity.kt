package com.homeglass.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.homeglass.app.data.ha.EncryptedSessionStore
import com.homeglass.app.data.ha.HaAuthManager
import com.homeglass.app.data.ha.HaController
import com.homeglass.app.data.ha.HaDiscovery
import com.homeglass.app.data.ha.HaHttpClient
import com.homeglass.app.data.ha.HttpClientFactory
import com.homeglass.app.data.ha.NetworkMonitor
import com.homeglass.app.data.layout.DataStoreLayoutCache
import com.homeglass.app.data.presence.PresenceService
import com.homeglass.app.data.presence.PresenceStore
import com.homeglass.app.ui.HomeGlassApp
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    /** Redirection OAuth reçue par `homeglass://auth-callback`. */
    private val redirect: MutableState<Uri?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        redirect.value = oauthRedirect(intent)
        applyWakeUp(intent)

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val controller = remember {
                val httpClient = HttpClientFactory.create()
                HaController(
                    context = context.applicationContext,
                    store = EncryptedSessionStore(context.applicationContext),
                    http = HaHttpClient(httpClient),
                    auth = HaAuthManager(HaHttpClient(httpClient)),
                    networkMonitor = NetworkMonitor(context.applicationContext),
                    discovery = HaDiscovery(context.applicationContext),
                    cache = DataStoreLayoutCache(context.applicationContext),
                    scope = scope,
                )
            }

            LaunchedEffect(controller) { controller.start() }

            // Le réveil par la caméra reprend au lancement si l'utilisateur l'a activé.
            LaunchedEffect(Unit) {
                if (PresenceStore(context.applicationContext).enabled.first()) {
                    PresenceService.start(context)
                }
            }

            HomeGlassApp(
                controller = controller,
                redirect = redirect.value,
                onRedirectConsumed = { redirect.value = null },
                onOpenUrl = { url ->
                    runCatching {
                        CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, Uri.parse(url))
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauthRedirect(intent)?.let { redirect.value = it }
        applyWakeUp(intent)
    }

    /**
     * Réveil déclenché par la détection de présence : l'écran s'allume et l'activité passe
     * au-dessus de l'écran de verrouillage.
     */
    private fun applyWakeUp(intent: Intent?) {
        if (intent?.getBooleanExtra(PresenceService.EXTRA_WAKE_UP, false) != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun oauthRedirect(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return data.takeIf { it.scheme == "homeglass" && it.host == "auth-callback" }
    }
}
