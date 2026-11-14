package com.homeglass.app.ui.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.homeglass.app.R
import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.domain.dashboard.CustomSection
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.ui.home.GlassPillButton
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Éditeur du tableau de bord personnel : zones nommées, appareils ajoutés et réordonnés
 * à la main (glisser-déposer), recherche dans les appareils disponibles.
 *
 * La sélection est enregistrée sous forme de zones ; un tableau vide rend la main au tableau
 * généré automatiquement depuis Home Assistant.
 */
@Composable
fun CustomDashboardScreen(
    states: Map<String, EntityState>,
    availableEntityIds: Set<String>,
    dashboard: CustomDashboard,
    onSave: (CustomDashboard) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography
    val dimens = GlassTheme.dimens

    var sections by remember(dashboard) {
        mutableStateOf(
            dashboard.sections.ifEmpty { listOf(CustomSection(id = NEW_SECTION_ID)) },
        )
    }
    var targetId by remember { mutableStateOf(sections.firstOrNull()?.id) }
    var query by remember { mutableStateOf("") }

    fun label(entityId: String): String =
        states[entityId]?.friendlyName?.takeIf { it.isNotBlank() } ?: entityId

    fun updateSection(id: String, transform: (CustomSection) -> CustomSection) {
        sections = sections.map { section -> if (section.id == id) transform(section) else section }
    }

    fun moveSection(id: String, delta: Int) {
        val index = sections.indexOfFirst { it.id == id }
        val target = (index + delta).coerceIn(0, sections.lastIndex)
        if (index < 0 || target == index) return
        sections = sections.toMutableList().apply { add(target, removeAt(index)) }
    }

    val assigned = sections.flatMap { it.entityIds }.toSet()
    val remaining = states.values
        .map { it.entityId }
        .filter { it in availableEntityIds }
        .filter { it !in assigned }
        .filter { entityId ->
            query.isBlank() ||
                label(entityId).contains(query, ignoreCase = true) ||
                entityId.contains(query, ignoreCase = true)
        }
        .sortedBy { label(it).lowercase() }

    val targetSection = sections.firstOrNull { it.id == targetId } ?: sections.firstOrNull()
    val newZoneTitle = stringResource(R.string.customize_zone_new)

    // Fond plein : l'éditeur se lit sur sa propre surface, les tuiles de l'accueil ne
    // transparaissent pas derrière les champs de saisie.
    Box(modifier.fillMaxSize().background(Color(0xFF0C0C0E))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = dimens.screenPadding)
                .padding(top = 12.dp, bottom = 20.dp),
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
                text = stringResource(R.string.customize_subtitle),
                style = typography.caption,
                color = colors.screenTextSecondary,
            )

            Spacer(Modifier.height(12.dp))

            // Zones : chaque carte se sélectionne (cible des ajouts) et se réorganise.
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sections.forEachIndexed { index, section ->
                    ZoneCard(
                        section = section,
                        selected = section.id == targetSection?.id,
                        canMoveUp = index > 0,
                        canMoveDown = index < sections.lastIndex,
                        label = ::label,
                        onSelect = { targetId = section.id },
                        onTitleChange = { title -> updateSection(section.id) { it.copy(title = title) } },
                        onMoveUp = { moveSection(section.id, -1) },
                        onMoveDown = { moveSection(section.id, 1) },
                        onDelete = {
                            sections = sections - section
                            if (targetId == section.id) targetId = sections.firstOrNull()?.id
                        },
                        onReorder = { from, to ->
                            updateSection(section.id) { current ->
                                current.copy(
                                    entityIds = current.entityIds.toMutableList()
                                        .apply { add(to, removeAt(from)) },
                                )
                            }
                        },
                        onRemoveEntity = { entityId ->
                            updateSection(section.id) { it.copy(entityIds = it.entityIds - entityId) }
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(shapes.pill)
                        .background(colors.chipBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val id = "zone_${System.currentTimeMillis()}"
                            sections = sections + CustomSection(id = id, title = newZoneTitle)
                            targetId = id
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.customize_zone_add),
                            style = typography.tileName,
                            color = colors.screenTextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(
                    R.string.customize_target,
                    targetSection?.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.customize_target_none),
                ),
                style = typography.tileName,
                color = colors.screenTextPrimary,
            )
            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.weight(0.9f).fillMaxWidth()) {
                items(remaining, key = { it }) { entityId ->
                    AvailableRow(
                        name = label(entityId),
                        detail = entityId,
                        onAdd = {
                            val target = targetSection ?: return@AvailableRow
                            updateSection(target.id) {
                                it.copy(entityIds = it.entityIds + entityId)
                            }
                        },
                    )
                }
                if (remaining.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.customize_empty),
                            style = typography.caption,
                            color = colors.screenTextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPillButton(
                    text = stringResource(R.string.customize_save),
                    onClick = {
                        onSave(CustomDashboard(sections).normalized())
                    },
                )
                GlassPillButton(
                    text = stringResource(R.string.customize_reset),
                    onClick = { onSave(CustomDashboard()) },
                )
            }
        }
    }
}

