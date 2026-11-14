package com.homeglass.app.ui.welcome

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.data.ha.DiscoveredInstance
import com.homeglass.app.data.ha.DiscoveredServer
import com.homeglass.app.data.ha.HaController
import com.homeglass.app.data.ha.LoginResult
import com.homeglass.app.data.ha.PairingStartResult
import com.homeglass.app.domain.ha.BaseUrlChooser
import com.homeglass.app.domain.ha.HaInstance
import com.homeglass.app.domain.ha.PairingProtocol
import com.homeglass.app.domain.ha.PairingStart
import com.homeglass.app.ui.home.GlassPillButton
import com.homeglass.app.ui.theme.GlassTheme
import kotlinx.coroutines.launch

/**
 * Écran de bienvenue : appairage d'un HomeGlass Server par code (méthode recommandée), puis
 * connexion directe à Home Assistant par jeton longue durée ou OAuth.
 */
@Composable
fun WelcomeScreen(
    controller: HaController,
    message: String?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<WelcomeStep>(WelcomeStep.Instances) }
    var instances by remember { mutableStateOf<List<DiscoveredInstance>>(emptyList()) }
    var servers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var scanKey by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<Int?>(null) }

    var manualUrl by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    var serverUrl by remember { mutableStateOf("") }
    var showServerManual by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    val deviceName = remember { Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android" }

    LaunchedEffect(scanKey) {
        searching = true
        instances = emptyList()
        controller.discoverInstances().collect { found -> instances = found }
        searching = false
    }

    // L'add-on partage la machine de Home Assistant : on teste son port dès qu'un hôte est connu.
    LaunchedEffect(instances) {
        servers = if (instances.isEmpty()) emptyList() else controller.probeServers(instances)
    }

    fun requestPairing(baseUrl: String) {
        if (busy) return
        busy = true
        localError = null
        scope.launch {
            try {
                when (val result = controller.startPairing(baseUrl, deviceName, Build.MODEL ?: "Android")) {
                    is PairingStartResult.Ok -> {
                        code = ""
                        step = WelcomeStep.Pairing(baseUrl, result.start)
                    }

                    PairingStartResult.NotAddon -> localError = R.string.pair_not_server
                    PairingStartResult.Unreachable -> localError = R.string.pair_server_unreachable
                }
            } catch (error: Throwable) {
                // Une erreur inattendue ne doit pas laisser le bouton définitivement inactif.
                localError = R.string.pair_server_unreachable
            } finally {
                busy = false
            }
        }
    }

    fun submit(instance: HaInstance) {
        if (busy) return
        busy = true
        localError = null
        scope.launch {
            val result = controller.login(instance, token)
            busy = false
            localError = result.errorRes()
            if (result == LoginResult.Connected) token = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = dimens.screenPadding)
            .padding(top = 32.dp, bottom = 48.dp),
    ) {
        Text(stringResource(R.string.welcome_title), style = typography.largeTitle, color = colors.screenTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = typography.tileStatus,
            color = colors.screenTextSecondary,
        )

        Spacer(Modifier.height(28.dp))

        when (val current = step) {
            WelcomeStep.Instances -> {
                Text(
                    text = stringResource(R.string.welcome_pair_title),
                    style = typography.sectionTitle,
                    color = colors.screenTextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.welcome_pair_subtitle),
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                )
                Spacer(Modifier.height(14.dp))

                if (searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SearchingIndicator()
                        Text(
                            text = stringResource(R.string.welcome_searching),
                            style = typography.tileName,
                            color = colors.screenTextPrimary,
                        )
                    }
                }

                servers.forEach { server ->
                    InstanceRow(
                        name = stringResource(
                            R.string.welcome_server_row,
                            server.version.ifBlank { "?" },
                        ),
                        url = server.baseUrl,
                        onClick = { requestPairing(server.baseUrl) },
                    )
                }

                if (!searching && servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.welcome_servers_none),
                        style = typography.tileStatus,
                        color = colors.screenTextSecondary,
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (showServerManual) {
                    GlassTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = stringResource(R.string.welcome_server_manual_hint),
                        keyboardType = KeyboardType.Uri,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassPillButton(
                        text = stringResource(R.string.welcome_continue),
                        enabled = serverUrl.isNotBlank() && !busy,
                        onClick = { requestPairing(serverUrl) },
                    )
                } else {
                    LinkText(
                        text = stringResource(R.string.welcome_server_manual),
                        onClick = { showServerManual = true },
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.welcome_direct_title),
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                )
                Spacer(Modifier.height(12.dp))

                if (instances.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.welcome_found_many, instances.size),
                        style = typography.caption,
                        color = colors.screenTextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    instances.forEach { instance ->
                        InstanceRow(
                            name = instance.locationName ?: instance.name,
                            url = instance.baseUrl,
                            onClick = {
                                step = WelcomeStep.Token(
                                    HaInstance(
                                        baseUrl = instance.baseUrl,
                                        internalUrl = instance.internalUrl,
                                        externalUrl = instance.externalUrl,
                                        locationName = instance.locationName,
                                        version = instance.version,
                                        uuid = instance.uuid,
                                    ),
                                )
                                manualUrl = instance.baseUrl
                            },
                        )
                    }
                } else if (!searching) {
                    Text(
                        text = stringResource(R.string.welcome_none_found),
                        style = typography.tileStatus,
                        color = colors.screenTextSecondary,
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (showManual) {
                    GlassTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        placeholder = stringResource(R.string.welcome_manual_hint),
                        keyboardType = KeyboardType.Uri,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassPillButton(
                        text = stringResource(R.string.welcome_continue),
                        enabled = manualUrl.isNotBlank(),
                        onClick = {
                            val normalized = BaseUrlChooser.normalize(manualUrl)
                            if (normalized.isBlank()) {
                                localError = R.string.welcome_manual_invalid
                            } else {
                                step = WelcomeStep.Token(HaInstance(baseUrl = normalized))
                            }
                        },
                    )
                } else {
                    LinkText(
                        text = stringResource(R.string.welcome_manual),
                        onClick = { showManual = true },
                    )
                }

                if (!searching) {
                    Spacer(Modifier.height(16.dp))
                    LinkText(
                        text = stringResource(R.string.welcome_retry_search),
                        onClick = { scanKey++ },
                    )
                }
            }

            is WelcomeStep.Pairing -> {
                Text(
                    text = stringResource(R.string.pair_code_title),
                    style = typography.sectionTitle,
                    color = colors.screenTextPrimary,
                )
                Text(
                    text = current.baseUrl,
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.pair_code_explainer, current.start.codeLength),
                    style = typography.tileStatus,
                    color = colors.screenTextPrimary,
                )

                Spacer(Modifier.height(14.dp))
                GlassTextField(
                    value = code,
                    onValueChange = { code = PairingProtocol.normalizeCode(it, current.start.codeLength) },
                    placeholder = stringResource(R.string.pair_code_hint),
                    keyboardType = KeyboardType.Number,
                )

                Spacer(Modifier.height(16.dp))
                GlassPillButton(
                    text = stringResource(if (busy) R.string.auth_busy else R.string.pair_code_submit),
                    enabled = !busy && code.length >= 4,
                    onClick = {
                        if (!busy) {
                            busy = true
                            localError = null
                            scope.launch {
                                val result =
                                    controller.claimPairing(current.baseUrl, current.start.pairingId, code)
                                busy = false
                                localError = result.errorRes()
                                if (result == LoginResult.Connected) code = ""
                            }
                        }
                    },
                )

                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.pair_code_waiting),
                        style = typography.caption,
                        color = colors.screenTextSecondary,
                    )
                }

                Spacer(Modifier.height(24.dp))
                LinkText(
                    text = stringResource(R.string.welcome_back),
                    onClick = {
                        step = WelcomeStep.Instances
                        code = ""
                        localError = null
                    },
                )
            }

            is WelcomeStep.Token -> {
                Text(
                    text = current.instance.displayName,
                    style = typography.sectionTitle,
                    color = colors.screenTextPrimary,
                )
                Text(
                    text = current.instance.baseUrl,
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.auth_token_explainer),
                    style = typography.tileStatus,
                    color = colors.screenTextPrimary,
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.auth_token_label),
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                GlassTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = stringResource(R.string.auth_token_hint),
                    keyboardType = KeyboardType.Password,
                    masked = true,
                )

                Spacer(Modifier.height(16.dp))
                GlassPillButton(
                    text = stringResource(if (busy) R.string.auth_busy else R.string.auth_token_submit),
                    enabled = !busy && token.isNotBlank(),
                    onClick = { submit(current.instance) },
                )

                if (controller.oauthAvailable) {
                    Spacer(Modifier.height(16.dp))
                    LinkText(
                        text = stringResource(R.string.auth_oauth),
                        onClick = {
                            val url = controller.startOAuth(current.instance)
                            if (url == null) {
                                localError = R.string.auth_oauth_unavailable
                            } else {
                                onOpenUrl(url)
                            }
                        },
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.auth_oauth_unavailable),
                        style = typography.caption,
                        color = colors.screenTextSecondary,
                    )
                }

                Spacer(Modifier.height(24.dp))
                LinkText(
                    text = stringResource(R.string.welcome_back),
                    onClick = {
                        step = WelcomeStep.Instances
                        token = ""
                        localError = null
                    },
                )
            }
        }

        val errorText = localError?.let { stringResource(it) } ?: message
        if (errorText != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = errorText,
                style = typography.tileStatus,
                color = colors.categoryDanger,
            )
        }
    }
}

