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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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

private fun hsv(h: Float, s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

/** Selector de color estilo iPadOS: Cuadrícula / Espectro / Controles + opacidad. */
@Composable
fun ColorPopover(initial: Int, swatches: List<Int>, onPick: (Int) -> Unit, onSave: (Int) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var rgb by remember { mutableIntStateOf(initial or 0xFF000000.toInt()) }
    var op by remember { mutableFloatStateOf(((initial ushr 24) / 255f).let { if (it == 0f) 1f else it }) }
    val cur = (rgb and 0x00FFFFFF) or ((op * 255).toInt() shl 24)
    fun set(c: Int) { rgb = c or 0xFF000000.toInt(); onPick((rgb and 0x00FFFFFF) or ((op * 255).toInt() shl 24)) }
    Surface(shape = squircle(28), shadowElevation = 16.dp, color = Ars.Glass, border = androidx.compose.foundation.BorderStroke(1.dp, Ars.GlassBorder), modifier = Modifier.width(340.dp).padding(bottom = 10.dp)) {
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
        Surface(shape = squircle(30), shadowElevation = 20.dp, color = Ars.Sheet, modifier = Modifier.widthIn(max = 460.dp).padding(20.dp).pointerInput(Unit) { detectTapGestures { } }) {
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
                        val bmp = remember(t) { com.arsnova.ink.Ink.paperPreview(t, -1, 0f, 330, 440).asImageBitmap() }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp).clickable { onPick(t) }) {
                            androidx.compose.foundation.Image(bmp, l, Modifier.size(84.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) Ars.Text else Ars.Line, RoundedCornerShape(8.dp)),
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
                onLines: () -> Unit, onPencil: () -> Unit, onEditBar: () -> Unit = {}, anchor: Alignment = Alignment.BottomCenter, pad: PaddingValues = PaddingValues(bottom = 96.dp), onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Surface(shape = squircle(28), shadowElevation = 16.dp, color = Ars.Glass, border = androidx.compose.foundation.BorderStroke(1.dp, Ars.GlassBorder), modifier = Modifier.align(anchor).padding(pad).width(340.dp).pointerInput(Unit) { detectTapGestures { } }) {
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
                HorizontalDivider()
                Text("Editar barra…", Modifier.fillMaxWidth().clickable { onEditBar() }.padding(18.dp, 14.dp))
            }
        }
    }
}

/** Fórmula dibujada (centrada y ajustada al hueco). */
@Composable
fun LatexView(tex: String, modifier: Modifier = Modifier) {
    val color = Ars.Text
    val dr = remember(tex, color) { com.arsnova.ink.Latex.get(tex, 64f, android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (dr == null) Text(if (tex.isBlank()) "Escribe la fórmula en LaTeX" else "Fórmula incompleta…", color = Ars.Muted, style = MaterialTheme.typography.bodySmall)
        else Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val k = minOf(size.width / dr.intrinsicWidth, size.height / dr.intrinsicHeight, 1.6f)
            drawContext.canvas.nativeCanvas.let { c -> c.save(); c.translate((size.width - dr.intrinsicWidth * k) / 2, (size.height - dr.intrinsicHeight * k) / 2); c.scale(k, k)
                dr.setBounds(0, 0, dr.intrinsicWidth, dr.intrinsicHeight); dr.draw(c); c.restore() }
        }
    }
}

