package com.arsnova.ui

import android.content.SharedPreferences
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke as DStroke
import androidx.compose.ui.unit.dp
import com.arsnova.data.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Estilos de tinta: 0 bolígrafo, 1 pluma, 2 lápiz, 3 pincel, 4 monolínea, 5 rotulador, 6 resaltador. */
val StyleNames = listOf("Bolígrafo", "Pluma estilográfica", "Lápiz", "Pincel", "Monolínea", "Rotulador", "Resaltador", "Caligrafía", "Neón", "Acuarela", "Puntillismo", "Cinta", "Crayón", "Línea discontinua")

@Serializable
data class Slot(val tool: Tool, val style: Int, val color: Int, val width: Float, val stab: Float = 0.2f) {
    val name get() = when (tool) { Tool.ERASER -> "Borrador"; Tool.LASSO -> "Lazo"; Tool.TEXT -> "Texto"; Tool.TAPE -> "Cinta para ocultar"; Tool.LASER -> "Puntero láser"; else -> StyleNames[style] }
}

object Slots {
    private val json = Json { ignoreUnknownKeys = true }
    /** Como en Apple: cada lápiz es su propio botón, con su color, grosor y estabilización. */
    val defaults = listOf(Slot(Tool.PEN, 0, 0xFF1D1B18.toInt(), 3.5f), Slot(Tool.PEN, 1, 0xFF2D5BD9.toInt(), 4f), Slot(Tool.PEN, 2, 0xFF3A3A3C.toInt(), 5f),
        Slot(Tool.HIGHLIGHTER, 6, 0xFFF2B705.toInt(), 28f, 0.5f), Slot(Tool.ERASER, 0, 0, 16f), Slot(Tool.LASSO, 0, 0, 0f), Slot(Tool.TAPE, 0, 0xFF7FB3D5.toInt(), 44f), Slot(Tool.LASER, 0, 0xFFFF3B30.toInt(), 8f))
    /** Orden de estilos de lápiz. */
    val penOrder = listOf(0, 1, 2, 12, 5, 3, 7, 4, 13, 9)
    fun fresh(t: Tool, style: Int = 0) = when (t) { Tool.HIGHLIGHTER -> defaults[3]; Tool.ERASER -> defaults[4]; Tool.LASSO -> defaults[5]; Tool.TAPE -> defaults[6]; Tool.LASER -> defaults[7]
        else -> Slot(Tool.PEN, style, 0xFF1D1B18.toInt(), if (style == 3) 9f else 4f) }
    fun load(p: SharedPreferences): List<Slot> = runCatching { json.decodeFromString<List<Slot>>(p.getString("slots2", null)!!) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: defaults
    fun save(p: SharedPreferences, s: List<Slot>) = p.edit().putString("slots2", json.encodeToString(s)).apply()
    fun loadSwatches(p: SharedPreferences): List<Int> = p.getString("swatches", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 5 }
        ?: listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30).map { it.toInt() }
    fun saveSwatches(p: SharedPreferences, s: List<Int>) = p.edit().putString("swatches", s.joinToString(",")).apply()
}

fun toolIcon(t: Tool) = when (t) { Tool.PEN -> ArsIcons.Pen; Tool.HIGHLIGHTER -> ArsIcons.Highlighter; Tool.ERASER -> ArsIcons.Eraser; Tool.LASSO -> ArsIcons.Lasso; Tool.TAPE -> ArsIcons.Tape; Tool.LASER -> ArsIcons.Laser; else -> ArsIcons.Text }
fun wfmt(w: Float) = if (kotlin.math.abs(w - Math.round(w)) < 0.01f) Math.round(w).toString() else "%.1f".format(w)

/** Paleta ampliada: 12 tonos × 6 luminosidades + grises. */
val BigPalette: List<Int> = buildList {
    for ((sa, v) in listOf(0.25f to 1f, 0.5f to 1f, 0.85f to 1f, 0.9f to 0.78f, 0.9f to 0.56f, 0.9f to 0.36f))
        for (h in 0 until 12) add(android.graphics.Color.HSVToColor(floatArrayOf(h * 30f, sa, v)))
    for (i in 0 until 12) { val g = (255 * i / 11f).toInt(); add(android.graphics.Color.rgb(g, g, g)) }
}

