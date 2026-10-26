package com.arsnova.ui

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arsnova.data.Tool
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Puente de teclas del lápiz (doble toque) desde la Activity. */
object StylusKeys {
    var handler: ((KeyEvent) -> Boolean)? = null
    val NATIVE = setOf(308, 309, 310, 311) // KEYCODE_STYLUS_BUTTON_PRIMARY..TAIL (API 34)
}

/** Rueda radial estilo Apple Pencil Pro / Samsung: herramientas fuera, colores dentro. */
@Composable
fun ToolWheel(
    at: Offset, tool: Tool, color: Int,
    onTool: (Tool) -> Unit, onColor: (Int) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onDismiss: () -> Unit,
) {
    val d = LocalDensity.current
    val sizePx = with(d) { 248.dp.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val anim = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
    Box(Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        val x = (at.x - sizePx / 2).coerceIn(0f, (box.width - sizePx).coerceAtLeast(0f))
        val y = (at.y - sizePx / 2).coerceIn(0f, (box.height - sizePx).coerceAtLeast(0f))
        Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.size(248.dp)
            .graphicsLayer { scaleX = anim.value; scaleY = anim.value; alpha = ((anim.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }) {
            Surface(Modifier.fillMaxSize(), shape = CircleShape, shadowElevation = 16.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {}
            val outer: List<Triple<ImageVector, Boolean, () -> Unit>> = listOf(
                Triple(Icons.Outlined.Draw, tool == Tool.PEN) { onTool(Tool.PEN) },
                Triple(Icons.Outlined.BorderColor, tool == Tool.HIGHLIGHTER) { onTool(Tool.HIGHLIGHTER) },
                Triple(EraserIcon, tool == Tool.ERASER) { onTool(Tool.ERASER) },
                Triple(Icons.Outlined.Gesture, tool == Tool.LASSO) { onTool(Tool.LASSO) },
                Triple(Icons.AutoMirrored.Outlined.Redo, false, onRedo),
                Triple(Icons.AutoMirrored.Outlined.Undo, false, onUndo),
            )
            outer.forEachIndexed { i, (icon, on, act) ->
                val a = Math.toRadians(-90.0 + i * 60.0)
                Box(Modifier.align(Alignment.Center).offset((cos(a) * 92).dp, (sin(a) * 92).dp).size(48.dp).clip(CircleShape)
                    .background(if (on) Ink else Color.Transparent).clickable { act() }, contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (on) Color.White else Ink)
                }
            }
            PenColors.forEachIndexed { i, c ->
                val a = Math.toRadians(-60.0 + i * 60.0)
                Box(Modifier.align(Alignment.Center).offset((cos(a) * 46).dp, (sin(a) * 46).dp).size(if (c == color) 28.dp else 22.dp)
                    .clip(CircleShape).background(Color(c)).then(if (c == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onColor(c) })
            }
        }
    }
}
