package com.homeglass.app.ui.media

import com.homeglass.app.domain.ha.EntityState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTriggerTest {

    private fun media(id: String, state: String, title: String? = null) = EntityState(
        entityId = id,
        state = state,
        attributes = buildMap {
            if (title != null) put("media_title", JsonPrimitive(title))
        },
    )

    private fun snapshot(vararg entities: EntityState): Map<String, EntityState> =
        entities.associateBy { it.entityId }

    @Test
    fun `premier releve n ouvre rien meme si la musique tourne deja`() {
        val states = snapshot(media("media_player.salon", "playing", "Titre"))

        assertNull(startedPlayback(states, previous = null))
    }

    @Test
    fun `un appareil qui commence a jouer ouvre le lecteur`() {
        val states = snapshot(media("media_player.salon", "playing", "Titre"))
        val previous = snapshot(media("media_player.salon", "idle"))

        assertEquals("media_player.salon", startedPlayback(states, previous))
    }

    @Test
    fun `un appareil eteint qui demarre ouvre le lecteur`() {
        val states = snapshot(media("media_player.salon", "playing", "Titre"))
        val previous = snapshot(media("media_player.salon", "off"))

        assertEquals("media_player.salon", startedPlayback(states, previous))
    }

    @Test
    fun `reprendre apres une pause n ouvre pas le lecteur`() {
        val states = snapshot(media("media_player.salon", "playing", "Titre"))
        val previous = snapshot(media("media_player.salon", "paused", "Titre"))

        assertNull(startedPlayback(states, previous))
    }

    @Test
    fun `un changement de piste n ouvre pas le lecteur`() {
        val states = snapshot(media("media_player.salon", "playing", "Titre suivant"))
        val previous = snapshot(media("media_player.salon", "playing", "Titre précédent"))

        assertNull(startedPlayback(states, previous))
    }

    @Test
    fun `une entite hors media_player est ignoree`() {
        val states = snapshot(EntityState(entityId = "switch.tv", state = "playing"))
        val previous = snapshot(EntityState(entityId = "switch.tv", state = "off"))

        assertNull(startedPlayback(states, previous))
    }

    @Test
    fun `le lecteur avec un titre est prefere`() {
        val states = snapshot(
            media("media_player.a_sans_titre", "playing"),
            media("media_player.b_avec_titre", "playing", "Titre"),
        )
        val previous = snapshot(
            media("media_player.a_sans_titre", "idle"),
            media("media_player.b_avec_titre", "idle"),
        )

        assertEquals("media_player.b_avec_titre", startedPlayback(states, previous))
    }

    @Test
    fun `l ecran reste ouvert en lecture et en pause seulement`() {
        assertTrue(staysOnScreen(media("media_player.salon", "playing")))
        assertTrue(staysOnScreen(media("media_player.salon", "paused")))
        assertFalse(staysOnScreen(media("media_player.salon", "off")))
        assertFalse(staysOnScreen(media("media_player.salon", "unavailable")))
        assertFalse(staysOnScreen(null))
    }
}
