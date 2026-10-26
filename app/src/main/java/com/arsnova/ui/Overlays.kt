package com.arsnova.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arsnova.data.Template
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paleta junto al lápiz: disco de vidrio con lápices en el arco superior y colores en el inferior. */
@Composable
fun PencilArc(at: Offset, slots: List<Slot>, active: Int, swatches: List<Int>, onSlot: (Int) -> Unit, onColor: (Int) -> Unit, onDismiss: () -> Unit) {
    val d = LocalDensity.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    val disc = 300.dp; val rp = with(d) { (disc / 2).toPx() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }
    Box(Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        if (box.width == 0) return@Box
        val cx = at.x.coerceIn(rp + 8f, maxOf(box.width - rp - 8f, rp + 8f)); val cy = at.y.coerceIn(rp + 8f, maxOf(box.height - rp - 8f, rp + 8f))
        val cur = slots.getOrNull(active)
        Surface(Modifier.offset { IntOffset((cx - rp).roundToInt(), (cy - rp).roundToInt()) }.size(disc)
            .graphicsLayer { scaleX = 0.85f + 0.15f * appear.value; scaleY = scaleX; alpha = appear.value }.pointerInput(Unit) { detectTapGestures { } },
            shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shadowElevation = 18.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.3f) 0.1f else 0.7f))) {
            Box(Modifier.fillMaxSize()) {
                val n = slots.size
                slots.forEachIndexed { i, s ->
                    val ang = Math.toRadians(198.0 + 144.0 * i / (n - 1).coerceAtLeast(1))
                    val it = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(30L + i * 18L); it.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                    val rr = 104f
                    Box(Modifier.align(Alignment.Center).offset((cos(ang) * rr).dp, (sin(ang) * rr + 6).dp).graphicsLayer { alpha = it.value; translationY = (1 - it.value) * 18f }) {
                        if (i == active) Box(Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                        ToolArt(s, i == active, label = false) { onSlot(i) }
                    }
                }
                swatches.forEachIndexed { i, c ->
                    val ang = Math.toRadians(40.0 + 100.0 * i / 4)
                    val it = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(110L + i * 22L); it.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f)) }
                    val on = cur != null && (cur.color or 0xFF000000.toInt()) == (c or 0xFF000000.toInt())
                    Box(Modifier.align(Alignment.Center).offset((cos(ang) * 96).dp, (sin(ang) * 96).dp).graphicsLayer { scaleX = it.value; scaleY = it.value }
                        .size(if (on) 34.dp else 28.dp).clip(CircleShape).background(Color(c)).border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.surface else Color.Black.copy(alpha = 0.1f), CircleShape)
                        .clickable { onColor(c) })
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(cur?.color ?: 0)).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
                    Text(cur?.name ?: "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private fun hsv(h: Float, s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

/** Selector de color estilo iPadOS: Cuadrícula / Espectro / Controles + opacidad. */
@Composable
fun ColorPopover(initial: Int, swatches: List<Int>, onPick: (Int) -> Unit, onSave: (Int) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var rgb by remember { mutableIntStateOf(initial or 0xFF000000.toInt()) }
    var op by remember { mutableFloatStateOf(((initial ushr 24) / 255f).let { if (it == 0f) 1f else it }) }
    val cur = (rgb and 0x00FFFFFF) or ((op * 255).toInt() shl 24)
    fun set(c: Int) { rgb = c or 0xFF000000.toInt(); onPick((rgb and 0x00FFFFFF) or ((op * 255).toInt() shl 24)) }
    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 16.dp, modifier = Modifier.width(340.dp).padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Colores", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClose) { Text("Listo") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Cuadrícula", "Espectro", "Controles").forEachIndexed { i, l -> SegmentedButton(tab == i, { tab = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l, maxLines = 1) } }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp))) {
                when (tab) {
                    0 -> Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) { for (i in 0 until 12) { val g = hsv(0f, 0f, 1f - i / 11f); Box(Modifier.weight(1f).fillMaxHeight().background(Color(g)).clickable { set(g) }) } }
                        for (row in 0 until 9) Row(Modifier.weight(1f)) {
                            for (h in 0 until 12) {
                                val v = if (row < 4) 1f else 1f - (row - 3) * 0.13f; val s = if (row < 4) 0.25f + row * 0.25f else 1f
                                val c = hsv((h * 30f + 200f) % 360f, s, v)
                                Box(Modifier.weight(1f).fillMaxHeight().background(Color(c)).clickable { set(c) })
                            }
                        }
                    }
                    1 -> Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                        fun at(o: Offset) { val h = (o.x / size.width).coerceIn(0f, 1f) * 360f; val y = (o.y / size.height).coerceIn(0f, 1f)
                            set(if (y < 0.5f) hsv(h, y * 2, 1f) else hsv(h, 1f, 1f - (y - 0.5f) * 2)) }
                        detectDragGestures(onDragStart = ::at) { c, _ -> at(c.position) }
                    }.pointerInput(Unit) { detectTapGestures { o -> val h = (o.x / size.width) * 360f; val y = o.y / size.height
                            set(if (y < 0.5f) hsv(h, y * 2, 1f) else hsv(h, 1f, 1f - (y - 0.5f) * 2)) } }) {
                        drawRect(Brush.horizontalGradient((0..12).map { Color(hsv(it * 30f, 1f, 1f)) }))
                        drawRect(Brush.verticalGradient(0f to Color.White, 0.5f to Color.Transparent))
                        drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black))
                    }
                    else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        listOf("Rojo" to 16, "Verde" to 8, "Azul" to 0).forEach { (l, sh) ->
                            val v = (rgb shr sh) and 0xFF
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(l, Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium)
                                Slider(v.toFloat(), { set((rgb and (0xFF shl sh).inv()) or (it.toInt() shl sh)) }, valueRange = 0f..255f, modifier = Modifier.weight(1f))
                                Text(v.toString(), Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text("#%06X".format(rgb and 0xFFFFFF), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
            Text("OPACIDAD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(op, { op = it; onPick(cur) }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
                Text("${(op * 100).toInt()}%", Modifier.width(44.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(cur)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)))
                swatches.forEach { c -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).clickable { set(c) }) }
                Box(Modifier.size(28.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { onSave(rgb) }, contentAlignment = Alignment.Center) { Text("+") }
            }
        }
    }
}

