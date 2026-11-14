package com.homeglass.app.domain.dashboard

import kotlinx.serialization.Serializable

/**
 * Zone du tableau de bord personnel : un titre libre et les appareils qu'elle contient.
 *
 * Une zone sans titre s'affiche sans en-tête (comportement historique, une seule grille).
 * L'identifiant est stable : il sert de clé à l'éditeur et au rendu.
 */
@Serializable
data class CustomSection(
    val id: String,
    val title: String = "",
    val entityIds: List<String> = emptyList(),
)

/**
 * Tableau de bord personnel enregistré sur la tablette.
 *
 * Vide ([isConfigured] faux) : l'accueil reste celui généré depuis Home Assistant.
 * Aucune référence à Android ici : le modèle reste testable sur JVM.
 */
@Serializable
data class CustomDashboard(
    val sections: List<CustomSection> = emptyList(),
) {
    val isConfigured: Boolean get() = sections.any { it.entityIds.isNotEmpty() }

    /**
     * Version assainie : identifiants uniques (une entité n'apparaît qu'une fois, dans sa
     * première zone), zones vides retirées, titres espacés nettoyés.
     */
    fun normalized(): CustomDashboard {
        val seen = mutableSetOf<String>()
        val cleaned = sections.mapNotNull { section ->
            val ids = section.entityIds
                .filter { it.isNotBlank() }
                .filter(seen::add)
            if (ids.isEmpty()) null else section.copy(title = section.title.trim(), entityIds = ids)
        }
        return CustomDashboard(cleaned.distinctBy { it.id })
    }

    companion object {
        /** Migration de l'ancien format : une liste d'appareils sans zone. */
        fun single(entityIds: List<String>, id: String = DEFAULT_SECTION_ID): CustomDashboard =
            CustomDashboard(listOf(CustomSection(id = id, entityIds = entityIds)))

        const val DEFAULT_SECTION_ID = "zone_accueil"
    }
}