/** Iconos de lápices diseñados como vectores (viewBox 40×80, punta arriba). */
private object PenArt {
    private val raw = mapOf(
        "calligraphy" to listOf("M11 36 C12 31 14 28 15 26 L25 26 C26 28 28 31 29 36 Z" to "dark", "M15 26 L16 10 L24 7 L25 26 Z" to "metal", "M16 10 L24 7 L24 9.2 L16 12.2 Z" to "ink"),
        "neon" to listOf("M11 36 L14 21 L26 21 L29 36 Z" to "dark", "M15.5 21 Q20 1 24.5 21 Z" to "ink", "M18.6 20 Q20 8 21.4 20 Z" to "white"),
        "watercolor" to listOf("M13 25 L27 25 L28.5 36 L11.5 36 Z" to "wood", "M14.5 25.5 C14 16 18 9 20 3.5 C22 9 26 16 25.5 25.5 Z" to "inka"),
        "dots" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "white", "M18.6 6 A1.4 1.4 0 1 0 21.4 6 A1.4 1.4 0 1 0 18.6 6 Z" to "ink", "M15.4 9.6 A1 1 0 1 0 17.4 9.6 A1 1 0 1 0 15.4 9.6 Z" to "ink", "M22.6 9.6 A1 1 0 1 0 24.6 9.6 A1 1 0 1 0 22.6 9.6 Z" to "ink"),
        "tape" to listOf("M11 36 L11 18 Q11 12 20 12 Q29 12 29 18 L29 36 Z" to "ink", "M14 20 L26 20 L26 23 L14 23 Z" to "white"),
        "laser" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "dark", "M17.5 10 A2.5 2.5 0 1 0 22.5 10 A2.5 2.5 0 1 0 17.5 10 Z" to "ink"),
        "monoline" to listOf("M11 36 C11 30 17 14 18.6 9 L21.4 9 C23 14 29 30 29 36 Z" to "white", "M18.6 9 L21.4 9 L20.9 4.2 Q20 3.2 19.1 4.2 Z" to "ink"),
        "ballpoint" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "white", "M18 12 L22 12 L20.7 5.2 L19.3 5.2 Z" to "metal", "M19.3 5.2 L20.7 5.2 L20.3 3.6 L19.7 3.6 Z" to "ink"),
        "fountain" to listOf("M11 36 C12 31 14 28 15 26 L25 26 C26 28 28 31 29 36 Z" to "dark", "M15 26 C15 18 18.6 9.5 20 3.8 C21.4 9.5 25 18 25 26 Z" to "metal", "M19.6 6 L20.4 6 L20.4 18 L19.6 18 Z" to "dark", "M18.8 19.6 A1.2 1.2 0 1 0 21.2 19.6 A1.2 1.2 0 1 0 18.8 19.6 Z" to "dark", "M19.4 3.4 L20.6 3.4 L20.3 5.2 L19.7 5.2 Z" to "ink"),
        "pencil" to listOf("M11 36 L18.2 12.5 L21.8 12.5 L29 36 Q26.6 34.4 24.5 36 Q22.2 34.4 20 36 Q17.8 34.4 15.5 36 Q13.4 34.4 11 36 Z" to "wood", "M18.2 12.5 L20 5 L21.8 12.5 Z" to "ink"),
        "brush" to listOf("M13 25 L27 25 L28.5 36 L11.5 36 Z" to "metal", "M14 25.5 C13 18 18 8.5 20 3 C22 8.5 27 18 26 25.5 Z" to "ink"),
        "marker" to listOf("M11 36 L14 22 L26 22 L29 36 Z" to "white", "M15 22 L15.8 12.5 L24.2 9 L25 22 Z" to "ink"),
        "highlighter" to listOf("M11 36 L13 24 L27 24 L29 36 Z" to "white", "M13.6 24 L13.6 15.5 L26.4 10 L26.4 24 Z" to "inka"),
        "eraser" to listOf("M11 30 L11 17 Q11 8.5 20 8.5 Q29 8.5 29 17 L29 30 Z" to "pink", "M11 30 L29 30 L29 33 L11 33 Z" to "metal"),
        "crayon" to listOf("M11 36 L12.5 21 L27.5 21 L29 36 Z" to "white", "M13.5 21 L15.5 9.5 Q20 5.5 24.5 9.5 L26.5 21 Z" to "ink", "M11 30 L29 30 L29 33 L11 33 Z" to "inka"),
        "dash" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "white", "M18 12 L22 12 L20.7 5.2 L19.3 5.2 Z" to "ink", "M13.2 28 L26.8 28 L27.6 30.4 L12.4 30.4 Z" to "ink", "M15.2 22 L24.8 22 L25.6 24.4 L14.4 24.4 Z" to "ink"),
        "lasso" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "white", "M18 12 L22 12 L20 5 Z" to "gray")
    )
    val parts: Map<String, List<Pair<Path, String>>> by lazy { raw.mapValues { (_, l) -> l.map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it.first).toPath() to it.second } } }
    val barrel by lazy { Path().apply { addRect(androidx.compose.ui.geometry.Rect(11f, 36f, 29f, 84f)) } }
    private fun g(vararg c: Long, x0: Float = 11f, x1: Float = 29f) = Brush.horizontalGradient(c.map { Color(it) }, x0, x1)
    val barrelB = g(0xFFDCD7CE, 0xFFFFFFFF, 0xFFF3F0EA, 0xFFCFC9BF)
    val mats = mapOf("white" to g(0xFFE2DED6, 0xFFFFFFFF, 0xFFD9D4CB), "metal" to g(0xFF8E8E93, 0xFFF2F2F7, 0xFF7C7C82, x0 = 13f, x1 = 27f),
        "wood" to g(0xFFD2A878, 0xFFF2D9B8, 0xFFC69A6B), "pink" to g(0xFFE8938F, 0xFFF9C2BE, 0xFFDE837F))
    fun key(s: Slot) = when (s.tool) { Tool.ERASER -> "eraser"; Tool.LASSO -> "lasso"; Tool.HIGHLIGHTER -> "highlighter"
        Tool.TAPE -> "tape"; Tool.LASER -> "laser"
        else -> when (s.style) { 1 -> "fountain"; 2 -> "pencil"; 3 -> "brush"; 4 -> "monoline"; 5 -> "marker"; 7 -> "calligraphy"; 8 -> "neon"; 9 -> "watercolor"; 10 -> "dots"; 12 -> "crayon"; 13 -> "dash"; else -> "ballpoint" } }
}

