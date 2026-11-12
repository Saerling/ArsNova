package com.arsnova.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.arsnova.R
import com.arsnova.ui.mochy.RoundedHighlight

/**
 * Paleta monocroma de Mochy Music (negro, grises y blanco; nada de color de marca): el color lo pone
 * la tinta de tus notas. En claro son los mismos escalones invertidos.
 */
object Ars {
    var dark by mutableStateOf(false)
    val Bg get() = if (dark) Color(0xFF0E0E10) else Color(0xFFF6F6F8)
    /** Tarjetas, campos y filas. */
    val Card get() = if (dark) Color(0xFF141416) else Color(0xFFFFFFFF)
    val CardHigh get() = if (dark) Color(0xFF1E1E21) else Color(0xFFE9E9EE)
    /** Hojas y menús. */
    val Sheet get() = if (dark) Color(0xFF161619) else Color(0xFFFFFFFF)
    /** Cristal de las barras flotantes sobre el lienzo. */
    val Glass get() = if (dark) Color(0xF2171719) else Color(0xF5FFFFFF)
    val GlassBorder get() = if (dark) Color(0x14FFFFFF) else Color(0x14000000)
    val Text get() = if (dark) Color.White else Color(0xFF111114)
    val Muted get() = if (dark) Color(0xFF9A9AA0) else Color(0xFF6B6B73)
    /** Botones redondos y píldoras. */
    val PillFill get() = if (dark) Color(0xFF2C2C2E) else Color(0x12000000)
    val Line get() = if (dark) Color(0xFF232326) else Color(0x1A000000)
    val Highlight get() = if (dark) Color(0x1FFFFFFF) else Color(0x14000000)
    /** Mesa bajo las hojas del cuaderno. */
    val Desk get() = if (dark) 0xFF0A0A0B.toInt() else 0xFFECECEF.toInt()
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)

/** Rojo de las acciones destructivas y de la grabación (el único acento de la interfaz). */
val Brand = Color(0xFFE5484D)
val Ink = Color(0xFF111114)
val Paper = Color(0xFFF6F6F8)

val PenColors = listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFFD9481F, 0xFF1F8A5B, 0xFF8E44AD, 0xFFF2B705).map { it.toInt() }
val CoverColors = listOf(0xFF1C1C1E, 0xFF3A3A3C, 0xFF2D5BD9, 0xFF1F8A5B, 0xFFB38B59, 0xFF8E44AD, 0xFFD9481F).map { it.toInt() }

private fun type(): Typography {
    val d = Typography()
    fun TextStyle.inter(w: FontWeight) = copy(fontFamily = Inter, fontWeight = w, letterSpacing = (-0.02).em)
    return Typography(
        displayLarge = d.displayLarge.inter(FontWeight.Bold), displayMedium = d.displayMedium.inter(FontWeight.Bold), displaySmall = d.displaySmall.inter(FontWeight.Bold),
        headlineLarge = d.headlineLarge.inter(FontWeight.Bold).copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.035).em),
        headlineMedium = d.headlineMedium.inter(FontWeight.Bold), headlineSmall = d.headlineSmall.inter(FontWeight.SemiBold),
        titleLarge = d.titleLarge.inter(FontWeight.SemiBold), titleMedium = d.titleMedium.inter(FontWeight.SemiBold).copy(fontSize = 15.sp), titleSmall = d.titleSmall.inter(FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.inter(FontWeight.Medium).copy(fontSize = 15.sp, lineHeight = 22.sp), bodyMedium = d.bodyMedium.inter(FontWeight.Medium), bodySmall = d.bodySmall.inter(FontWeight.Medium),
        labelLarge = d.labelLarge.inter(FontWeight.SemiBold), labelMedium = d.labelMedium.inter(FontWeight.SemiBold), labelSmall = d.labelSmall.inter(FontWeight.SemiBold),
    )
}
private val ArsType by lazy { type() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArsTheme(theme: Int = 0, content: @Composable () -> Unit) {
    val dark = theme == 2 || (theme == 0 && isSystemInDarkTheme())
    Ars.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Color.White, onPrimary = Color.Black, secondary = Color.White, onSecondary = Color.Black, tertiary = Color.White,
            primaryContainer = Color(0xFF2C2C2E), onPrimaryContainer = Color.White, secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
            background = Ars.Bg, onBackground = Color.White, surface = Ars.Card, onSurface = Color.White, surfaceVariant = Color(0xFF1E1E21), onSurfaceVariant = Color(0xFF9A9AA0),
            surfaceContainerLowest = Color(0xFF0B0B0C), surfaceContainerLow = Color(0xFF141416), surfaceContainer = Color(0xFF161619), surfaceContainerHigh = Color(0xFF1E1E21), surfaceContainerHighest = Color(0xFF2C2C2E),
            outline = Color(0xFF9A9AA0), outlineVariant = Color(0xFF232326), error = Brand, surfaceTint = Color.Transparent, inverseSurface = Color.White, inverseOnSurface = Color.Black,
        ) else lightColorScheme(
            primary = Ink, onPrimary = Color.White, secondary = Ink, onSecondary = Color.White, tertiary = Ink,
            primaryContainer = Color(0xFFE4E4EA), onPrimaryContainer = Ink, secondaryContainer = Color(0xFFE4E4EA), onSecondaryContainer = Ink,
            background = Ars.Bg, onBackground = Ink, surface = Ars.Card, onSurface = Ink, surfaceVariant = Color(0xFFE9E9EE), onSurfaceVariant = Color(0xFF6B6B73),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFB), surfaceContainer = Color(0xFFF2F2F5), surfaceContainerHigh = Color(0xFFE9E9EE), surfaceContainerHighest = Color(0xFFE1E1E7),
            outline = Color(0xFF6B6B73), outlineVariant = Color(0x1A000000), error = Brand, surfaceTint = Color.Transparent, inverseSurface = Ink, inverseOnSurface = Color.White,
        ),
        typography = ArsType,
    ) {
        // Como en Mochy: sin onda de Material; lo pulsado se resalta con una caja redondeada suave.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalRippleConfiguration provides null,
            LocalIndication provides RoundedHighlight(14.dp, color = Ars.Highlight),
            content = content,
        )
    }
}
