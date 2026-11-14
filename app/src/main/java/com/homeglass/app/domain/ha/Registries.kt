package com.homeglass.app.domain.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Pièce (`config/area_registry/list`). */
@Serializable
data class AreaEntry(
    @SerialName("area_id") val areaId: String,
    val name: String = "",
    val icon: String? = null,
    @SerialName("floor_id") val floorId: String? = null,
)

/** Appareil (`config/device_registry/list`) : porte l'aire des entités sans aire propre. */
@Serializable
data class DeviceEntry(
    val id: String = "",
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("name_by_user") val nameByUser: String? = null,
    val name: String? = null,
    @SerialName("disabled_by") val disabledBy: String? = null,
)

/** Entrée du registre des entités (`config/entity_registry/list`). */
@Serializable
data class EntityEntry(
    @SerialName("entity_id") val entityId: String,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val icon: String? = null,
    val platform: String? = null,
    @SerialName("disabled_by") val disabledBy: String? = null,
    @SerialName("hidden_by") val hiddenBy: String? = null,
    @SerialName("entity_category") val entityCategory: String? = null,
) {
    /** Une entité désactivée, masquée ou de diagnostic ne doit pas apparaître dans l'UI. */
    val isVisible: Boolean
        get() = disabledBy == null && hiddenBy == null && entityCategory != CATEGORY_DIAGNOSTIC

    companion object {
        const val CATEGORY_DIAGNOSTIC = "diagnostic"
    }
}

/** Instantané des trois registres. */
data class HaRegistry(
    val areas: List<AreaEntry> = emptyList(),
    val devices: List<DeviceEntry> = emptyList(),
    val entities: List<EntityEntry> = emptyList(),
) {
    val isEmpty: Boolean
        get() = areas.isEmpty() && entities.isEmpty()

    /** Aire d'une entité : la sienne, sinon celle de son appareil. */
    fun areaOf(entity: EntityEntry?): String? {
        entity ?: return null
        return entity.areaId ?: devices.firstOrNull { it.id == entity.deviceId }?.areaId
    }

    companion object {
        /** Décode les listes renvoyées par les commandes de registre Home Assistant. */
        fun areasFromJson(element: JsonElement): List<AreaEntry> =
            objects(element).mapNotNull { obj ->
                val id = obj.string("area_id") ?: return@mapNotNull null
                AreaEntry(areaId = id, name = obj.string("name").orEmpty(), icon = obj.string("icon"))
            }

        fun devicesFromJson(element: JsonElement): List<DeviceEntry> =
            objects(element).mapNotNull { obj ->
                val id = obj.string("id") ?: return@mapNotNull null
                DeviceEntry(
                    id = id,
                    areaId = obj.string("area_id"),
                    nameByUser = obj.string("name_by_user"),
                    name = obj.string("name"),
                    disabledBy = obj.string("disabled_by"),
                )
            }

        fun entitiesFromJson(element: JsonElement): List<EntityEntry> =
            objects(element).mapNotNull { obj ->
                val id = obj.string("entity_id") ?: return@mapNotNull null
                EntityEntry(
                    entityId = id,
                    areaId = obj.string("area_id"),
                    deviceId = obj.string("device_id"),
                    name = obj.string("name"),
                    originalName = obj.string("original_name"),
                    icon = obj.string("icon"),
                    platform = obj.string("platform"),
                    disabledBy = obj.string("disabled_by"),
                    hiddenBy = obj.string("hidden_by"),
                    entityCategory = obj.string("entity_category"),
                )
            }

        private fun objects(element: JsonElement): List<JsonObject> =
            (element as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