private fun DrawScope.toolArt(s: Slot, dark: Boolean) {
    val k = size.width / 40f
    val ink = Color(s.color).copy(alpha = 1f)
    val line = if (dark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.22f)
    val st = DStroke(0.6f, join = StrokeJoin.Round)
    val name = PenArt.key(s)
    withTransform({ scale(k, k, Offset.Zero) }) {
        drawPath(PenArt.barrel, PenArt.barrelB); drawPath(PenArt.barrel, line, style = st)
        if (name != "eraser" && name != "lasso") drawRect(ink, Offset(11f, 44f), Size(18f, 3.4f))
        for ((p, m) in PenArt.parts[name].orEmpty()) {
            when (m) {
                "ink" -> drawPath(p, ink); "inka" -> drawPath(p, ink.copy(alpha = 0.85f))
                "dark" -> drawPath(p, Color(0xFF2C2C2E)); "gray" -> drawPath(p, Color(0xFFA1A1A6))
                else -> drawPath(p, PenArt.mats.getValue(m))
            }
            drawPath(p, line, style = st)
        }
    }
}

@Composable
fun ToolArt(s: Slot, sel: Boolean, label: Boolean = true, onClick: () -> Unit) {
    val lift by animateDpAsState(if (sel) 0.dp else 14.dp, spring(dampingRatio = 0.55f, stiffness = 380f), label = "lift")
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    Box(Modifier.width(40.dp).height(64.dp).clipToBounds().clickable(onClick = onClick)) {
        Canvas(Modifier.fillMaxSize().offset(y = lift)) { toolArt(s, dark) }
        if (label && sel && (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER))
            Text(s.width.toInt().toString(), Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF3A3A3C))
    }
}