private sealed interface WelcomeStep {
    data object Instances : WelcomeStep
    data class Pairing(val baseUrl: String, val start: PairingStart) : WelcomeStep
    data class Token(val instance: HaInstance) : WelcomeStep
}

private fun LoginResult.errorRes(): Int? = when (this) {
    LoginResult.Connected -> null
    LoginResult.InvalidToken -> R.string.auth_token_invalid
    LoginResult.Unreachable -> R.string.auth_error_unreachable
    LoginResult.CleartextRefused -> R.string.welcome_manual_cleartext
    LoginResult.OAuthUnavailable -> R.string.auth_oauth_unavailable
    LoginResult.CodeWrong -> R.string.pair_code_invalid
    LoginResult.CodeExpired -> R.string.pair_code_expired
    LoginResult.TooManyAttempts -> R.string.pair_code_too_many
    LoginResult.NotAddonServer -> R.string.pair_not_server
    LoginResult.SessionExpired -> R.string.auth_session_expired
    is LoginResult.Failed -> R.string.error_command
}

/** Anneaux pulsés pendant la recherche mDNS. */
@Composable
private fun SearchingIndicator() {
    val accent = GlassTheme.colors.accent
    val transition = rememberInfiniteTransition(label = "search")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "pulse",
    )

    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = accent.copy(alpha = ((1f - progress) * 0.4f).coerceIn(0f, 1f)),
                radius = size.minDimension / 2f * (0.35f + 0.65f * progress),
            )
            drawCircle(color = accent, radius = size.minDimension / 10f)
        }
    }
}

@Composable
private fun InstanceRow(name: String, url: String, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography
    val haptics = com.homeglass.app.core.haptics.rememberGlassHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shapes.tile)
            .background(colors.chipBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(name, style = typography.tileName, color = colors.screenTextPrimary)
        Text(url, style = typography.caption, color = colors.screenTextSecondary)
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    masked: Boolean = false,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.pill)
            .background(colors.chipBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = typography.tileStatus, color = colors.screenTextSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = typography.tileStatus.copy(color = colors.screenTextPrimary),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (masked) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val haptics = com.homeglass.app.core.haptics.rememberGlassHaptics()

    Text(
        text = text,
        style = GlassTheme.typography.tileName,
        color = colors.accent,
        modifier = Modifier
            .clip(GlassTheme.shapes.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(vertical = 8.dp),
    )
}
