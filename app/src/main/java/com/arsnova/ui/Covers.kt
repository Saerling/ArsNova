package com.arsnova.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.sin

/** Píldora translúcida (vidrio) usada en todo el sistema. */
@Composable
fun Pill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.65f))) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

val PatternNames = listOf("Sin portada", "Puntos", "Rayas", "Cuadros", "Ondas", "Confeti", "Arcos", "Estrellas")

private fun light(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f)

/** Portadas con patrón generadas (estilo Samsung Notes). */
@Composable
fun CoverArt(color: Int, pattern: Int, modifier: Modifier) = Canvas(modifier) {
    val c = Color(color); val a = light(c, 0.55f); val w = size.width; val h = size.height; val u = w / 8
    drawRect(c)
    when (pattern) {
        1 -> { var y = u / 2; var row = 0; while (y < h) { var x = if (row % 2 == 0) u / 2 else u; while (x < w) { drawCircle(a, u * 0.18f, Offset(x, y)); x += u }; y += u * 0.8f; row++ } }
        2 -> { var x = -h; while (x < w) { drawLine(a, Offset(x, h), Offset(x + h, 0f), u * 0.35f); x += u } }
        3 -> { var y = 0f; var r = 0; while (y < h) { var x = if (r % 2 == 0) 0f else u; while (x < w) { drawRect(a, Offset(x, y), Size(u, u)); x += 2 * u }; y += u; r++ } }
        4 -> { var y = u; while (y < h) { val p = Path(); p.moveTo(0f, y); var x = 0f; while (x <= w) { p.lineTo(x, y + sin(x / u * 1.6f) * u * 0.3f); x += 4f }; drawPath(p, a, style = Stroke(u * 0.22f)); y += u * 0.9f } }
        5 -> { val rnd = java.util.Random(color.toLong()); repeat(40) { val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
                if (rnd.nextBoolean()) drawCircle(a, u * (0.1f + rnd.nextFloat() * 0.25f), Offset(x, y)) else drawRect(light(c, 0.8f), Offset(x, y), Size(u * 0.25f, u * 0.6f)) } }
        6 -> { var y = u; var r = 0; while (y < h + u) { var x = if (r % 2 == 0) 0f else u; while (x < w + u) { drawArc(a, 180f, 180f, false, Offset(x - u * 0.8f, y - u * 0.8f), Size(u * 1.6f, u * 1.6f), style = Stroke(u * 0.25f)); x += 2 * u }; y += u * 1.2f; r++ } }
        7 -> { val rnd = java.util.Random(7L + color); repeat(14) { val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val s = u * (0.25f + rnd.nextFloat() * 0.3f)
                val p = Path(); for (i in 0 until 10) { val ang = Math.PI / 5 * i - Math.PI / 2; val rr = if (i % 2 == 0) s else s * 0.45f
                    val px = x + (kotlin.math.cos(ang) * rr).toFloat(); val py = y + (kotlin.math.sin(ang) * rr).toFloat(); if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }; p.close(); drawPath(p, a) } }
    }
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), 0f, w * 0.12f))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(w * 0.085f, 0f), Offset(w * 0.085f, h), 1.5f)
}

/** Fondo editorial cálido con luces ambientales suaves (para que el vidrio tenga algo detrás). */
@Composable
fun Ambient(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bg = MaterialTheme.colorScheme.background; val dark = bg.luminance() < 0.3f
    androidx.compose.foundation.layout.Box(modifier.background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Brand.copy(alpha = if (dark) 0.16f else 0.10f), Color.Transparent), Offset(size.width * 0.92f, size.height * 0.04f), size.width * 0.55f), size.width * 0.55f, Offset(size.width * 0.92f, size.height * 0.04f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2D5BD9).copy(alpha = if (dark) 0.14f else 0.07f), Color.Transparent), Offset(0f, size.height * 0.85f), size.width * 0.6f), size.width * 0.6f, Offset(0f, size.height * 0.85f))
        }
        content()
    }
}
/** Grupo de ajustes estilo iOS: tarjeta de vidrio redondeada. */
@Composable
fun Group(content: @Composable () -> Unit) {
    Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.3f) 0.06f else 0.6f))) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent)) { androidx.compose.foundation.layout.Column { content() } }
    }
}
