package com.homeglass.app.ui.tiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.data.widgets.BingPhotoRepository
import com.homeglass.app.data.widgets.DailyPhoto
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.theme.GlassTheme
import kotlinx.coroutines.delay

/** Délai entre deux tirages de photo : l'image change sans action de l'utilisateur. */
private const val REFRESH_MILLIS = 15 * 60 * 1000L

/**
 * Tuile photo : une image d'ambiance tirée au hasard parmi les fonds du jour de Bing.
 *
 * Deux thèmes :
 * - `cover` : photo plein cadre, sans texte ;
 * - `soft` : photo avec dégradé et crédits en bas.
 */
@Composable
fun PhotoTile(
    model: TileUiModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography
    val theme = model.widgetTheme ?: WidgetKind.PHOTO_COVER

    var photo by remember(model.id) { mutableStateOf<DailyPhoto?>(null) }
    var bitmap by remember(model.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(model.id) {
        while (true) {
            val next = BingPhotoRepository.randomPhoto()
            if (next != null) {
                photo = next
                bitmap = BingPhotoRepository.bitmap(next.url)
            }
            delay(REFRESH_MILLIS)
        }
    }

    val description = stringResource(R.string.a11y_photo_tile)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(GlassTheme.shapes.tile)
            .background(colors.tileInactiveBackground)
            .semantics { contentDescription = description },
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (theme == WidgetKind.PHOTO_SOFT) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimens.tilePadding),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
                ) {
                    val caption = photo?.copyright?.takeIf { it.isNotBlank() }
                        ?: photo?.title.orEmpty()
                    if (caption.isNotEmpty()) {
                        Text(
                            text = caption,
                            style = typography.caption,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.tilePadding),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = colors.iconOnInactive,
                    modifier = Modifier.size(dimens.tileIcon),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.widget_photo_pending),
                    style = typography.caption,
                    color = colors.textOnInactiveSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