/** Barra compacta estilo Samsung Notes: un botón por herramienta, colores y acciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleToolbar(
    slots: List<Slot>, active: Int, swatches: List<Int>, canUndo: Boolean, canRedo: Boolean,
    onSelect: (Int) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onColor: (Int) -> Unit, onEditSwatch: (Int) -> Unit,
    onPicker: () -> Unit, insertMenu: @Composable (Boolean, () -> Unit) -> Unit, onMore: () -> Unit, onCollapse: () -> Unit = {},
    dock: Int = 0, handle: Modifier = Modifier, grouped: Boolean = false, penIdx: Int = 0, art: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme; val dark = cs.background.luminance() < 0.3f
    var plus by remember { mutableStateOf(false) }
    val vertical = dock == 1 || dock == -1
    val shape = squircle(28)
    val cur = slots.getOrNull(active)?.color ?: slots[0].color
    @Composable fun Sep() = Box(Modifier.padding(4.dp).size(if (vertical) 22.dp else 1.dp, if (vertical) 1.dp else 22.dp).background(cs.outline.copy(alpha = 0.35f)))
    @Composable fun ToolBtn(i: Int) {
        val s = slots[i]; val on = i == active
        val bg by androidx.compose.animation.animateColorAsState(if (on) Ars.Text else Color.Transparent, androidx.compose.animation.core.tween(180), label = "tb")
        val fg by androidx.compose.animation.animateColorAsState(if (on) Ars.Bg else Ars.Text.copy(alpha = 0.62f), androidx.compose.animation.core.tween(180), label = "tf")
        val pop by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.9f, spring(dampingRatio = 0.5f, stiffness = 520f), label = "tp")
        Box(Modifier.padding(2.dp).size(44.dp).bounceClick(scale = 0.84f) { onSelect(i) }.graphicsLayer { scaleX = pop; scaleY = pop }.clip(squircle(15)).background(bg), contentAlignment = Alignment.Center) {
            Icon(when (s.tool) { Tool.PEN -> ArsIcons.Pen; Tool.HIGHLIGHTER -> ArsIcons.Highlighter; Tool.ERASER -> ArsIcons.Eraser; Tool.LASSO -> ArsIcons.Lasso; Tool.TAPE -> ArsIcons.Tape; Tool.LASER -> ArsIcons.Laser; else -> ArsIcons.Text },
                s.name, tint = fg)
            if (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER || s.tool == Tool.TAPE || s.tool == Tool.LASER) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(9.dp).clip(CircleShape).background(Color(s.color).copy(alpha = 1f)).border(1.5.dp, if (on) Ars.Text else Ars.Glass.copy(alpha = 1f), CircleShape))
        }
    }
    @Composable fun Dot(c: Int, k: Int) {
        val on = (c or 0xFF000000.toInt()) == (cur or 0xFF000000.toInt())
        val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "dot")
        Box(Modifier.padding(4.dp).size(26.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(Color(c)).border(if (on) 2.5.dp else 1.dp, if (on) cs.onSurface else Ars.GlassBorder, CircleShape)
            .combinedClickable(onClick = { onColor(c) }, onLongClick = { onEditSwatch(k) }), contentAlignment = Alignment.Center) {
            if (on) Icon(ArsIcons.Check, null, Modifier.size(14.dp), tint = if (Color(c).luminance() > 0.6f) Color.Black else Color.White) }
    }
    @Composable fun Items() {
        IconButton(onUndo, enabled = canUndo) { Icon(ArsIcons.Undo, "Deshacer") }
        IconButton(onRedo, enabled = canRedo) { Icon(ArsIcons.Redo, "Rehacer") }
        Sep()
        // Estilo Apple: en horizontal los lápices asoman de pie desde el borde y el elegido sube; agrupados, solo se ve el lápiz en uso
        slots.indices.filter { slots[it].tool != Tool.PEN || !grouped || it == penIdx }.forEach { i ->
            if (art && !vertical) Box(Modifier.padding(horizontal = 1.dp).graphicsLayer { rotationZ = if (dock == 2) 180f else 0f }) { ToolArt(slots[i], i == active, label = false) { onSelect(i) } }
            else ToolBtn(i) }
        Sep()
        (if (vertical) swatches.take(2) else swatches).forEachIndexed { k, c -> Dot(c, k) }
        Box(Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).clickable { onPicker() })
        Sep()
        Box { IconButton({ plus = true }) { Icon(ArsIcons.Add, "Insertar") }; insertMenu(plus) { plus = false } }
        IconButton(onMore) { Icon(ArsIcons.More, "Más") }
        IconButton(onCollapse) { Icon(ArsIcons.Down, "Minimizar", tint = cs.outline,
            modifier = Modifier.graphicsLayer { rotationZ = when (dock) { 1 -> 90f; -1 -> -90f; 2 -> 180f; else -> 0f } }) }
    }
    val grip = @Composable { Box(handle.size(if (vertical) 52.dp else 36.dp, if (vertical) 34.dp else 52.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (vertical) 24.dp else 4.dp, if (vertical) 4.dp else 24.dp).clip(CircleShape).background(cs.outline.copy(alpha = 0.55f))) } }
    Surface(shape = shape, color = Ars.Glass, shadowElevation = 10.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Ars.GlassBorder)) {
        if (vertical) Column(Modifier.padding(vertical = 6.dp).widthIn(max = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            grip(); Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { Items() } }
        else Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            grip(); Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { Items() } }
    }
}

/** Tres grosores guardados por herramienta (toca para usar, mantén para guardar el actual). */
object Widths {
    fun defaults(t: Tool) = when (t) { Tool.HIGHLIGHTER -> listOf(18f, 28f, 44f); Tool.ERASER -> listOf(10f, 18f, 40f); Tool.TAPE -> listOf(24f, 44f, 72f); else -> listOf(2.5f, 5f, 9f) }
    fun load(p: SharedPreferences, t: Tool): List<Float> = p.getString("wp${t.ordinal}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 3 } ?: defaults(t)
    fun save(p: SharedPreferences, t: Tool, l: List<Float>) = p.edit().putString("wp${t.ordinal}", l.joinToString(",")).apply()
    fun range(t: Tool) = when (t) { Tool.HIGHLIGHTER -> 8f..70f; Tool.ERASER -> 4f..80f; Tool.TAPE -> 12f..120f; else -> 0.3f..30f }
    fun step(t: Tool) = if (t == Tool.PEN) 0.1f else 0.5f
}

/** Ajuste fino: deslizador + botones de un paso + el número, que se puede tocar para escribir el valor exacto. */
@Composable
private fun Stepper(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, unit: String = "", curve: Float = 1f, onChange: (Float) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    fun snap(v: Float) = (Math.round(v / step) * step).coerceIn(range.start, range.endInclusive)
    val span = range.endInclusive - range.start
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Box(Modifier.bounceClick { typing = true }.clip(squircle(10)).background(Ars.PillFill).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(wfmt(value) + unit, style = MaterialTheme.typography.labelLarge) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onChange(snap(value - step)) }) { Text("–", style = MaterialTheme.typography.titleLarge) }
        Slider(Math.pow(((value - range.start) / span).coerceIn(0f, 1f).toDouble(), 1.0 / curve).toFloat(), { v -> onChange(snap(range.start + Math.pow(v.toDouble(), curve.toDouble()).toFloat() * span)) }, Modifier.weight(1f))
        IconButton({ onChange(snap(value + step)) }) { Icon(ArsIcons.Add, null) }
    }
    if (typing) { var t by remember { mutableStateOf(wfmt(value)) }
        AlertDialog({ typing = false }, title = { Text(label) }, text = { OutlinedTextField(t, { t = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, singleLine = true, shape = squircle(16),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)) },
            confirmButton = { TextButton({ t.replace(',', '.').toFloatOrNull()?.let { onChange(it.coerceIn(range.start, range.endInclusive)) }; typing = false }) { Text("Listo") } },
            dismissButton = { TextButton({ typing = false }) { Text("Cancelar") } }) }
}