val TemplateChoices = listOf(Template.BLANK to "Ninguna", Template.LINED_N to "Líneas estrechas", Template.LINED to "Líneas", Template.LINED_W to "Líneas anchas",
    Template.GRID_S to "Cuadrícula pequeña", Template.GRID to "Cuadrícula", Template.GRID_L to "Cuadrícula grande", Template.DOTS to "Puntos")

/** Hoja "Líneas y cuadrículas" (iPadOS). */
@Composable
fun LinesSheet(current: Template, onPick: (Template) -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { onCancel() } }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(26.dp), shadowElevation = 20.dp, modifier = Modifier.widthIn(max = 460.dp).padding(20.dp).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth()) {
                    TextButton(onCancel, Modifier.align(Alignment.CenterStart)) { Text("Cancelar") }
                    Text("Líneas y cuadrículas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
                }
                Text("Cambia el estilo de líneas o cuadrícula para escribir en esta nota", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(14.dp))
                TemplateChoices.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    row.forEach { (t, l) ->
                        val sel = t == current
                        val bmp = remember(t) { com.arsnova.ink.Ink.render(com.arsnova.data.Page(0, t), 0.07f).asImageBitmap() }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp).clickable { onPick(t) }) {
                            androidx.compose.foundation.Image(bmp, l, Modifier.size(84.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                                .border(if (sel) 3.dp else 1.dp, if (sel) Brand else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Text(l, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } }
            }
        }
    }
}

/** Menú ⋯ de la barra con interruptores (iPadOS). */
@Composable
fun PaletteMenu(autoMin: Boolean, finger: Boolean, beautify: Boolean, onAutoMin: (Boolean) -> Unit, onFinger: (Boolean) -> Unit, onBeautify: (Boolean) -> Unit,
                onLines: () -> Unit, onPencil: () -> Unit, anchor: Alignment = Alignment.BottomCenter, pad: PaddingValues = PaddingValues(bottom = 96.dp), onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 16.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), modifier = Modifier.align(anchor).padding(pad).width(340.dp).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.padding(vertical = 6.dp)) {
                @Composable fun T(l: String, v: Boolean, on: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickable { on(!v) }.padding(18.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.weight(1f)); Switch(v, on) }
                T("Minimizar automáticamente", autoMin, onAutoMin)
                HorizontalDivider()
                T("Dibujar con el dedo", finger, onFinger)
                HorizontalDivider()
                T("Mejorar escritura a mano automáticamente", beautify, onBeautify)
                HorizontalDivider()
                Text("Líneas y cuadrículas…", Modifier.fillMaxWidth().clickable { onLines() }.padding(18.dp, 14.dp))
                HorizontalDivider()
                Text("Ajustes del lápiz…", Modifier.fillMaxWidth().clickable { onPencil() }.padding(18.dp, 14.dp))
            }
        }
    }
}
