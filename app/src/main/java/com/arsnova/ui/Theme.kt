package com.arsnova.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Brand = Color(0xFFD9481F)
val Ink = Color(0xFF1D1B18)
val Paper = Color(0xFFF6F3EC)

val PenColors = listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFFD9481F, 0xFF1F8A5B, 0xFF8E44AD, 0xFFF2B705).map { it.toInt() }
val CoverColors = listOf(0xFFD9481F, 0xFF1D1B18, 0xFF2D5BD9, 0xFF1F8A5B, 0xFFB38B59, 0xFF8E44AD).map { it.toInt() }

@Composable
fun ArsTheme(theme: Int = 0, content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (theme == 2 || (theme == 0 && isSystemInDarkTheme())) darkColorScheme(
        primary = Color(0xFFFF7A50), onPrimary = Color.Black, secondary = Color(0xFFEDE6DA), background = Color(0xFF151413),
        surface = Color(0xFF1F1D1B), surfaceContainer = Color(0xFF262321), surfaceContainerHigh = Color(0xFF2E2B28),
        onSurface = Color(0xFFEDE6DA), onBackground = Color(0xFFEDE6DA), outline = Color(0xFF6E675E),
    ) else lightColorScheme(
        primary = Brand, onPrimary = Color.White, secondary = Ink, background = Paper, surface = Color(0xFFFFFCF6),
        surfaceContainer = Color(0xFFF0ECE3), surfaceContainerHigh = Color(0xFFEAE5DA), onSurface = Ink, onBackground = Ink,
        outline = Color(0xFFCBC4B6),
    ),
    typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    ),
    content = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content) },
)
