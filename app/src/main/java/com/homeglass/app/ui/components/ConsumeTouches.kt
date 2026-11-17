package com.homeglass.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Rend un panneau plein écran prioritaire pour le toucher.
 *
 * Les panneaux superposés (éditeur, Web Player) sont composés au-dessus du tableau, mais
 * leurs zones vides n'ont pas de gestionnaire de toucher : un appui y traversait jusqu'aux
 * cases du tableau en dessous et pouvait, par exemple, éteindre une lumière. Ce
 * modificateur fait du panneau une cible pour tout l'écran ; les enfants (boutons, listes,
 * GeckoView) gardent la priorité et ne sont pas gênés.
 */
fun Modifier.consumeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { change ->
                if (!change.isConsumed) change.consume()
            }
        }
    }
}