/** Carte d'une zone : titre éditable, actions de zone et appareils réorganisables. */
@Composable
private fun ZoneCard(
    section: CustomSection,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    label: (String) -> String,
    onSelect: () -> Unit,
    onTitleChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onRemoveEntity: (String) -> Unit,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.tile)
            .background(colors.chipBackground)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) colors.accent else Color.Transparent,
                shape = shapes.tile,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (section.title.isEmpty()) {
                    Text(
                        text = stringResource(R.string.customize_zone_name_hint),
                        style = typography.tileName,
                        color = colors.screenTextSecondary,
                    )
                }
                BasicTextField(
                    value = section.title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = typography.tileName.copy(color = colors.screenTextPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconAction(Icons.Rounded.ArrowUpward, stringResource(R.string.customize_zone_up), canMoveUp, onMoveUp)
            IconAction(Icons.Rounded.ArrowDownward, stringResource(R.string.customize_zone_down), canMoveDown, onMoveDown)
            IconAction(Icons.Rounded.DeleteOutline, stringResource(R.string.customize_zone_delete), true, onDelete)
        }

        Spacer(Modifier.height(6.dp))

        if (section.entityIds.isEmpty()) {
            Text(
                text = stringResource(R.string.customize_zone_empty),
                style = typography.caption,
                color = colors.screenTextSecondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            ReorderableRows(
                entityIds = section.entityIds,
                label = label,
                onReorder = onReorder,
                onRemove = onRemoveEntity,
            )
        }
    }
}

/**
 * Liste d'appareils réordonnable au doigt.
 *
 * Chaque ligne garde une hauteur fixe : le déplacement se traduit directement en nombre de
 * rangées franchies, sans mesurer la liste. Le glissement se fait par la poignée, pour ne pas
 * voler le défilement de l'éditeur.
 */
@Composable
private fun ReorderableRows(
    entityIds: List<String>,
    label: (String) -> String,
    onReorder: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography
    val rowHeight = 52.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    val latestIds by rememberUpdatedState(entityIds)
    val latestReorder by rememberUpdatedState(onReorder)
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column {
        // `key` garde une identité stable à chaque ligne : sans elle, un déplacement de liste
        // réaffecte le geste en cours à un autre appareil et le glissement s'interrompt.
        entityIds.forEach { entityId ->
            key(entityId) {
            val dragging = entityId == draggedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Poignée de glissement : elle consomme le toucher dès l'appui, sinon le
                // défilement de l'éditeur récupère le geste vertical et rien ne bouge.
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.customize_drag),
                    tint = colors.screenTextSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(entityId) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                draggedId = entityId
                                dragOffset = 0f
                                drag(down.id) { change ->
                                    dragOffset += change.positionChange().y
                                    change.consume()
                                    val step = (dragOffset / rowPx).toInt()
                                    if (step != 0) {
                                        val ids = latestIds
                                        val from = ids.indexOf(entityId)
                                        val to = (from + step).coerceIn(0, ids.lastIndex)
                                        if (from >= 0 && to != from) {
                                            latestReorder(from, to)
                                            dragOffset -= (to - from) * rowPx
                                        }
                                    }
                                }
                                draggedId = null
                                dragOffset = 0f
                            }
                        },
                )
                Text(
                    text = label(entityId),
                    style = typography.tileName,
                    color = if (dragging) colors.accent else colors.screenTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconAction(
                    icon = Icons.Rounded.Close,
                    description = stringResource(R.string.customize_remove),
                    enabled = true,
                ) { onRemove(entityId) }
            }
            }
        }
    }
}

@Composable
private fun AvailableRow(name: String, detail: String, onAdd: () -> Unit) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
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
            Text(
                text = detail,
                style = typography.caption,
                color = colors.screenTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconAction(
            icon = Icons.Rounded.Add,
            description = stringResource(R.string.customize_add),
            enabled = true,
        ) { onAdd() }
    }
}

/** Petit bouton d'icône, zone tactile suffisante pour un usage au doigt. */
@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = GlassTheme.colors

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(GlassTheme.shapes.pill)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colors.screenTextPrimary else colors.screenTextSecondary.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val NEW_SECTION_ID = "zone_accueil"
