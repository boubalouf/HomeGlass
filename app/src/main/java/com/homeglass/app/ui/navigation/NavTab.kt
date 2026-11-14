package com.homeglass.app.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.homeglass.app.domain.layout.TabType

/**
 * Onglet de la barre de navigation, généré depuis `layout.tabs`.
 * Le libellé vient de la configuration (donc de l'add-on), pas des ressources : renommer un
 * onglet dans l'éditeur change la barre sans reconstruire l'app.
 */
@Immutable
data class NavTabUi(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val type: TabType = TabType.Home,
)
