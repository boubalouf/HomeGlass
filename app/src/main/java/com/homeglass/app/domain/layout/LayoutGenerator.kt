package com.homeglass.app.domain.layout

import com.homeglass.app.domain.ha.AreaEntry
import com.homeglass.app.domain.ha.EntityEntry
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.HaRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/** Textes que l'app injecte dans une mise en page générée (tous issus de `strings.xml`). */
data class LayoutLabels(
    val home: String,
    val rooms: String,
    val settings: String,
    val scenes: String,
    val lights: String,
    val climate: String,
    val security: String,
    val covers: String,
    val media: String,
    val cameras: String,
    val switches: String,
    val sensors: String,
    val cleaning: String,
    val other: String,
)

/**
 * Génère un document de mise en page **au format du contrat v1** à partir des registres Home
 * Assistant, tant que l'add-on n'est pas branché (étape 4B).
 *
 * La sortie passe ensuite par le même pipeline que le JSON de l'add-on : elle est sérialisée
 * puis relue par `LayoutRepository`, donc les mappers et l'UI ne changent pas.
 *
 * Règles : pièces = aires, entités désactivées / masquées / de diagnostic exclues, scènes et
 * scripts sur l'accueil en cartes à dégradé, tailles et `display` par défaut selon le domaine.
 */
object LayoutGenerator {

    /** Domaines qui n'ont rien à faire dans une tuile (personnes, mises à jour, outils). */
    private val EXCLUDED_DOMAINS = setOf(
        "person", "zone", "tag", "update", "tts", "stt", "conversation", "event", "button",
        "notify", "device_tracker", "geo_location", "sun", "weather", "calendar", "todo",
        "image", "select", "input_select", "text", "input_text", "number", "input_number",
    )

    private val GRADIENTS = listOf(
        listOf("#FF9500", "#FF375F"),
        listOf("#FFD60A", "#FF9F0A"),
        listOf("#5E5CE6", "#BF5AF2"),
        listOf("#64D2FF", "#5E5CE6"),
        listOf("#30D158", "#64D2FF"),
    )

    private enum class Group { Lights, Climate, Security, Covers, Media, Cameras, Switches, Sensors, Cleaning, Other }

    private data class Candidate(
        val entityId: String,
        val name: String,
        val domain: String,
        val icon: String,
        val areaId: String?,
        val state: EntityState,
    )

    fun generate(
        registry: HaRegistry,
        states: Map<String, EntityState>,
        labels: LayoutLabels,
        revision: Int = 1,
        homeTileLimit: Int = 12,
    ): Layout {
        val entries = registry.entities.associateBy { it.entityId }

        val candidates = states.values
            .asSequence()
            .filter { it.entityId.substringBefore('.') !in EXCLUDED_DOMAINS }
            .mapNotNull { state ->
                val entry = entries[state.entityId]
                if (entry != null && !entry.isVisible) return@mapNotNull null
                val domain = state.domain
                if (groupOf(domain) == null && domain != "scene" && domain != "script") return@mapNotNull null
                Candidate(
                    entityId = state.entityId,
                    name = nameOf(entry, state),
                    domain = domain,
                    icon = iconOf(entry, domain),
                    areaId = registry.areaOf(entry),
                    state = state,
                )
            }
            .sortedWith(compareBy<Candidate>({ priority(it.domain) }, { it.name.lowercase() }))
            .toList()

        val sceneCandidates = candidates.filter { it.domain == "scene" || it.domain == "script" }
        val tileCandidates = candidates.filter { it.domain != "scene" && it.domain != "script" }

        val homeSections = buildList {
            if (sceneCandidates.isNotEmpty()) {
                add(
                    Section(
                        id = "sec_scenes",
                        kind = Section.KIND_SCENES,
                        title = labels.scenes,
                        items = sceneCandidates.mapIndexed { index, candidate ->
                            sceneJson(candidate, index)
                        },
                    ),
                )
            }

            var remaining = homeTileLimit
            Group.entries.forEach { group ->
                if (remaining <= 0) return@forEach
                val inGroup = tileCandidates.filter { groupOf(it.domain) == group }
                if (inGroup.isEmpty()) return@forEach
                val taken = inGroup.take(remaining)
                remaining -= taken.size
                add(
                    Section(
                        id = "sec_${group.name.lowercase()}",
                        kind = Section.KIND_TILES,
                        title = group.title(labels),
                        items = taken.map { tileJson(it) },
                    ),
                )
            }
        }

        val rooms = registry.areas
            .sortedBy { it.name.lowercase() }
            .map { area -> roomFor(area, tileCandidates.filter { it.areaId == area.areaId }) }

        return Layout(
            schemaVersion = LayoutParser.SUPPORTED_SCHEMA_VERSION,
            revision = revision,
            publishedAt = "",
            theme = LayoutTheme(),
            tabs = listOf(
                LayoutTab(id = "home", type = TabType.Home, label = labels.home, icon = "home"),
                LayoutTab(id = "rooms", type = TabType.Rooms, label = labels.rooms, icon = "meeting_room"),
                LayoutTab(id = "settings", type = TabType.Settings, label = labels.settings, icon = "settings"),
            ),
            home = LayoutHome(title = labels.home, sections = homeSections),
            rooms = rooms,
        )
    }

