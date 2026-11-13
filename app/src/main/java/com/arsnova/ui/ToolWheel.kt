package com.arsnova.ui

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.arsnova.data.Tool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Puente de teclas del lápiz (doble toque) desde la Activity. */
object StylusKeys {
    var handler: ((KeyEvent) -> Boolean)? = null
    val NATIVE = setOf(308, 309, 310, 311, 718) // STYLUS_BUTTON_* (API 34) y 718 = doble toque Huawei M-Pencil
}

/**
 * Rueda del lápiz (doble toque): un dial que nace bajo la punta.
 * - Anillo exterior: tus herramientas y lápices favoritos. Se gira como un dial (la que queda arriba, bajo la
 *   marca, es la elegida) o se toca directamente.
 * - Centro: grosor. Al tocarlo el anillo se vuelve un dial de grosor: girar a la derecha engorda, a la izquierda afina.
 * - Anillo interior: colores. Bajo la rueda: qué hay en la mano, deshacer y rehacer.
 */
@Composable
fun ToolWheel(
    at: Offset, slots: List<Slot>, active: Int, swatches: List<Int>, canUndo: Boolean, canRedo: Boolean,
    onSlot: (Int) -> Unit, onColor: (Int) -> Unit, onPicker: () -> Unit, onWidth: (Float) -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit, onEdit: () -> Unit = {}, onDismiss: () -> Unit,
) {
    val d = LocalDensity.current; val view = LocalView.current; val scope = rememberCoroutineScope()
    val radius = 152.dp; val rp = with(d) { radius.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var widthMode by remember { mutableStateOf(false) }
    val n = slots.size.coerceAtLeast(1); val step = 360f / n
    val cur = slots.getOrNull(active)
    val nS by rememberUpdatedState(n); val curS by rememberUpdatedState(cur)
    val slotCb by rememberUpdatedState(onSlot); val widthCb by rememberUpdatedState(onWidth)
    // giro del anillo: la herramienta activa queda arriba; al abrir entra girando
    var rot by remember { mutableFloatStateOf(-(active.coerceAtLeast(0)) * step - 70f) }
    var ticks by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableIntStateOf(active.coerceAtLeast(0)) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun settle(i: Int) { job?.cancel(); val st = 360f / nS; val want = -i * st; val k = Math.round((rot - want) / 360f); val target = want + k * 360f
        job = scope.launch { animate(rot, target, animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f)) { v, _ -> rot = v } } }
    fun topIdx(): Int { val st = 360f / nS; return (((-rot / st).roundToInt() % nS) + nS) % nS }
    LaunchedEffect(Unit) { launch { open.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }; settle(active.coerceAtLeast(0)) }
    fun close() { if (!closing) { closing = true; scope.launch { open.animateTo(0f, tween(130)); onDismiss() } } }
    val hasWidth = cur != null && cur.tool != Tool.LASSO && cur.tool != Tool.LASER

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) { detectTapGestures { close() } }) {
        if (box.width == 0) return@Box
        val cx = at.x.coerceIn(rp + 8f, maxOf(box.width - rp - 8f, rp + 8f)); val cy = at.y.coerceIn(rp + 8f, maxOf(box.height - rp - 8f, rp + 8f))
        Surface(Modifier.offset { IntOffset((cx - rp).roundToInt(), (cy - rp).roundToInt()) }.size(radius * 2)
            .graphicsLayer { val k = 0.72f + 0.28f * open.value; scaleX = k; scaleY = k; alpha = open.value.coerceIn(0f, 1f) }
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                // girar el anillo exterior como un dial
                var prev = 0f; var dial = false; var w = 1f; var acc = 0f
                detectDragGestures(onDragStart = { p -> val dx = p.x - size.width / 2f; val dy = p.y - size.height / 2f
                    dial = hypot(dx, dy) > 82.dp.toPx(); prev = atan2(dy, dx); w = curS?.width ?: 1f; acc = 0f; job?.cancel() },
                    onDragEnd = { if (dial && !widthMode) settle(topIdx()) }, onDragCancel = { if (dial && !widthMode) settle(topIdx()) }) { c, _ ->
                    if (!dial) return@detectDragGestures
                    c.consume()
                    val a = atan2(c.position.y - size.height / 2f, c.position.x - size.width / 2f)
                    var da = Math.toDegrees((a - prev).toDouble()).toFloat(); if (da > 180f) da -= 360f; if (da < -180f) da += 360f; prev = a
                    if (!widthMode) { rot += da; val i = topIdx(); if (i != top) { top = i; tick(); slotCb(i) } }
                    else curS?.let { s ->
                        ticks += da; acc += da; if (kotlin.math.abs(acc) > 9f) { acc = 0f; tick() }
                        val range = Widths.range(s.tool); val st = Widths.step(s.tool)
                        w = (w * exp(da * 0.0062f)).coerceIn(range.start, range.endInclusive)
                        val r = (Math.round(w / st) * st).coerceIn(range.start, range.endInclusive)
                        if (kotlin.math.abs(r - s.width) > 0.001f) widthCb(r)
                    }
                }
            },
            shape = CircleShape, color = Ars.Glass, shadowElevation = 20.dp, border = BorderStroke(1.dp, Ars.GlassBorder)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val line = Ars.Line; val track = Ars.Text.copy(alpha = 0.045f); val mark = Ars.Text
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(track, 116.dp.toPx(), style = Stroke(58.dp.toPx()))
                    drawCircle(line, 87.dp.toPx(), style = Stroke(1.dp.toPx()))
                    // marca fija de arriba: lo que queda debajo es lo elegido
                    drawLine(mark, Offset(center.x, 3.dp.toPx()), Offset(center.x, 11.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                }
                AnimatedContent(widthMode, transitionSpec = { (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), 0.88f)) togetherWith (fadeOut(tween(110)) + scaleOut(targetScale = 1.07f)) },
                    contentAlignment = Alignment.Center, label = "ring") { wm ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (wm) Canvas(Modifier.fillMaxSize()) {
                            // dial de grosor: marcas que giran con el dedo
                            rotate(ticks) { for (k in 0 until 60) rotate(k * 6f) {
                                val long = k % 5 == 0
                                drawLine(mark.copy(alpha = if (long) 0.7f else 0.28f), Offset(center.x, (if (long) 24 else 30).dp.toPx()), Offset(center.x, 44.dp.toPx()), (if (long) 2.2f else 1.4f).dp.toPx(), StrokeCap.Round) } }
                        } else {
                            val size = (2 * Math.PI * 116 / n - 10).toFloat().coerceIn(30f, 50f).dp
                            slots.forEachIndexed { i, s ->
                                val p = remember { Animatable(0f) }
                                LaunchedEffect(Unit) { delay(20L + i * 14L); p.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 480f)) }
                                val on = i == active
                                val pop by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "pop")
                                // cada herramienta es su lápiz dibujado, en abanico con la punta hacia fuera (como la paleta del Apple Pencil)
                                val gw = size * 0.74f
                                Box(Modifier.offset { val a = Math.toRadians((-90f + i * step + rot).toDouble()); val k = 0.55f + 0.45f * p.value
                                        IntOffset((cos(a) * 119.dp.toPx() * k).roundToInt(), (sin(a) * 119.dp.toPx() * k).roundToInt()) }
                                    .graphicsLayer { alpha = p.value.coerceIn(0f, 1f); val k = (0.6f + 0.4f * p.value) * pop; scaleX = k; scaleY = k; rotationZ = i * step + rot }
                                    .size(gw + 10.dp, gw * 1.55f + 8.dp).bounceClick(scale = 0.86f) { tick(); top = i; onSlot(i); settle(i); scope.launch { delay(240); close() } }
                                    .clip(RoundedCornerShape(50)).background(if (on) Ars.Text.copy(alpha = 0.13f) else Color.Transparent)
                                    .semantics { contentDescription = s.name },
                                    contentAlignment = Alignment.Center) {
                                    ToolGlyph(s, Modifier.size(gw, gw * 1.55f), fade = true)
                                }
                            }
                        }
                    }
                }
                // colores
                val ringItem: @Composable (Int, @Composable () -> Unit) -> Unit = { i, content ->
                    val a = Math.toRadians(-90.0 + 360.0 * i / (swatches.size + 1))
                    val p = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(60L + i * 16L); p.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 480f)) }
                    Box(Modifier.offset { IntOffset((cos(a) * 60.dp.toPx() * p.value).roundToInt(), (sin(a) * 60.dp.toPx() * p.value).roundToInt()) }.graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }) { content() }
                }
                swatches.forEachIndexed { i, c -> ringItem(i) {
                    val on = cur != null && (cur.color or 0xFF000000.toInt()) == (c or 0xFF000000.toInt())
                    val sc by animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "sw")
                    Box(Modifier.size(30.dp).graphicsLayer { scaleX = sc; scaleY = sc }.bounceClick(scale = 0.8f) { tick(); onColor(c) }.clip(CircleShape).background(Color(c))
                        .border(if (on) 2.5.dp else 1.dp, if (on) Ars.Text else Ars.GlassBorder, CircleShape))
                } }
                ringItem(swatches.size) {
                    Box(Modifier.size(30.dp).bounceClick(scale = 0.8f) { tick(); onPicker(); close() }.clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
                }
                // centro: grosor
                Box(Modifier.size(54.dp).bounceClick(scale = 0.85f) { tick(); if (hasWidth) widthMode = !widthMode else close() }
                    .clip(CircleShape).background(Ars.Text.copy(alpha = if (widthMode) 0.16f else 0.07f)), contentAlignment = Alignment.Center) {
                    if (!hasWidth) Icon(ArsIcons.Close, "Cerrar", tint = Ars.Text)
                    else if (widthMode) Text(wfmt(cur!!.width), style = MaterialTheme.typography.titleMedium, color = Ars.Text)
                    else {
                        val dot by animateDpAsState((cur!!.width * (if (cur.tool == Tool.PEN) 2.2f else 0.5f)).coerceIn(6f, 36f).dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "dot")
                        Box(Modifier.size(dot).clip(CircleShape).background(if (cur.tool == Tool.ERASER) Ars.Muted else Color(cur.color).copy(alpha = 1f)).border(1.5.dp, Ars.Text.copy(alpha = 0.45f), CircleShape))
                    }
                }
            }
        }
        // bajo la rueda: deshacer · qué hay en la mano · rehacer
        val below = cy + rp + with(d) { 170.dp.toPx() } < box.height || cy - rp < with(d) { 130.dp.toPx() }
        Row(Modifier.offset { IntOffset((cx - rp).roundToInt(), (if (below) cy + rp + 10.dp.toPx() else cy - rp - 58.dp.toPx()).roundToInt()) }.width(radius * 2)
            .graphicsLayer { alpha = open.value.coerceIn(0f, 1f); translationY = (1f - open.value) * (if (below) -18f else 18f) },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Pill { IconButton({ tick(); onUndo() }, enabled = canUndo) { Icon(ArsIcons.Undo, "Deshacer") } }
            Pill { AnimatedContent(if (widthMode) "Gira para cambiar el grosor" else (cur?.name ?: "") + (if (hasWidth) " · ${wfmt(cur!!.width)}" else ""),
                transitionSpec = { (fadeIn(tween(140)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(90)) + slideOutVertically { -it / 2 }) }, label = "lbl") { t ->
                Text(t, Modifier.padding(horizontal = 14.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1) } }
            Pill { IconButton({ tick(); onRedo() }, enabled = canRedo) { Icon(ArsIcons.Redo, "Rehacer") } }
            Pill { IconButton({ tick(); onEdit(); close() }) { Icon(ArsIcons.Edit, "Editar la rueda") } }
        }
    }
}
