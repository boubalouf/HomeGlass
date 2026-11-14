package com.homeglass.app.data.ha

import android.content.Context
import com.homeglass.app.R
import com.homeglass.app.data.layout.LayoutSource
import com.homeglass.app.domain.layout.Layout
import com.homeglass.app.domain.layout.LayoutGenerator
import com.homeglass.app.domain.layout.LayoutLabels
import com.homeglass.app.domain.layout.LayoutParser

/**
 * Source de mise en page « locale » tant que l'add-on n'est pas branché (étape 4B) :
 * génère un document au format du contrat v1 à partir des registres Home Assistant.
 *
 * Le résultat est sérialisé en JSON et repasse par `LayoutRepository` : le pipeline, les
 * mappers et l'écran Maison restent identiques à ceux de l'add-on.
 */
class GeneratedLayoutSource(
    private val context: Context,
    private val realtime: HaRealtime,
) : LayoutSource {

    override suspend fun load(): String? {
        val registry = realtime.registry.value
        val states = realtime.states.value
        if (registry.isEmpty && states.isEmpty()) return null

        val layout = LayoutGenerator.generate(
            registry = registry,
            states = states,
            labels = labels(),
            revision = (System.currentTimeMillis() / 1000L).toInt(),
        )
        return LayoutParser.json.encodeToString(Layout.serializer(), layout)
    }

    private fun labels(): LayoutLabels = LayoutLabels(
        home = context.getString(R.string.nav_home),
        rooms = context.getString(R.string.nav_rooms),
        settings = context.getString(R.string.nav_settings),
        scenes = context.getString(R.string.section_scenes),
        lights = context.getString(R.string.section_lights),
        climate = context.getString(R.string.section_climate),
        security = context.getString(R.string.section_security),
        covers = context.getString(R.string.section_covers),
        media = context.getString(R.string.section_media),
        cameras = context.getString(R.string.section_cameras),
        switches = context.getString(R.string.section_switches),
        sensors = context.getString(R.string.section_sensors),
        cleaning = context.getString(R.string.section_cleaning),
        other = context.getString(R.string.section_other),
    )
}
