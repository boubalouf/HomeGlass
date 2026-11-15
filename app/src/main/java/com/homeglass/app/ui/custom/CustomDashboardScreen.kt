package com.homeglass.app.ui.custom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.domain.dashboard.DashboardLayout
import com.homeglass.app.domain.dashboard.PlacedTile
import com.homeglass.app.domain.dashboard.WidgetItem
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.home.GlassPillButton
import com.homeglass.app.ui.home.storedLabel
import com.homeglass.app.ui.home.toPlacedTile
import com.homeglass.app.ui.home.toTileUiModel
import com.homeglass.app.ui.home.toWidgetKind
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.tiles.EntityTile
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileUiModel
import com.homeglass.app.ui.tiles.freeColumnsFor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Éditeur du tableau de bord personnel.
 *
 * L'accueil se manipule comme un canevas : chaque case se déplace au doigt sur une grille de
 * 2 à 4 colonnes et se redimensionne par sa poignée en bas à droite. Le contenu de la case
 * se réorganise réellement à la nouvelle taille (pas un simple zoom), car la case est
 * remesurée à chaque changement de portée.
 */
@Composable
fun CustomDashboardScreen(
    initialTiles: List<TileUiModel>,
    availableTiles: List<TileUiModel>,
    onSave: (List<PlacedTile>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography
    val dimens = GlassTheme.dimens

    // Les tuiles sans position (accueil généré) sont placées une première fois pour donner
    // un canevas cohérent. Le semis n'a lieu qu'à l'ouverture : les mises à jour d'état Home
    // Assistant renvoient de nouvelles listes et réinitialisaient sinon les modifications en
    // cours (la case revenait à sa place d'origine, voire à la disposition par défaut).
    // `initialTiles` est un instantané fixé à l'ouverture (voir AppRoot) : l'état ci-dessous
    // ne se réinitialise donc plus à chaque relevé d'état.
    var tiles by remember { mutableStateOf(seedPositions(initialTiles)) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }

    // Canevas défilable, partagé avec l'ajout : une nouvelle case est amenée à l'écran.
    val canvasScroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val rowStridePx = with(LocalDensity.current) { (dimens.tileHeight + dimens.gridSpacing).toPx() }

    // Nom d'origine de chaque entité : sert à ne mémoriser que les renommages réels.
    val originalNames = remember(availableTiles) {
        availableTiles.mapNotNull { tile -> tile.entityId?.let { it to tile.name } }.toMap()
    }

    fun placed(): List<PlacedTile> = tiles.map { tile ->
        val placedTile = tile.toPlacedTile()
        val customLabel = if (tile.entityId != null) {
            storedLabel(tile.name, tile.entityId.let(originalNames::get))
        } else {
            null
        }
        placedTile.copy(label = customLabel)
    }

    fun settle(priorityId: String, columns: Int) {
        val geometry = DashboardLayout.settle(placed(), priorityId, columns).associateBy { it.id }
        tiles = tiles.map { tile ->
            geometry[tile.id]?.let { cell ->
                tile.copy(spanX = cell.spanX, spanY = cell.spanY, gridCol = cell.col, gridRow = cell.row)
            } ?: tile
        }
    }

    fun addTile(tile: TileUiModel, columns: Int) {
        if (tiles.any { it.id == tile.id }) return
        val spot = DashboardLayout.firstFree(placed(), tile.spanX, tile.spanY, columns) ?: (0 to 0)
        tiles = tiles + tile.copy(gridCol = spot.first, gridRow = spot.second)
        selectedId = tile.id
        // Amène la nouvelle case dans le champ de vision du canevas.
        scrollScope.launch { canvasScroll.animateScrollTo((spot.second * rowStridePx).roundToInt()) }
    }

    val remaining = availableTiles
        .filter { candidate ->
            tiles.none { it.id == candidate.id || (it.entityId != null && it.entityId == candidate.entityId) }
        }
        .filter { candidate ->
            query.isBlank() ||
                candidate.name.contains(query, ignoreCase = true) ||
                (candidate.entityId?.contains(query, ignoreCase = true) == true)
        }
        .sortedBy { candidate -> candidate.name.lowercase() }

    BoxWithConstraints(
        modifier.fillMaxSize().background(Brush.verticalGradient(colors.backgroundGradient)),
    ) {
        // Nombre de colonnes réel de l'écran : les ajouts se placent sur cette grille.
        val screenColumns = freeColumnsFor(maxWidth)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = dimens.screenPadding)
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.customize_title),
                    style = typography.largeTitle,
                    color = colors.screenTextPrimary,
                )
                Text(
                    text = stringResource(R.string.customize_cancel),
                    style = typography.tileName,
                    color = colors.screenTextSecondary,
                    modifier = Modifier
                        .clip(shapes.pill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.customize_drag_hint),
                style = typography.caption,
                color = colors.screenTextSecondary,
            )

            Spacer(Modifier.height(10.dp))

            // Canevas : toute la place restante, défilement vertical possible.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val columns = screenColumns
                val spacing = dimens.gridSpacing
                val rowHeight = dimens.tileHeight
                val columnWidth = ((maxWidth - spacing * (columns - 1)) / columns).coerceAtLeast(120.dp)

                val rows = (tiles.maxOfOrNull { (it.gridRow ?: 0) + it.spanY } ?: 0)
                    .coerceAtLeast(3) + 1
                val canvasHeight = rowHeight * rows + spacing * (rows - 1)

                Column(Modifier.fillMaxSize().verticalScroll(canvasScroll)) {
                    Box(Modifier.fillMaxWidth().height(canvasHeight)) {
                        tiles.forEach { tile ->
                            EditorTile(
                                tile = tile,
                                columns = columns,
                                columnWidth = columnWidth,
                                rowHeight = rowHeight,
                                spacing = spacing,
                                selected = tile.id == selectedId,
                                onSelect = { selectedId = it },
                                onMove = { col, row ->
                                    val moved = tile.copy(gridCol = col, gridRow = row)
                                    tiles = tiles.map { if (it.id == tile.id) moved else it }
                                    settle(tile.id, columns)
                                },
                                onResize = { spanX, spanY ->
                                    val resized = tile.copy(spanX = spanX, spanY = spanY)
                                    tiles = tiles.map { if (it.id == tile.id) resized else it }
                                    settle(tile.id, columns)
                                },
                                onRemove = {
                                    tiles = tiles.filterNot { it.id == tile.id }
                                    if (selectedId == tile.id) selectedId = null
                                },
                                onCycleTheme = {
                                    val kind = tile.category.toWidgetKind()
                                    if (kind != null && kind.themes.size > 1) {
                                        val themes = kind.themes
                                        val current = kind.coerceTheme(tile.widgetTheme ?: kind.defaultTheme)
                                        val next = themes[(themes.indexOf(current) + 1) % themes.size]
                                        tiles = tiles.map {
                                            if (it.id == tile.id) it.copy(widgetTheme = next) else it
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Palette : cases déco, recherche et appareils à ajouter.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetKind.addable.forEach { kind ->
                        AddChip(
                            icon = kind.iconVector(),
                            text = stringResource(kind.labelRes()),
                            onClick = { addTile(widgetTile(kind), screenColumns) },
                        )
                    }
                }

                // Renommage d'un appareil sélectionné : le nom affiché remplace celui de
                // Home Assistant sans toucher à l'entité.
                val selectedEntity = tiles.firstOrNull {
                    it.id == selectedId && it.entityId != null
                }
                AnimatedVisibility(visible = selectedEntity != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.pill)
                            .background(colors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (selectedEntity != null && selectedEntity.name.isEmpty()) {
                            Text(
                                text = stringResource(R.string.customize_rename_hint),
                                style = typography.tileName,
                                color = colors.screenTextSecondary,
                            )
                        }
                        BasicTextField(
                            value = selectedEntity?.name.orEmpty(),
                            onValueChange = { value ->
                                val id = selectedEntity?.id ?: return@BasicTextField
                                tiles = tiles.map { if (it.id == id) it.copy(name = value) else it }
                            },
                            singleLine = true,
                            textStyle = typography.tileName.copy(color = colors.screenTextPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.pill)
                        .background(colors.chipBackground)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.customize_search_hint),
                            style = typography.tileName,
                            color = colors.screenTextSecondary,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = typography.tileName.copy(color = colors.screenTextPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Liste toujours visible : on peut parcourir les appareils ou rechercher.
                LazyColumn(modifier = Modifier.height(130.dp).fillMaxWidth()) {
                    items(remaining, key = { it.id }) { candidate ->
                        AvailableRow(
                            name = candidate.name.ifBlank { candidate.entityId.orEmpty() },
                            detail = candidate.entityId.orEmpty(),
                            onAdd = { addTile(candidate, screenColumns) },
                        )
                    }
                    if (remaining.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.customize_empty),
                                style = typography.caption,
                                color = colors.screenTextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPillButton(
                    text = stringResource(R.string.customize_save),
                    onClick = { onSave(placed()) },
                )
                GlassPillButton(
                    text = stringResource(R.string.customize_reset),
                    onClick = { onSave(emptyList()) },
                )
            }
        }
    }
}

/** Place les tuiles sans position sur la grille, sans toucher à celles déjà positionnées. */
private fun seedPositions(tiles: List<TileUiModel>): List<TileUiModel> {
    if (tiles.isEmpty()) return tiles
    if (tiles.all { it.gridCol != null && it.gridRow != null }) return tiles
    val placed = DashboardLayout.autoPlace(tiles.map { it.toPlacedTile() })
    val byId = placed.associateBy { it.id }
    return tiles.map { tile ->
        byId[tile.id]?.let { cell ->
            tile.copy(spanX = cell.spanX, spanY = cell.spanY, gridCol = cell.col, gridRow = cell.row)
        } ?: tile
    }
}

private fun widgetTile(kind: WidgetKind): TileUiModel =
    WidgetItem(
        id = "widget_${kind.name}_${System.currentTimeMillis()}",
        kind = kind,
        theme = kind.defaultTheme,
    ).toTileUiModel()

/**
 * Case du canevas : aperçu réel de la tuile, déplacement au doigt et poignée de
 * redimensionnement. Une surcouche transparente capte les gestes pour que la tuile ne
 * déclenche aucune action Home Assistant pendant l'édition.
 */
@Composable
private fun EditorTile(
    tile: TileUiModel,
    columns: Int,
    columnWidth: Dp,
    rowHeight: Dp,
    spacing: Dp,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onCycleTheme: () -> Unit,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val density = LocalDensity.current

    val col = tile.gridCol ?: 0
    val row = tile.gridRow ?: 0

    var drag by remember(tile.id) { mutableStateOf(Offset.Zero) }
    var resize by remember(tile.id) { mutableStateOf(Offset.Zero) }
    var previewSpan by remember(tile.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    val cellWidthPx = with(density) { (columnWidth + spacing).toPx() }
    val cellHeightPx = with(density) { (rowHeight + spacing).toPx() }

    val spanX = previewSpan?.first ?: tile.spanX
    val spanY = previewSpan?.second ?: tile.spanY

    // Les positions et tailles s'animent quand la case se recale, pour un déplacement fluide.
    val settleSpec: AnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val baseX by animateDpAsState(
        targetValue = (columnWidth + spacing) * col,
        animationSpec = settleSpec,
        label = "tileX",
    )
    val baseY by animateDpAsState(
        targetValue = (rowHeight + spacing) * row,
        animationSpec = settleSpec,
        label = "tileY",
    )
    val animatedWidth by animateDpAsState(
        targetValue = columnWidth * tile.spanX + spacing * (tile.spanX - 1),
        animationSpec = settleSpec,
        label = "tileW",
    )
    val animatedHeight by animateDpAsState(
        targetValue = rowHeight * tile.spanY + spacing * (tile.spanY - 1),
        animationSpec = settleSpec,
        label = "tileH",
    )

    val x = baseX + with(density) { drag.x.toDp() }
    val y = baseY + with(density) { drag.y.toDp() }
    // Pendant le redimensionnement, la taille suit le doigt sans animation ; au relâchement
    // elle se recale en douceur.
    val width = if (previewSpan != null) columnWidth * spanX + spacing * (spanX - 1) else animatedWidth
    val height = if (previewSpan != null) rowHeight * spanY + spacing * (spanY - 1) else animatedHeight

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width = width, height = height),
    ) {
        // Aperçu réel : la tuile est remesurée, donc son contenu se réorganise.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shapes.tile)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, colors.accent, shapes.tile)
                    } else {
                        Modifier
                    },
                ),
        ) {
            EntityTile(
                model = tile,
                onToggle = {},
                onLongClick = {},
                onLevelChange = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Surcouche de gestes : elle capte tout, la tuile dessous reste passive.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(tile.id, columns, columnWidth, rowHeight) {
                    detectDragGestures(
                        onDragStart = { onSelect(tile.id) },
                        onDragEnd = {
                            val dCol = (drag.x / cellWidthPx).roundToInt()
                            val dRow = (drag.y / cellHeightPx).roundToInt()
                            drag = Offset.Zero
                            onMove(
                                (col + dCol).coerceIn(0, (columns - tile.spanX).coerceAtLeast(0)),
                                (row + dRow).coerceAtLeast(0),
                            )
                        },
                        onDragCancel = { drag = Offset.Zero },
                    ) { change, amount ->
                        drag += amount
                        change.consume()
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(tile.id) },
                ),
        )

        if (selected) {
            // Retrait et changement de thème, réservés aux cases décoratives pour le thème.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val widgetKind = tile.category.toWidgetKind()
                if (widgetKind != null && widgetKind.themes.size > 1) {
                    SmallAction(
                        text = stringResource(
                            widgetKind.themeLabelRes(widgetKind.coerceTheme(tile.widgetTheme ?: widgetKind.defaultTheme)),
                        ),
                        onClick = onCycleTheme,
                    )
                }
                SmallAction(text = "✕", onClick = onRemove)
            }

            // Poignée de redimensionnement : glisser pour changer les deux portées.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.accent)
                    .pointerInput(tile.id, columns) {
                        detectDragGestures(
                            onDragStart = {
                                resize = Offset.Zero
                                previewSpan = tile.spanX to tile.spanY
                            },
                            onDragEnd = {
                                val next = previewSpan
                                previewSpan = null
                                resize = Offset.Zero
                                if (next != null) onResize(next.first, next.second)
                            },
                            onDragCancel = {
                                previewSpan = null
                                resize = Offset.Zero
                            },
                        ) { change, amount ->
                            resize += amount
                            val maxSpanX = (columns - col).coerceAtLeast(1)
                            val sx = (tile.spanX + (resize.x / cellWidthPx).roundToInt())
                                .coerceIn(1, maxSpanX)
                            val sy = (tile.spanY + (resize.y / cellHeightPx).roundToInt())
                                .coerceAtLeast(1)
                            previewSpan = sx to sy
                            change.consume()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Icône centrée dans le disque : un glyphe texte n'avait pas un centrage fiable.
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = stringResource(R.string.customize_resize),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    Box(
        modifier = Modifier
            .clip(shapes.pill)
            .background(colors.chipBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = GlassTheme.typography.caption, color = colors.screenTextPrimary)
    }
}

@Composable
private fun AddChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    Row(
        modifier = Modifier
            .clip(shapes.pill)
            .background(colors.accent.copy(alpha = 0.16f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = GlassTheme.typography.caption, color = colors.screenTextPrimary)
    }
}

@Composable
private fun AvailableRow(name: String, detail: String, onAdd: () -> Unit) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = typography.tileName,
                color = colors.screenTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.customize_add),
            tint = colors.accent,
            modifier = Modifier
                .size(40.dp)
                .clip(GlassTheme.shapes.pill)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAdd,
                )
                .padding(10.dp),
        )
    }
}

