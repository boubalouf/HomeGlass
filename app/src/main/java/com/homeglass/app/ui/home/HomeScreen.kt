package com.homeglass.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.core.haptics.rememberGlassHaptics
import com.homeglass.app.domain.layout.LayoutIssue
import com.homeglass.app.domain.layout.LayoutState
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.tiles.FreeTileGrid
import com.homeglass.app.ui.tiles.TileGrid
import com.homeglass.app.ui.tiles.TileUiModel

/**
 * Contenu de l'onglet Maison : titre, chips, sections de tuiles et cartes de scènes.
 * La barre de navigation, le fond et le flou sont fournis par la coquille (`HomeGlassApp`).
 *
 * Aucune action n'est exécutée ici : les gestes remontent au contrôleur, qui décide de la
 * bascule, de l'appel de service ou de l'ouverture du panneau d'info.
 */
@Composable
fun HomeScreen(
    home: HomeUiModel?,
    state: LayoutState,
    onRetry: () -> Unit,
    onTileTap: (TileUiModel) -> Unit,
    onTileLongPress: (TileUiModel) -> Unit,
    onTileLevelChange: (TileUiModel, Float) -> Unit,
    onSceneTap: (SceneCardUi) -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when {
            home != null -> HomeContent(
                home = home,
                onTileTap = onTileTap,
                onTileLongPress = onTileLongPress,
                onTileLevelChange = onTileLevelChange,
                onSceneTap = onSceneTap,
                onCustomize = onCustomize,
            )

            state is LayoutState.Loading -> LoadingScreen()
            state is LayoutState.Error -> ErrorScreen(issue = state.issue, onRetry = onRetry)
        }

        if (state is LayoutState.Stale && state.issue != LayoutIssue.Cached) {
            LayoutBanner(issue = state.issue, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun HomeContent(
    home: HomeUiModel,
    onTileTap: (TileUiModel) -> Unit,
    onTileLongPress: (TileUiModel) -> Unit,
    onTileLevelChange: (TileUiModel, Float) -> Unit,
    onSceneTap: (SceneCardUi) -> Unit,
    onCustomize: () -> Unit,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = dimens.screenPadding)
            .padding(top = 24.dp, bottom = 140.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                home.title,
                style = typography.largeTitle,
                color = colors.screenTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Personnalisation accessible directement depuis l'accueil, sans passer par les
            // réglages : l'utilisateur modifie ses tuiles là où il les voit.
            Spacer(Modifier.width(12.dp))
            GlassPillButton(text = stringResource(R.string.home_edit), onClick = onCustomize)
        }

        if (home.chips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SummaryChips(home.chips)
        }

        home.sections.forEach { section ->
            Spacer(Modifier.height(24.dp))
            if (section.title.isNotBlank()) {
                Text(section.title, style = typography.sectionTitle, color = colors.screenTextPrimary)
                Spacer(Modifier.height(12.dp))
            }

            when (section.kind) {
                HomeSectionKind.Tiles -> TileSection(
                    tiles = section.tiles,
                    freeform = section.freeform,
                    onTileTap = onTileTap,
                    onTileLongPress = onTileLongPress,
                    onTileLevelChange = onTileLevelChange,
                )

                HomeSectionKind.Scenes -> SceneSection(
                    scenes = section.scenes,
                    onSceneTap = onSceneTap,
                )
            }
        }
    }
}

/**
 * Grille de tuiles d'une section, partagée par l'accueil et les pièces. Une section
 * `freeform` utilise les positions enregistrées sur la grille libre ; les autres gardent le
 * placement automatique compact.
 */
@Composable
internal fun TileSection(
    tiles: List<TileUiModel>,
    onTileTap: (TileUiModel) -> Unit,
    onTileLongPress: (TileUiModel) -> Unit,
    onTileLevelChange: (TileUiModel, Float) -> Unit,
    freeform: Boolean = false,
) {
    if (tiles.isEmpty()) return
    if (freeform) {
        FreeTileGrid(
            tiles = tiles,
            onTileClick = onTileTap,
            onTileLongClick = onTileLongPress,
            onTileLevelChange = onTileLevelChange,
        )
    } else {
        TileGrid(
            tiles = tiles,
            onTileClick = onTileTap,
            onTileLongClick = onTileLongPress,
            onTileLevelChange = onTileLevelChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryChips(chips: List<SummaryChipUi>) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Row(
                modifier = Modifier
                    .clip(shapes.chip)
                    .background(colors.chipBackground)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = chip.icon,
                    contentDescription = null,
                    tint = colors.screenTextSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(chip.labelRes),
                    style = typography.caption,
                    color = colors.screenTextPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Cartes de scène à dégradé, deux par ligne, même hauteur que les tuiles. */
@Composable
internal fun SceneSection(scenes: List<SceneCardUi>, onSceneTap: (SceneCardUi) -> Unit) {
    if (scenes.isEmpty()) return
    val dimens = GlassTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.gridSpacing)) {
        scenes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing)) {
                row.forEach { scene ->
                    SceneCard(
                        scene = scene,
                        onClick = { onSceneTap(scene) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SceneCard(scene: SceneCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = GlassTheme.dimens
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography
    val haptics = rememberGlassHaptics()
    val description = stringResource(R.string.a11y_scene_card, scene.name)

    Box(
        modifier = modifier
            .height(dimens.tileHeight)
            .clip(shapes.tile)
            .background(Brush.linearGradient(scene.gradient))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(dimens.tilePadding)
            .semantics { contentDescription = description },
    ) {
        Column(Modifier.fillMaxSize()) {
            Icon(
                imageVector = scene.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(dimens.tileIcon),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = scene.name,
                style = typography.tileName,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    val colors = GlassTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.layout_loading),
                style = GlassTheme.typography.caption,
                color = colors.screenTextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorScreen(issue: LayoutIssue, onRetry: () -> Unit) {
    val colors = GlassTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(issue.messageRes()),
                style = GlassTheme.typography.sectionTitle,
                color = colors.screenTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            GlassPillButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}

/** Bouton pilule du design system, réutilisé par les écrans de connexion et de réglages. */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val haptics = rememberGlassHaptics()

    Box(
        modifier = modifier
            .clip(shapes.pill)
            .background(if (enabled) colors.chipBackground else colors.chipBackground.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = GlassTheme.typography.tileName,
            color = if (enabled) colors.screenTextPrimary else colors.screenTextSecondary,
        )
    }
}

/** Bandeau discret quand l'écran affiche une version en cache ou non exploitable. */
@Composable
fun LayoutBanner(issue: LayoutIssue, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shapes.pill)
            .background(colors.chipBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = colors.categoryWarning,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(issue.messageRes()),
            style = GlassTheme.typography.caption,
            color = colors.screenTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@StringRes
internal fun LayoutIssue.messageRes(): Int = when (this) {
    LayoutIssue.Cached -> R.string.layout_issue_cached
    LayoutIssue.Offline -> R.string.layout_issue_offline
    LayoutIssue.UpdateApp -> R.string.layout_issue_update_app
    LayoutIssue.CorruptedLayout -> R.string.layout_issue_corrupted
    LayoutIssue.NoData -> R.string.layout_issue_no_data
}