private fun fmt(w: Float) = wfmt(w)

/** Trazo de muestra dibujado con el motor de tinta real: lo que ves es lo que escribirás. */
@Composable
fun StrokePreview(s: Slot, modifier: Modifier = Modifier, onPaper: Boolean = false) {
    val dark = Ars.dark && !onPaper
    Canvas(modifier) {
        val k = density; val w = size.width / k; val h = size.height / k; val n = 44
        val pts = FloatArray(n * 3)
        val m = (s.width * 0.6f).coerceAtMost(w * 0.2f) + 3f
        for (i in 0 until n) { val t = i / (n - 1f)
            pts[3 * i] = m + t * (w - 2 * m); pts[3 * i + 1] = h / 2 - kotlin.math.sin(t * 2 * Math.PI).toFloat() * h * 0.26f
            pts[3 * i + 2] = 0.22f + 0.68f * kotlin.math.sin(t * Math.PI).toFloat() }
        val style = when (s.tool) { Tool.HIGHLIGHTER -> 6; Tool.TAPE -> 11; else -> s.style }
        val color = if (s.tool == Tool.HIGHLIGHTER) (s.color and 0x00FFFFFF) or 0x66000000 else s.color
        drawContext.canvas.nativeCanvas.let { c -> c.save(); c.scale(k, k); com.arsnova.ink.Ink.darkPaper = dark
            com.arsnova.ink.Ink.drawLive(c, pts, n * 3, s.width, style, color, if (s.tool == Tool.TAPE) s.style else 0); c.restore() }
    }
}

