package com.arsnova.ui

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arsnova.data.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Puente de teclas del lápiz (doble toque) desde la Activity. */
object StylusKeys {
    var handler: ((KeyEvent) -> Boolean)? = null
    val NATIVE = setOf(308, 309, 310, 311, 718) // STYLUS_BUTTON_* (API 34) y 718 = doble toque Huawei M-Pencil
}

/**
 * Rueda del lápiz (doble toque): nace bajo la punta. Anillo exterior: herramientas, deshacer y rehacer
 * (tocar el lápiz ya activo lo cambia por los tipos de lápiz, cada uno con su trazo de muestra).
 * Anillo interior: colores. Centro: grosor, que salta entre los tres tamaños guardados.
 */
@Composable
fun ToolWheel(
    at: Offset, slots: List<Slot>, active: Int, swatches: List<Int>, presets: List<Float>, canUndo: Boolean, canRedo: Boolean,
    onSlot: (Int) -> Unit, onPenStyle: (Int) -> Unit, onColor: (Int) -> Unit, onPicker: () -> Unit, onWidth: (Float) -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit, onDismiss: () -> Unit,
) {
    val d = LocalDensity.current; val view = LocalView.current; val scope = rememberCoroutineScope()
    val radius = 152.dp; val rp = with(d) { radius.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var styles by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun close() { if (!closing) { closing = true; scope.launch { open.animateTo(0f, tween(130)); onDismiss() } } }
    val cur = slots.getOrNull(active)
    val penIdx = slots.indexOfFirst { it.tool == Tool.PEN }

    /** Un elemento sobre un anillo: entra deslizándose desde el centro, uno tras otro. */
    @Composable fun OnRing(i: Int, n: Int, r: Dp, start: Float = -90f, content: @Composable () -> Unit) {
        val a = Math.toRadians((start + 360.0 * i / n))
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) { delay(20L + i * 14L); p.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 480f)) }
        Box(Modifier.offset { val k = 0.55f + 0.45f * p.value; IntOffset((cos(a) * r.toPx() * k).roundToInt(), (sin(a) * r.toPx() * k).roundToInt()) }
            .graphicsLayer { alpha = p.value.coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * p.value; scaleY = scaleX }, contentAlignment = Alignment.Center) { content() }
    }
    @Composable fun RingButton(on: Boolean, enabled: Boolean = true, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
        val bg by androidx.compose.animation.animateColorAsState(if (on) Ars.Text else Color.Transparent, tween(160), label = "rb")
        Box(Modifier.size(50.dp).alpha(if (enabled) 1f else 0.3f).bounceClick(enabled, 0.8f, onClick = onClick).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { content(if (on) Ars.Bg else Ars.Text) }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) { detectTapGestures { close() } }) {
        if (box.width == 0) return@Box
        val cx = at.x.coerceIn(rp + 8f, maxOf(box.width - rp - 8f, rp + 8f)); val cy = at.y.coerceIn(rp + 8f, maxOf(box.height - rp - 8f, rp + 8f))
        Surface(Modifier.offset { IntOffset((cx - rp).roundToInt(), (cy - rp).roundToInt()) }.size(radius * 2)
            .graphicsLayer { val k = 0.72f + 0.28f * open.value; scaleX = k; scaleY = k; alpha = open.value.coerceIn(0f, 1f) }.pointerInput(Unit) { detectTapGestures { } },
            shape = CircleShape, color = Ars.Glass, shadowElevation = 20.dp, border = BorderStroke(1.dp, Ars.GlassBorder)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // pista del anillo exterior y aro del centro
                val line = Ars.Line; val track = Ars.Text.copy(alpha = 0.045f)
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(track, 116.dp.toPx(), style = Stroke(58.dp.toPx()))
                    drawCircle(line, 87.dp.toPx(), style = Stroke(1.dp.toPx()))
                }
                AnimatedContent(styles, transitionSpec = { (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), 0.86f)) togetherWith (fadeOut(tween(110)) + scaleOut(targetScale = 1.08f)) },
                    contentAlignment = Alignment.Center, label = "ring") { st ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!st) {
                            val n = slots.size + 2
                            slots.forEachIndexed { i, s -> OnRing(i, n, 116.dp) {
                                RingButton(i == active, onClick = { tick(); if (i == active && s.tool == Tool.PEN) styles = true else { onSlot(i); close() } }) { tint ->
                                    Icon(when (s.tool) { Tool.PEN -> ArsIcons.Pen; Tool.HIGHLIGHTER -> ArsIcons.Highlighter; Tool.ERASER -> ArsIcons.Eraser; Tool.LASSO -> ArsIcons.Lasso; Tool.TAPE -> ArsIcons.Tape; Tool.LASER -> ArsIcons.Laser; else -> ArsIcons.Text }, s.name, tint = tint)
                                    if (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(9.dp).clip(CircleShape).background(Color(s.color).copy(alpha = 1f)).border(1.5.dp, if (i == active) Ars.Text else Ars.Glass.copy(alpha = 1f), CircleShape))
                                } } }
                            OnRing(slots.size, n, 116.dp) { RingButton(false, canRedo, { tick(); onRedo() }) { Icon(ArsIcons.Redo, "Rehacer", tint = it) } }
                            OnRing(slots.size + 1, n, 116.dp) { RingButton(false, canUndo, { tick(); onUndo() }) { Icon(ArsIcons.Undo, "Deshacer", tint = it) } }
                        } else {
                            val pen = slots[penIdx]; val n = Slots.penOrder.size
                            Slots.penOrder.forEachIndexed { i, stl -> OnRing(i, n, 116.dp) {
                                val on = pen.style == stl
                                Box(Modifier.size(48.dp).bounceClick(scale = 0.8f) { tick(); onPenStyle(stl); close() }.clip(CircleShape).background(if (on) Ars.Text.copy(alpha = 0.12f) else Color.Transparent)
                                    .border(if (on) 2.dp else 0.dp, if (on) Ars.Text else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                    StrokePreview(pen.copy(style = stl, width = pen.width.coerceIn(3f, 7f)), Modifier.size(40.dp, 30.dp)) }
                            } }
                        }
                    }
                }
                // colores
                swatches.forEachIndexed { i, c -> OnRing(i, swatches.size + 1, 60.dp) {
                    val on = cur != null && (cur.color or 0xFF000000.toInt()) == (c or 0xFF000000.toInt())
                    val sc by animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "sw")
                    Box(Modifier.size(30.dp).graphicsLayer { scaleX = sc; scaleY = sc }.bounceClick(scale = 0.8f) { tick(); onColor(c); close() }.clip(CircleShape).background(Color(c))
                        .border(if (on) 2.5.dp else 1.dp, if (on) Ars.Text else Ars.GlassBorder, CircleShape))
                } }
                OnRing(swatches.size, swatches.size + 1, 60.dp) {
                    Box(Modifier.size(30.dp).bounceClick(scale = 0.8f) { tick(); onPicker(); close() }.clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
                }
                // centro: grosor (o volver, cuando se están eligiendo lápices)
                val hasWidth = cur != null && cur.tool != Tool.LASSO && cur.tool != Tool.LASER
                Box(Modifier.size(54.dp).bounceClick(scale = 0.85f) {
                    tick()
                    if (styles) styles = false
                    else if (hasWidth && presets.isNotEmpty()) onWidth(presets.firstOrNull { it > cur!!.width + 0.1f } ?: presets.first())
                    else close()
                }.clip(CircleShape).background(Ars.Text.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
                    if (styles) Icon(ArsIcons.Back, "Herramientas", tint = Ars.Text)
                    else if (hasWidth) {
                        val dot by animateDpAsState((cur!!.width * (if (cur.tool == Tool.PEN) 2.2f else 0.5f)).coerceIn(6f, 36f).dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "dot")
                        Box(Modifier.size(dot).clip(CircleShape).background(if (cur.tool == Tool.ERASER) Ars.Muted else Color(cur.color).copy(alpha = 1f)).border(1.5.dp, Ars.Text.copy(alpha = 0.45f), CircleShape))
                    } else Icon(ArsIcons.Close, "Cerrar", tint = Ars.Text)
                }
            }
        }
        // etiqueta bajo la rueda: qué hay en la mano
        val below = cy + rp + with(d) { 54.dp.toPx() } < box.height
        Box(Modifier.offset { IntOffset((cx - rp).roundToInt(), (if (below) cy + rp + 10.dp.toPx() else cy - rp - 50.dp.toPx()).roundToInt()) }.width(radius * 2)
            .graphicsLayer { alpha = open.value.coerceIn(0f, 1f); translationY = (1f - open.value) * (if (below) -16f else 16f) }, contentAlignment = Alignment.Center) {
            Pill { Text(if (styles) "Tipo de lápiz" else (cur?.name ?: "") + (if (cur != null && cur.tool != Tool.LASSO && cur.tool != Tool.LASER) " · ${cur.width.let { if (it % 1f == 0f) it.toInt().toString() else "%.1f".format(it) }}" else ""),
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge) }
        }
    }
}