@Composable
private fun WidgetKind.iconVector(): ImageVector = when (this) {
    WidgetKind.Clock -> Icons.Rounded.Schedule
    WidgetKind.Photo -> Icons.Rounded.Image
    WidgetKind.Calendar -> Icons.Rounded.CalendarMonth
    WidgetKind.Title -> Icons.Rounded.Title
}

private fun WidgetKind.labelRes(): Int = when (this) {
    WidgetKind.Clock -> R.string.widget_clock
    WidgetKind.Photo -> R.string.widget_photo
    WidgetKind.Calendar -> R.string.widget_calendar
    WidgetKind.Title -> R.string.widget_title
}

private fun WidgetKind.themeLabelRes(theme: String): Int = when (theme) {
    WidgetKind.CLOCK_CLASSIC -> R.string.theme_clock_classic
    WidgetKind.CLOCK_MINIMAL -> R.string.theme_clock_minimal
    WidgetKind.CLOCK_BOLD -> R.string.theme_clock_bold
    WidgetKind.PHOTO_COVER -> R.string.theme_photo_cover
    WidgetKind.PHOTO_SOFT -> R.string.theme_photo_soft
    WidgetKind.CALENDAR_WEEK -> R.string.theme_calendar_week
    WidgetKind.CALENDAR_FOCUS -> R.string.theme_calendar_focus
    WidgetKind.TITLE_PLAIN -> R.string.widget_title
    else -> R.string.theme_calendar_minimal
}
