package com.homeglass.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.homeglass.app.R
import com.homeglass.app.data.presence.PresenceService
import com.homeglass.app.data.presence.PresenceStore
import com.homeglass.app.domain.presence.PresenceEntry
import com.homeglass.app.domain.presence.PresenceSource
import com.homeglass.app.domain.presence.PresenceTuning
import com.homeglass.app.domain.ha.ConnectionStatus
import com.homeglass.app.domain.ha.HaSession
import com.homeglass.app.ui.home.GlassPillButton
import com.homeglass.app.ui.theme.GlassTheme
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 
 * Réglages minimaux : instance connectée, état de la liaison (point vert / orange / rouge), 
 * adresse réellement utilisée et déconnexion. 
 */
@Composable
fun SettingsScreen(
    session: HaSession,
    status: ConnectionStatus,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = dimens.screenPadding)
            .padding(top = 24.dp, bottom = 140.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = typography.largeTitle, color = colors.screenTextPrimary)

        Spacer(Modifier.height(24.dp))
        SettingRow(
            label = stringResource(R.string.settings_instance),
            value = session.instance.displayName,
        )
        SettingRow(
            label = stringResource(R.string.settings_url),
            value = session.instance.baseUrl,
        )
        session.instance.version?.let { version -> 
            SettingRow(label = stringResource(R.string.settings_version), value = version)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(status.color(colors.categorySecure, colors.categoryWarning, colors.categoryDanger, colors.screenTextSecondary)),
            )
            Text(
                text = stringResource(status.labelRes()),
                style = typography.tileName,
                color = colors.screenTextPrimary,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_status),
            style = typography.caption,
            color = colors.screenTextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        GlassPillButton(
            text = stringResource(R.string.settings_customize),
            onClick = onCustomize,
        )

        Spacer(Modifier.height(24.dp))
        PresenceSetting()

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPillButton(text = stringResource(R.string.settings_reconnect), onClick = onReconnect)
            GlassPillButton(text = stringResource(R.string.settings_disconnect), onClick = onDisconnect)
        }
    }
}

/** 
 * Réveil au passage d'une personne : la caméra frontale est analysée par un service au 
 * premier plan ; l'autorisation est demandée à l'activation seulement. 
 */
@Composable
private fun PresenceSetting() {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography
    val context = LocalContext.current
    val store = remember(context) { PresenceStore(context.applicationContext) }
    val enabled by store.enabled.collectAsState(initial = false)
    val source by store.source.collectAsState(initial = PresenceSource.Auto)
    val log by store.log.collectAsState(initial = emptyList())
    val storedThreshold by store.thresholdCm.collectAsState(initial = PresenceTuning.DEFAULT_THRESHOLD_CM)
    val storedCooldown by store.cooldownMs.collectAsState(initial = PresenceTuning.DEFAULT_COOLDOWN_MS)
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }

    // Valeurs manipulées pendant le glissement, enregistrées au relâchement du curseur.
    var thresholdCm by remember { mutableFloatStateOf(storedThreshold) }
    var cooldownSeconds by remember { mutableFloatStateOf(storedCooldown / 1000f) }
    LaunchedEffect(storedThreshold) { thresholdCm = storedThreshold }
    LaunchedEffect(storedCooldown) { cooldownSeconds = storedCooldown / 1000f }

    // Le capteur de proximité est prioritaire : il ne demande aucune autorisation et
    // détecte une approche bien plus tôt qu'une analyse d'image.
    val hasProximity = remember(context) {
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
    }
    val hasFrontCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }
    val available = hasProximity || hasFrontCamera

    // Un capteur binaire (portée de quelques centimètres) ne sait pas dire « à 1 mètre » :
    // la distance n'a alors pas de sens, la caméra prend le relais en mode automatique.
    val proximityMaxRange = remember(context) {
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?.maximumRange ?: 0f
    }
    val binaryProximity = hasProximity && proximityMaxRange in 0f..10f

    // Une seule demande d'autorisation, réutilisée pour l'activation et pour le mode caméra.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> 
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke() else denied = true
    }
    val requestCameraPermission: (() -> Unit) -> Unit = { action -> 
        pendingAction = action
        launcher.launch(Manifest.permission.CAMERA)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_presence_title),
                style = typography.tileName,
                color = colors.screenTextPrimary,
            )
            Text(
                text = stringResource(R.string.settings_presence_description),
                style = typography.caption,
                color = colors.screenTextSecondary,
            )
        }
        Switch(
            checked = enabled,
            enabled = available,
            onCheckedChange = { value -> 
                denied = false
                if (!value) {
                    scope.launch { store.setEnabled(false) }
                    PresenceService.stop(context)
                } else if (
                    hasProximity ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    scope.launch { store.setEnabled(true) }
                    PresenceService.start(context)
                } else {
                    requestCameraPermission {
                        scope.launch { store.setEnabled(true) }
                        PresenceService.start(context)
                    }
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
        )
    }

    if (enabled && available) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_presence_source),
            style = typography.caption,
            color = colors.screenTextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresenceSource.entries.forEach { option -> 
                ChoiceChip(
                    label = stringResource(option.labelRes()),
                    selected = source == option,
                    enabled = when (option) {
                        PresenceSource.Auto -> true
                        PresenceSource.Proximity -> hasProximity
                        PresenceSource.Camera -> hasFrontCamera
                    },
                    onClick = {
                        val needsPermission =
                            option == PresenceSource.Camera &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                                PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            requestCameraPermission { scope.launch { store.setSource(option) } }
                        } else {
                            scope.launch { store.setSource(option) }
                        }
                    },
                )
            }
        }
    }

    if (!available) {
        Text(
            text = stringResource(R.string.settings_presence_unavailable),
            style = typography.caption,
            color = colors.categoryWarning,
        )
    } else if (denied) {
        Text(
            text = stringResource(R.string.settings_presence_denied),
            style = typography.caption,
            color = colors.categoryWarning,
        )
    }

    if (enabled && available) {
        TuningSlider(
            label = stringResource(R.string.settings_presence_distance),
            valueText = stringResource(
                R.string.settings_presence_distance_value,
                thresholdCm.roundToInt(),
            ),
            value = thresholdCm,
            range = PresenceTuning.MIN_THRESHOLD_CM..PresenceTuning.MAX_THRESHOLD_CM,
            enabled = !binaryProximity,
            onValueChange = { thresholdCm = it },
            onChangeFinished = { scope.launch { store.setThresholdCm(thresholdCm) } },
        )
        Text(
            text = stringResource(R.string.settings_presence_binary_hint),
            style = typography.caption,
            color = colors.screenTextSecondary,
        )
        TuningSlider(
            label = stringResource(R.string.settings_presence_cooldown),
            valueText = stringResource(
                R.string.settings_presence_cooldown_value,
                cooldownSeconds.roundToInt(),
            ),
            value = cooldownSeconds,
            range = (PresenceTuning.MIN_COOLDOWN_MS / 1000f)..(PresenceTuning.MAX_COOLDOWN_MS / 1000f),
            onValueChange = { cooldownSeconds = it },
            onChangeFinished = {
                scope.launch { store.setCooldownMs((cooldownSeconds * 1000f).roundToInt().toLong()) }
            },
        )

        Spacer(Modifier.height(16.dp))
        PresenceJournal(
            entries = log,
            onClear = { scope.launch { store.clearLog() } },
        )
    }
}

