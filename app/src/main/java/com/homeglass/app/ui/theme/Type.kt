package com.homeglass.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.homeglass.app.R

/** Inter fournie en ressource, en substitut de SF Pro. */
val InterFontFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Échelle typographique du design system.
 * Les tailles sont en sp : le réglage de taille de police système est donc respecté.
 */
@Immutable
data class GlassTypography(
    val largeTitle: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp,
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    val tileName: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.1).sp,
    ),
    val tileStatus: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    ),
    /** Valeur de capteur mise en avant, dans la tuile. */
    val sensorValue: TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
)

/** Mappe l'échelle custom sur Material 3 pour que [androidx.compose.material3.Text] sans style explicite reste en Inter. */
internal fun glassMaterialTypography(typography: GlassTypography): Typography = Typography(
    displayLarge = typography.largeTitle,
    headlineSmall = typography.sectionTitle,
    bodyLarge = typography.tileName,
    bodyMedium = typography.tileStatus,
    labelMedium = typography.caption,
)

val LocalGlassTypography = staticCompositionLocalOf { GlassTypography() }
