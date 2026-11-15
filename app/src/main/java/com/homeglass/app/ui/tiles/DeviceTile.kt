package com.homeglass.app.ui.tiles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.core.haptics.rememberGlassHaptics
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Chrome commun à toutes les tuiles : fond animé, enfoncement à 0,96, haptique,
 * appui court / appui long, opacité réduite si l'entité est indisponible.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileShell(
    active: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val motion = GlassTheme.motion
    val haptics = rememberGlassHaptics()

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && available) 0.96f else 1f,
        animationSpec = motion.press(),
        label = "tilePressScale",
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !available -> colors.tileInactiveBackground
            active -> colors.tileActiveBackground
            else -> colors.tileInactiveBackground
        },
        animationSpec = motion.state(),
        label = "tileBackground",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shapes.tile)
            .background(SolidColor(backgroundColor))
            .alpha(if (available) 1f else 0.4f)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null, // pas de ripple Material : le retour visuel est l'échelle + la teinte
                enabled = available,
                onClick = {
                    haptics.tap()
                    onClick()
                },
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
            ),
    ) {
        overlay()
        content()
    }
}

/**
 * Tuile d'appareil standard : icône en haut à gauche, nom en bas, ligne d'état dessous.
 *
 * Le nombre de lignes du nom s'adapte à la hauteur disponible (1 ligne sur une tuile
 * 96 dp, 2 lignes sur une grande tuile) afin qu'aucun texte ne soit jamais tronqué
 * à cause du réglage de taille de police système.
 */
@Composable
fun DeviceTile(
    model: TileUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = TileVisuals.icon(model.category),
    tint: Color = TileVisuals.color(model, GlassTheme.colors),
    backgroundOverlay: @Composable BoxScope.() -> Unit = {},
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    /** Disposition spécifique quand la case est nettement plus large que haute. */
    wideContent: (@Composable RowScope.() -> Unit)? = null,
    /** Disposition dédiée aux grandes cases (2 colonnes × 2 rangées ou plus). */
    largeContent: (@Composable BoxScope.() -> Unit)? = null,
    showIcon: Boolean = true,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography
    val motion = GlassTheme.motion

    val iconTint by animateColorAsState(
        targetValue = if (model.isActive && model.isAvailable) tint else colors.iconOnInactive,
        animationSpec = motion.state(),
        label = "tileIconTint",
    )
    // Couleur de texte selon la luminance du fond de la tuile.
    // Attention : Color.red/green/blue renvoient des Float 0..1 (pas 0..255).
    val tileBg = if (model.isActive) colors.tileActiveBackground else colors.tileInactiveBackground
    val luminance = 0.2126f * tileBg.red + 0.7152f * tileBg.green + 0.0722f * tileBg.blue
    val textColor = if (luminance < 0.5f) Color.White else Color.Black


    val nameColor by animateColorAsState(
        targetValue = textColor,
        animationSpec = motion.state(),
        label = "tileNameColor",
    )
    val statusColor by animateColorAsState(
        targetValue = textColor,
        animationSpec = motion.state(),
        label = "tileStatusColor",
    )

    val a11yDescription = stringResource(R.string.a11y_tile_description, model.name, model.stateText)
    val unavailableLabel = stringResource(R.string.state_unavailable)

    TileShell(
        active = model.isActive && model.isAvailable,
        available = model.isAvailable,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = a11yDescription
            if (!model.isAvailable) stateDescription = unavailableLabel
        },
        overlay = backgroundOverlay,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(dimens.tilePadding)) {
            val density = LocalDensity.current
            val nameLine = with(density) { typography.tileName.lineHeight.toDp() }
            val statusLine = with(density) { typography.tileStatus.lineHeight.toDp() }
            // La ligne d'état ne doit jamais être rognée : le nom ne passe sur deux lignes
            // que s'il reste la hauteur exacte de cette ligne sous lui (marge de 2 dp).
            val statusSpace = if (model.stateText.isEmpty()) 0.dp else statusLine
            val textSpace = maxHeight - if (showIcon) dimens.tileIcon else 0.dp
            val nameMaxLines = if (textSpace >= nameLine * 2 + statusSpace + 2.dp) 2 else 1

            // Disposition choisie sur la forme réelle de la case, pas sur sa portée : une
            // case nettement plus large que haute place l'icône à gauche et le texte à
            // droite ; une case plus haute que large aère la disposition verticale. Ce n'est
            // donc pas un agrandissement : la disposition interne change réellement.
            val wide = bottomContent == null && maxWidth > maxHeight * 1.7f
            val tall = maxHeight > maxWidth * 1.2f

            if (largeContent != null && model.spanX >= 2 && model.spanY >= 2) {
                largeContent()
            } else if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showIcon) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(dimens.tileIcon),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            wideContent != null -> wideContent()
                            bottomContent != null -> Column { bottomContent() }
                            else -> Column(modifier = Modifier.weight(1f)) {
                                TileTexts(
                                    name = model.name,
                                    status = model.stateText,
                                    nameColor = nameColor,
                                    statusColor = statusColor,
                                    nameMaxLines = 2,
                                )
                            }
                        }
                    }
                }
            } else if (tall) {
                // Case haute : icône et texte centrés ensemble, sans grand vide au-dessus.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (showIcon) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(dimens.tileIcon),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (bottomContent != null) {
                        bottomContent()
                    } else {
                        TileTexts(
                            name = model.name,
                            status = model.stateText,
                            nameColor = nameColor,
                            statusColor = statusColor,
                            nameMaxLines = 2,
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showIcon) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null, // décoratif : la description est portée par la tuile entière
                            tint = iconTint,
                            modifier = Modifier.size(dimens.tileIcon),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (bottomContent != null) {
                        bottomContent()
                    } else {
                        TileTexts(
                            name = model.name,
                            status = model.stateText,
                            nameColor = nameColor,
                            statusColor = statusColor,
                            nameMaxLines = nameMaxLines,
                        )
                    }
                }
            }
        }
    }
}

/** Bloc texte nom + état, partagé par les tuiles qui gardent la disposition standard. */
@Composable
internal fun ColumnScope.TileTexts(
    name: String,
    status: String,
    nameColor: Color,
    statusColor: Color,
    nameMaxLines: Int,
) {
    val typography = GlassTheme.typography
    Text(
        text = name,
        style = typography.tileName,
        color = nameColor,
        maxLines = nameMaxLines,
        overflow = TextOverflow.Ellipsis,
    )
    // Tant que les états Home Assistant ne sont pas branchés (étape 4), aucune ligne n'est
    // réservée : la tuile ne doit pas afficher de blanc inutile sous le nom.
    if (status.isNotEmpty()) {
        Text(
            text = status,
            style = typography.tileStatus,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
