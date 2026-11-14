package com.homeglass.app.domain.layout

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Résultat d'analyse d'un document de mise en page : jamais d'exception qui remonte. */
sealed interface LayoutParseResult {
    data class Supported(val layout: Layout) : LayoutParseResult

    /** Le document vient d'une version plus récente de l'add-on que celle que l'app gère. */
    data class TooNew(val schemaVersion: Int) : LayoutParseResult

    data class Invalid(val reason: String) : LayoutParseResult
}

/**
 * Parseur tolérant du contrat v1.
 *
 * - `ignoreUnknownKeys` : un champ ajouté côté serveur ne casse pas l'app ;
 * - `coerceInputValues` : `null` sur un champ non nullable et valeur d'énumération inconnue
 *   retombent sur la valeur par défaut ;
 * - la version est lue **avant** le décodage complet, pour distinguer « trop récent » de
 *   « corrompu » et garder le cache dans le premier cas ;
 * - le décodage des items de section est individuel : un item illisible est ignoré, les
 *   autres restent affichables.
 */
object LayoutParser {

    /** Version de schéma gérée par cette application. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = true
        isLenient = false
    }

    fun parse(raw: String): LayoutParseResult {
        if (raw.isBlank()) return LayoutParseResult.Invalid("document vide")

        val element = try {
            json.parseToJsonElement(raw)
        } catch (error: SerializationException) {
            return LayoutParseResult.Invalid("JSON illisible : ${error.message.orEmpty()}")
        } catch (error: IllegalArgumentException) {
            return LayoutParseResult.Invalid("JSON illisible : ${error.message.orEmpty()}")
        }

        if (element !is JsonObject) return LayoutParseResult.Invalid("objet JSON attendu")

        val version = element["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: SUPPORTED_SCHEMA_VERSION
        if (version > SUPPORTED_SCHEMA_VERSION) return LayoutParseResult.TooNew(version)

        return try {
            val layout = json.decodeFromJsonElement(Layout.serializer(), dropObsoleteTabs(element))
            LayoutParseResult.Supported(layout)
        } catch (error: SerializationException) {
            LayoutParseResult.Invalid(error.message ?: "structure invalide")
        } catch (error: IllegalArgumentException) {
            LayoutParseResult.Invalid(error.message ?: "structure invalide")
        }
    }

    /**
     * L'onglet « Automatisations » a été abandonné côté application. Un document encore produit
     * par une version antérieure de l'add-on est nettoyé avant décodage : sans cela, le type
     * inconnu retomberait sur « Maison » et créerait un onglet fantôme.
     */
    private fun dropObsoleteTabs(element: JsonObject): JsonObject {
        val tabs = element["tabs"] as? JsonArray ?: return element
        val kept = tabs.filterNot { tab ->
            val type = ((tab as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull
            type == OBSOLETE_TAB_TYPE
        }
        if (kept.size == tabs.size) return element
        return JsonObject(element.toMutableMap().apply { put("tabs", JsonArray(kept)) })
    }

    /** Type d'onglet retiré du contrat côté client. */
    private const val OBSOLETE_TAB_TYPE = "automations"

    /**
     * Décode les items d'une section selon son `kind`.
     * Un item illisible est ignoré ; le reste de la section est conservé.
     */
    fun decodeItems(section: Section): List<SectionItem> = section.items.mapNotNull { element ->
        runCatching {
            if (section.kind == Section.KIND_SCENES) {
                SectionItem.Scene(json.decodeFromJsonElement(SceneItem.serializer(), element))
            } else {
                SectionItem.Tile(json.decodeFromJsonElement(TileItem.serializer(), element))
            }
        }.getOrNull()
    }
}