/** Panel de la herramienta: muestra del trazo, tipo de punta, grosor, estabilización y opacidad con ajuste fino, favoritos y colores. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolSettings(s: Slot, swatches: List<Int>, shapesOn: Boolean, prefs: SharedPreferences, onShapes: (Boolean) -> Unit, onChange: (Slot) -> Unit, onFav: (() -> Unit)? = null, onPicker: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ink = s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER
    Surface(shape = squircle(30), shadowElevation = 16.dp, color = Ars.Glass, border = androidx.compose.foundation.BorderStroke(1.dp, Ars.GlassBorder), modifier = Modifier.padding(vertical = 10.dp).width(372.dp)) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(18.dp, 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                if (ink && onFav != null) Row(Modifier.bounceClick { onFav() }.clip(CircleShape).background(Ars.PillFill).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ArsIcons.Star, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text("Guardar favorito", style = MaterialTheme.typography.labelMedium) }
            }
            if (ink || s.tool == Tool.TAPE) Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(72.dp).clip(squircle(18)).background(Color(0xFFFCFCFA)).border(1.dp, Ars.GlassBorder, squircle(18))) {
                StrokePreview(s, Modifier.fillMaxSize(), onPaper = true) }
            if (s.tool == Tool.PEN) Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(70.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                Slots.penOrder.forEach { st -> ToolArt(s.copy(style = st), st == s.style, label = false) { onChange(s.copy(style = st)) } }
            }
            if (s.tool == Tool.ERASER || s.tool == Tool.TAPE) {
                val opts = if (s.tool == Tool.ERASER) listOf("Objeto", "Píxel") else listOf("Liso", "Puntos", "Cuadrícula", "Rayas")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    opts.forEachIndexed { i, l -> SegmentedButton(s.style == i, { onChange(s.copy(style = i)) }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(l, maxLines = 1) } }
                }
                if (s.tool == Tool.TAPE) Text("Dibuja la cinta sobre lo que quieras ocultar; tócala con esta herramienta para mostrar u ocultar.", style = MaterialTheme.typography.bodySmall, color = Ars.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            if (s.tool != Tool.LASSO && s.tool != Tool.LASER) {
                var presets by remember(s.tool) { mutableStateOf(Widths.load(prefs, s.tool)) }
                Stepper("Grosor", s.width, Widths.range(s.tool), Widths.step(s.tool), curve = 2.2f) { onChange(s.copy(width = it)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEachIndexed { i, w -> val on = kotlin.math.abs(w - s.width) < 0.05f
                        Row(Modifier.weight(1f).height(38.dp).clip(squircle(14)).background(if (on) Ars.Text else Ars.PillFill)
                            .combinedClickable(onClick = { onChange(s.copy(width = w)) }, onLongClick = { presets = presets.toMutableList().also { it[i] = s.width }; Widths.save(prefs, s.tool, presets) }),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.size((w * (if (s.tool == Tool.PEN) 1.8f else 0.4f)).coerceIn(3f, 22f).dp).clip(CircleShape).background(if (on) Ars.Bg else Ars.Text))
                            Spacer(Modifier.width(8.dp)); Text(wfmt(w), style = MaterialTheme.typography.labelMedium, color = if (on) Ars.Bg else Ars.Text) } }
                }
                Text("Mantén pulsado un tamaño para guardar el grosor actual", style = MaterialTheme.typography.labelSmall, color = Ars.Muted, modifier = Modifier.padding(top = 4.dp))
            }
            if (s.tool == Tool.ERASER) {
                var hold by remember { mutableStateOf(prefs.getBoolean("holdErase", true)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Column(Modifier.weight(1f)) { Text("Mantener para borrar", color = cs.onSurface)
                        Text("Con cualquier lápiz: apoya la punta sin moverla medio segundo y borra hasta que la levantes", style = MaterialTheme.typography.bodySmall, color = Ars.Muted) }
                    Switch(hold, { hold = it; prefs.edit().putBoolean("holdErase", it).apply() }) }
            }
            if (s.tool == Tool.TAPE || s.tool == Tool.LASER) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                listOf(0xFF7FB3D5, 0xFFF5B7B1, 0xFFA9DFBF, 0xFFF9E79F, 0xFFD7BDE2, 0xFFFF3B30, 0xFF1D1B18).map { it.toInt() }.forEach { c -> val on = c == s.color
                    Box(Modifier.size(30.dp).bounceClick { onChange(s.copy(color = c)) }.clip(CircleShape).background(Color(c)), contentAlignment = Alignment.Center) {
                        if (on) Icon(ArsIcons.Check, null, Modifier.size(16.dp), tint = Color.White) } }
            }
            if (ink) {
                Stepper("Estabilización", Math.round(s.stab * 100).toFloat(), 0f..100f, 1f, "%") { onChange(s.copy(stab = it / 100f)) }
                Stepper("Opacidad", Math.round((s.color ushr 24) / 2.55f).toFloat(), 10f..100f, 1f, "%") { onChange(s.copy(color = (s.color and 0x00FFFFFF) or ((it * 2.55f).toInt().coerceIn(20, 255) shl 24))) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) { Text("Enderezar líneas y figuras", Modifier.weight(1f), color = cs.onSurface); Switch(shapesOn, onShapes) }
                val quick = (swatches + listOf(0xFFFFFFFF, 0xFF8E8E93, 0xFFAF52DE, 0xFF5AC8FA).map { it.toInt() }).distinct().take(8)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                    quick.forEach { c -> val on = (c or 0xFF000000.toInt()) == (s.color or 0xFF000000.toInt())
                        Box(Modifier.size(30.dp).bounceClick { onChange(s.copy(color = (c and 0x00FFFFFF) or (s.color and 0xFF000000.toInt()))) }.clip(CircleShape).background(Color(c)).border(1.dp, Ars.GlassBorder, CircleShape), contentAlignment = Alignment.Center) {
                            if (on) Icon(ArsIcons.Check, null, Modifier.size(16.dp), tint = if (Color(c).luminance() > 0.6f) Color.Black else Color.White) } }
                    Box(Modifier.size(30.dp).bounceClick { onPicker() }.clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }; var sa by remember { mutableFloatStateOf(hsv[1]) }; var v by remember { mutableFloatStateOf(hsv[2]) }
    val cur = android.graphics.Color.HSVToColor(floatArrayOf(h, sa, v))
    AlertDialog(onDismiss, title = { Text("Colores") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BigPalette.chunked(12).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { c -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(Color(c)).clickable { onPick(c) }) } } }
            Spacer(Modifier.height(8.dp))
            Text("Tono"); Slider(h, { h = it }, valueRange = 0f..360f)
            Text("Saturación"); Slider(sa, { sa = it })
            Text("Brillo"); Slider(v, { v = it })
            Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(cur)))
        }
    }, confirmButton = { TextButton({ onPick(cur) }) { Text("Usar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}