/** Pastille de choix (déclencheur), accentuée quand elle est retenue. */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes

    Box(
        modifier = Modifier
            .clip(shapes.pill)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.chipBackground)
            .border(1.dp, if (selected) colors.accent else Color.Transparent, shapes.pill)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = GlassTheme.typography.caption,
            color = when {
                !enabled -> colors.screenTextSecondary.copy(alpha = 0.4f)
                selected -> colors.accent
                else -> colors.screenTextPrimary
            },
        )
    }
}

/** Journal des derniers passages : heure, déclencheur et distance quand elle est connue. */
@Composable
private fun PresenceJournal(entries: List<PresenceEntry>, onClear: () -> Unit) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_presence_log),
            style = typography.tileName,
            color = colors.screenTextPrimary,
        )
        if (entries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.settings_presence_log_clear),
                style = typography.caption,
                color = colors.accent,
                modifier = Modifier
                    .clip(GlassTheme.shapes.pill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_presence_log_empty),
            style = typography.caption,
            color = colors.screenTextSecondary,
        )
        return
    }

    entries.take(6).forEach { entry -> 
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${timeFormat.format(Date(entry.at))} · ${stringResource(entry.source.labelRes())}",
                style = typography.caption,
                color = colors.screenTextSecondary,
                modifier = Modifier.weight(1f),
            )
            entry.detail?.let { detail -> 
                Text(detail, style = typography.caption, color = colors.screenTextPrimary)
            }
        }
    }
}

private fun PresenceSource.labelRes(): Int = when (this) {
    PresenceSource.Auto -> R.string.settings_presence_source_auto
    PresenceSource.Proximity -> R.string.settings_presence_source_proximity
    PresenceSource.Camera -> R.string.settings_presence_source_camera
}

/** Curseur étiqueté, enregistré à la fin du glissement. */
@Composable
private fun TuningSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = typography.caption, color = colors.screenTextSecondary)
            Text(valueText, style = typography.caption, color = colors.screenTextPrimary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = typography.caption, color = colors.screenTextSecondary)
        Text(value, style = typography.tileName, color = colors.screenTextPrimary)
    }
}

private fun ConnectionStatus.color(ok: Color, warn: Color, error: Color, idle: Color): Color = when (this) {
    ConnectionStatus.Connected -> ok
    is ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> warn
    is ConnectionStatus.Failed -> error
    ConnectionStatus.Idle -> idle
}

private fun ConnectionStatus.labelRes(): Int = when (this) {
    ConnectionStatus.Connected -> R.string.settings_status_connected
    is ConnectionStatus.Connecting -> R.string.settings_status_connecting
    is ConnectionStatus.Reconnecting -> R.string.settings_status_reconnecting
    is ConnectionStatus.Failed -> R.string.settings_status_failed
    ConnectionStatus.Idle -> R.string.settings_status_idle
}