    private fun roomFor(area: AreaEntry, candidates: List<Candidate>): LayoutRoom {
        val sections = if (candidates.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Section(
                    id = "sec_room_${area.areaId}",
                    kind = Section.KIND_TILES,
                    title = "",
                    items = candidates.map { tileJson(it) },
                ),
            )
        }
        return LayoutRoom(
            id = "room_${area.areaId}",
            name = area.name,
            icon = area.icon?.stripIconPrefix() ?: "meeting_room",
            areaId = area.areaId,
            sections = sections,
        )
    }

    private fun tileJson(candidate: Candidate): JsonElement =
        LayoutParser.json.encodeToJsonElement(
            TileItem.serializer(),
            TileItem(
                id = "tile_${candidate.entityId.replace('.', '_')}",
                entityId = candidate.entityId,
                name = candidate.name,
                icon = candidate.icon,
                accent = null,
                size = sizeFor(candidate.domain),
                display = displayFor(candidate.domain),
                hidden = false,
                tap = tapFor(candidate.domain),
                longPress = LayoutAction(type = ActionType.MoreInfo),
            ),
        )

    private fun sceneJson(candidate: Candidate, index: Int): JsonElement =
        LayoutParser.json.encodeToJsonElement(
            SceneItem.serializer(),
            SceneItem(
                id = "scene_${candidate.entityId.replace('.', '_')}",
                name = candidate.name,
                icon = candidate.icon,
                gradient = GRADIENTS[index % GRADIENTS.size],
                actions = listOf(
                    LayoutAction(
                        type = ActionType.CallService,
                        service = "${candidate.domain}.turn_on",
                        target = mapOf("entity_id" to JsonPrimitive(candidate.entityId)),
                    ),
                ),
            ),
        )

    private fun nameOf(entry: EntityEntry?, state: EntityState): String =
        entry?.name?.takeIf { it.isNotBlank() }
            ?: state.friendlyName?.takeIf { it.isNotBlank() }
            ?: entry?.originalName?.takeIf { it.isNotBlank() }
            ?: state.entityId.substringAfter('.')

    private fun iconOf(entry: EntityEntry?, domain: String): String =
        entry?.icon?.takeIf { it.isNotBlank() }?.stripIconPrefix() ?: when (domain) {
            "light" -> "lightbulb"
            "switch", "input_boolean", "fan" -> "toggle_on"
            "climate", "water_heater" -> "thermostat"
            "cover" -> "blinds"
            "lock" -> "lock"
            "alarm_control_panel" -> "shield"
            "media_player" -> "speaker"
            "camera" -> "camera_alt"
            "vacuum" -> "cleaning_services"
            "sensor", "binary_sensor" -> "sensors"
            "scene" -> "auto_awesome"
            "script" -> "bolt"
            else -> "devices_other"
        }

    private fun groupOf(domain: String): Group? = when (domain) {
        "light" -> Group.Lights
        "climate", "water_heater" -> Group.Climate
        "lock", "alarm_control_panel" -> Group.Security
        "cover" -> Group.Covers
        "media_player" -> Group.Media
        "camera" -> Group.Cameras
        "switch", "input_boolean", "fan" -> Group.Switches
        "sensor", "binary_sensor" -> Group.Sensors
        "vacuum" -> Group.Cleaning
        "scene", "script" -> null
        else -> Group.Other
    }

    private fun priority(domain: String): Int = when (domain) {
        "light" -> 0
        "switch", "input_boolean", "fan" -> 1
        "climate", "water_heater" -> 2
        "cover" -> 3
        "lock", "alarm_control_panel" -> 4
        "media_player" -> 5
        "camera" -> 6
        "vacuum" -> 7
        "sensor", "binary_sensor" -> 8
        "scene" -> 9
        "script" -> 10
        else -> 11
    }

    private fun sizeFor(domain: String): TileSize = when (domain) {
        "climate", "media_player", "camera" -> TileSize.Wide
        "cover" -> TileSize.Small
        else -> TileSize.Small
    }

    private fun displayFor(domain: String): Display = when (domain) {
        "light", "cover", "fan" -> Display.Slider
        "climate", "sensor", "binary_sensor" -> Display.Value
        "switch", "input_boolean", "lock", "vacuum", "alarm_control_panel" -> Display.Button
        else -> Display.Auto
    }

    private fun tapFor(domain: String): LayoutAction = when (domain) {
        "light", "switch", "input_boolean", "fan", "cover", "lock", "media_player", "vacuum", "automation" ->
            LayoutAction(type = ActionType.Toggle)

        else -> LayoutAction(type = ActionType.MoreInfo)
    }

    private fun Group.title(labels: LayoutLabels): String = when (this) {
        Group.Lights -> labels.lights
        Group.Climate -> labels.climate
        Group.Security -> labels.security
        Group.Covers -> labels.covers
        Group.Media -> labels.media
        Group.Cameras -> labels.cameras
        Group.Switches -> labels.switches
        Group.Sensors -> labels.sensors
        Group.Cleaning -> labels.cleaning
        Group.Other -> labels.other
    }

    /** Les icônes Home Assistant sont des noms Material Design (`mdi:ceiling-light`). */
    private fun String.stripIconPrefix(): String =
        substringAfter(':', missingDelimiterValue = this).replace('-', '_')
}
