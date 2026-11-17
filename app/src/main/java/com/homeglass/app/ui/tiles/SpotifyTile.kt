package com.homeglass.app.ui.tiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.ui.theme.GlassTheme

/** Ouvre le Web Player Spotify ; fourni par `HomeGlassApp` (onglet navigateur intégré). */
val LocalSpotifyOpen = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Case Spotify : le logo et un appui qui ouvre le Web Player.
 *
 * Elle n'a aucune entité Home Assistant et ne dépend d'aucune connexion : tout se passe
 * dans la page web (`open.spotify.com`), où le compte se connecte une seule fois.
 */
@Composable
fun SpotifyTile(model: TileUiModel, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography
    val open = LocalSpotifyOpen.current

    val background = colors.tileInactiveBackground
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    val textColor = if (luminance < 0.5f) Color.White else Color.Black
    val secondary = textColor.copy(alpha = 0.62f)
    val large = model.spanX >= 2 && model.spanY >= 2

    TileShell(
        active = false,
        available = true,
        onClick = open,
        onLongClick = open,
        modifier = modifier,
    ) {
        if (large) {
            Column(
                modifier = Modifier.fillMaxSize().padding(dimens.tilePadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpotifyLogo(size = 96.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.spotify_title),
                    style = typography.tileName,
                    color = textColor,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.spotify_open_web),
                    style = typography.tileStatus,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            return@TileShell
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(dimens.tilePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpotifyLogo(size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.spotify_title),
                    style = typography.tileName,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.spotify_open_web),
                    style = typography.tileStatus,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Logo Spotify (disque vert et ondes), dessiné en vectoriel. */
@Composable
private fun SpotifyLogo(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.ic_spotify),
        contentDescription = stringResource(R.string.spotify_title),
        modifier = Modifier.size(size),
    )
}