/** Editor de fórmulas: LaTeX con vista previa en vivo y atajos de los símbolos más usados. */
@Composable
fun FormulaDialog(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var tex by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }
    fun put(sn: String) { val a = tex.selection.min; val b = tex.selection.max; val t = tex.text.substring(0, a) + sn + tex.text.substring(b)
        val cur = sn.indexOf("{}").let { if (it >= 0) a + it + 1 else a + sn.length }; tex = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(cur)) }
    AlertDialog(onDismiss, title = { Text("Fórmula") }, text = {
        Column {
            LatexView(tex.text, Modifier.fillMaxWidth().height(110.dp).clip(squircle(18)).background(Ars.CardHigh))
            OutlinedTextField(tex, { tex = it }, Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 2, placeholder = { Text("\\frac{a}{b} + \\sqrt{x}") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), shape = squircle(16))
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("a/b" to "\\frac{}{}", "√" to "\\sqrt{}", "xⁿ" to "^{}", "xₙ" to "_{}", "∫" to "\\int_{}^{} ", "Σ" to "\\sum_{}^{} ", "∂" to "\\partial ", "Δ" to "\\Delta ", "α" to "\\alpha ", "β" to "\\beta ", "π" to "\\pi ",
                    "∞" to "\\infty ", "·" to "\\cdot ", "→" to "\\rightarrow ", "⇌" to "\\rightleftharpoons ", "≤" to "\\leq ", "≥" to "\\geq ", "≈" to "\\approx ", "°" to "^{\\circ}").forEach { (l, sn) ->
                    Box(Modifier.bounceClick { put(sn) }.clip(squircle(12)).background(Ars.PillFill).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(l, style = MaterialTheme.typography.labelLarge) } }
            }
        }
    }, confirmButton = { TextButton({ onDone(tex.text.trim()) }) { Text("Insertar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

/** Editar la barra: qué herramientas y lápices hay, en qué orden, y cómo se ven. */
@Composable
fun BarEditor(slots: List<Slot>, grouped: Boolean, art: Boolean, onSlots: (List<Slot>) -> Unit, onGrouped: (Boolean) -> Unit, onArt: (Boolean) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.Center) {
        Surface(shape = squircle(30), shadowElevation = 20.dp, color = Ars.Sheet, modifier = Modifier.widthIn(max = 480.dp).padding(20.dp).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Editar barra", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClose) { Text("Listo") } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) { Text("Lápices con forma (estilo Apple)", Modifier.weight(1f)); Switch(art, onArt) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Column(Modifier.weight(1f)) { Text("Agrupar los lápices en un solo botón"); Text("La barra muestra solo el lápiz en uso; los demás siguen en la rueda", style = MaterialTheme.typography.bodySmall, color = Ars.Muted) }
                    Switch(grouped, onGrouped) }
                Text("En la barra", style = MaterialTheme.typography.labelLarge, color = Ars.Muted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                slots.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(squircle(16)).background(Ars.CardHigh.copy(alpha = 0.6f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.tool == com.arsnova.data.Tool.PEN || s.tool == com.arsnova.data.Tool.HIGHLIGHTER) StrokePreview(s.copy(width = if (s.tool == com.arsnova.data.Tool.PEN) s.width.coerceIn(3f, 6f) else 12f), Modifier.size(44.dp, 30.dp))
                        else Box(Modifier.size(44.dp, 30.dp), contentAlignment = Alignment.Center) { Icon(toolIcon(s.tool), null) }
                        Text(s.name + (if (s.width > 0f) " · ${wfmt(s.width)}" else ""), Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
                        IconButton({ onSlots(slots.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0) { Icon(ArsIcons.Up, "Subir") }
                        IconButton({ onSlots(slots.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < slots.size - 1) { Icon(ArsIcons.Down, "Bajar") }
                        IconButton({ onSlots(slots.toMutableList().also { it.removeAt(i) }) }, enabled = slots.size > 1) { Icon(ArsIcons.Trash, "Quitar", tint = Brand) }
                    }
                }
                Text("Añadir", style = MaterialTheme.typography.labelLarge, color = Ars.Muted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                val add = Slots.penOrder.map { Slots.fresh(com.arsnova.data.Tool.PEN, it) } + listOf(com.arsnova.data.Tool.HIGHLIGHTER, com.arsnova.data.Tool.ERASER, com.arsnova.data.Tool.LASSO, com.arsnova.data.Tool.TAPE, com.arsnova.data.Tool.LASER).map { Slots.fresh(it) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    add.forEach { n -> Row(Modifier.bounceClick { onSlots(slots + n) }.clip(CircleShape).background(Ars.PillFill).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ArsIcons.Add, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(n.name, style = MaterialTheme.typography.labelLarge) } }
                }
                TextButton({ onSlots(Slots.defaults) }, Modifier.padding(top = 10.dp)) { Text("Restablecer la barra", color = Ars.Muted) }
            }
        }
    }
}
