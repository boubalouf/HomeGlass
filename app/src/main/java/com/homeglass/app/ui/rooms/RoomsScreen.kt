package com.homeglass.app.ui.rooms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeglass.app.R
import com.homeglass.app.ui.home.HomeSectionKind
import com.homeglass.app.ui.home.RoomUi
import com.homeglass.app.ui.home.SceneCardUi
import com.homeglass.app.ui.home.SceneSection
import com.homeglass.app.ui.home.TileSection
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.tiles.TileUiModel

/**
 * Écran Pièces : une section par aire Home Assistant, même rendu que l'accueil.
 *
 * Le grand titre se réduit progressivement au défilement (34 → 26 sp) au lieu de disparaître
 * brutalement : sur une liste de pièces, la position reste lisible sans barre repliée.
 */
@Composable
fun RoomsScreen(
    rooms: List<RoomUi>,
    onTileTap: (TileUiModel) -> Unit,
    onTileLongPress: (TileUiModel) -> Unit,
    onTileLevelChange: (TileUiModel, Float) -> Unit,
    onSceneTap: (SceneCardUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography
    val scroll = rememberScrollState()
    val collapse = (scroll.value / 200f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = dimens.screenPadding)
            .padding(top = 24.dp, bottom = 140.dp),
    ) {
        Text(
            text = stringResource(R.string.rooms_title),
            style = typography.largeTitle.copy(fontSize = (34f - 8f * collapse).sp),
            color = colors.screenTextPrimary,
        )

        if (rooms.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.rooms_empty),
                style = typography.tileStatus,
                color = colors.screenTextSecondary,
            )
            return@Column
        }

        rooms.forEach { room ->
            Spacer(Modifier.height(24.dp))
            Text(
                text = room.name,
                style = typography.sectionTitle.copy(fontSize = (22f - 2f * collapse).sp),
                color = colors.screenTextPrimary,
            )
            Spacer(Modifier.height(12.dp))

            room.sections.forEach { section ->
                when (section.kind) {
                    HomeSectionKind.Tiles -> TileSection(
                        tiles = section.tiles,
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
}
