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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.graphics.drawscope.Stroke as DStroke
import androidx.compose.ui.unit.dp
import com.arsnova.data.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Estilos de tinta: 0 bolígrafo, 1 pluma, 2 lápiz, 3 pincel, 4 monolínea, 5 rotulador, 6 resaltador. */
val StyleNames = listOf("Bolígrafo", "Pluma de caligrafía", "Lápiz", "Pincel", "Monolínea", "Rotulador", "Resaltador")

@Serializable
data class Slot(val tool: Tool, val style: Int, val color: Int, val width: Float, val stab: Float = 0.2f) {
    val name get() = when (tool) { Tool.ERASER -> "Borrador"; Tool.LASSO -> "Lazo"; Tool.TEXT -> "Texto"; else -> StyleNames[style] }
}

object Slots {
    private val json = Json { ignoreUnknownKeys = true }
    val defaults = listOf(Slot(Tool.PEN, 0, 0xFF1D1B18.toInt(), 5f), Slot(Tool.HIGHLIGHTER, 6, 0xFFF2B705.toInt(), 28f, 0.5f),
        Slot(Tool.ERASER, 0, 0, 16f), Slot(Tool.LASSO, 0, 0, 0f))
    /** Orden de estilos de lápiz (uno de cada uno). */
    val penOrder = listOf(1, 0, 4, 2, 3, 5)
    fun load(p: SharedPreferences): List<Slot> = runCatching { json.decodeFromString<List<Slot>>(p.getString("slots", null)!!) }.getOrNull()?.takeIf { it.size == 4 } ?: defaults
    fun save(p: SharedPreferences, s: List<Slot>) = p.edit().putString("slots", json.encodeToString(s)).apply()
    fun loadSwatches(p: SharedPreferences): List<Int> = p.getString("swatches", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 5 }
        ?: listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30).map { it.toInt() }
    fun saveSwatches(p: SharedPreferences, s: List<Int>) = p.edit().putString("swatches", s.joinToString(",")).apply()
}

/** Paleta ampliada: 12 tonos × 6 luminosidades + grises. */
val BigPalette: List<Int> = buildList {
    for ((sa, v) in listOf(0.25f to 1f, 0.5f to 1f, 0.85f to 1f, 0.9f to 0.78f, 0.9f to 0.56f, 0.9f to 0.36f))
        for (h in 0 until 12) add(android.graphics.Color.HSVToColor(floatArrayOf(h * 30f, sa, v)))
    for (i in 0 until 12) { val g = (255 * i / 11f).toInt(); add(android.graphics.Color.rgb(g, g, g)) }
}

/** Iconos de lápices diseñados como vectores (viewBox 40×80, punta arriba). */
private object PenArt {
    private val raw = mapOf(
        "monoline" to listOf("M11 36 C11 30 17 14 18.6 9 L21.4 9 C23 14 29 30 29 36 Z" to "white", "M18.6 9 L21.4 9 L20.9 4.2 Q20 3.2 19.1 4.2 Z" to "ink"),
        "ballpoint" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "white", "M18 12 L22 12 L20.7 5.2 L19.3 5.2 Z" to "metal", "M19.3 5.2 L20.7 5.2 L20.3 3.6 L19.7 3.6 Z" to "ink"),
        "fountain" to listOf("M11 36 C12 31 14 28 15 26 L25 26 C26 28 28 31 29 36 Z" to "dark", "M15 26 C15 18 18.6 9.5 20 3.8 C21.4 9.5 25 18 25 26 Z" to "metal", "M19.6 6 L20.4 6 L20.4 18 L19.6 18 Z" to "dark", "M18.8 19.6 A1.2 1.2 0 1 0 21.2 19.6 A1.2 1.2 0 1 0 18.8 19.6 Z" to "dark", "M19.4 3.4 L20.6 3.4 L20.3 5.2 L19.7 5.2 Z" to "ink"),
        "pencil" to listOf("M11 36 L18.2 12.5 L21.8 12.5 L29 36 Q26.6 34.4 24.5 36 Q22.2 34.4 20 36 Q17.8 34.4 15.5 36 Q13.4 34.4 11 36 Z" to "wood", "M18.2 12.5 L20 5 L21.8 12.5 Z" to "ink"),
        "brush" to listOf("M13 25 L27 25 L28.5 36 L11.5 36 Z" to "metal", "M14 25.5 C13 18 18 8.5 20 3 C22 8.5 27 18 26 25.5 Z" to "ink"),
        "marker" to listOf("M11 36 L14 22 L26 22 L29 36 Z" to "white", "M15 22 L15.8 12.5 L24.2 9 L25 22 Z" to "ink"),
        "highlighter" to listOf("M11 36 L13 24 L27 24 L29 36 Z" to "white", "M13.6 24 L13.6 15.5 L26.4 10 L26.4 24 Z" to "inka"),
        "eraser" to listOf("M11 30 L11 17 Q11 8.5 20 8.5 Q29 8.5 29 17 L29 30 Z" to "pink", "M11 30 L29 30 L29 33 L11 33 Z" to "metal"),
        "lasso" to listOf("M11 36 C11 30 16.5 16 18 12 L22 12 C23.5 16 29 30 29 36 Z" to "white", "M18 12 L22 12 L20 5 Z" to "gray")
    )
    val parts: Map<String, List<Pair<Path, String>>> by lazy { raw.mapValues { (_, l) -> l.map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it.first).toPath() to it.second } } }
    val barrel by lazy { Path().apply { addRect(androidx.compose.ui.geometry.Rect(11f, 36f, 29f, 84f)) } }
    private fun g(vararg c: Long, x0: Float = 11f, x1: Float = 29f) = Brush.horizontalGradient(c.map { Color(it) }, x0, x1)
    val barrelB = g(0xFFDCD7CE, 0xFFFFFFFF, 0xFFF3F0EA, 0xFFCFC9BF)
    val mats = mapOf("white" to g(0xFFE2DED6, 0xFFFFFFFF, 0xFFD9D4CB), "metal" to g(0xFF8E8E93, 0xFFF2F2F7, 0xFF7C7C82, x0 = 13f, x1 = 27f),
        "wood" to g(0xFFD2A878, 0xFFF2D9B8, 0xFFC69A6B), "pink" to g(0xFFE8938F, 0xFFF9C2BE, 0xFFDE837F))
    fun key(s: Slot) = when (s.tool) { Tool.ERASER -> "eraser"; Tool.LASSO -> "lasso"; Tool.HIGHLIGHTER -> "highlighter"
        else -> when (s.style) { 1 -> "fountain"; 2 -> "pencil"; 3 -> "brush"; 4 -> "monoline"; 5 -> "marker"; else -> "ballpoint" } }
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

private val LassoIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder("lasso", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12 4C7 4 3 6.7 3 10s4 6 9 6 9-2.7 9-6-4-6-9-6z").toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round)
    addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M7 15.5c-1.2 1.2-1.4 3 .2 3.9 1.4.8 2.8-.3 2.3-1.8").toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round)
}.build()

/** Barra compacta estilo Samsung Notes: un botón por herramienta, colores y acciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleToolbar(
    slots: List<Slot>, active: Int, swatches: List<Int>, canUndo: Boolean, canRedo: Boolean,
    onSelect: (Int) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onColor: (Int) -> Unit, onEditSwatch: (Int) -> Unit,
    onPicker: () -> Unit, insertMenu: @Composable (Boolean, () -> Unit) -> Unit, onMore: () -> Unit, onCollapse: () -> Unit = {},
    dock: Int = 0, handle: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme; val dark = cs.background.luminance() < 0.3f
    var plus by remember { mutableStateOf(false) }
    val vertical = dock != 0
    val shape = RoundedCornerShape(26.dp)
    val cur = slots.getOrNull(active)?.color ?: slots[0].color
    @Composable fun Sep() = Box(Modifier.padding(4.dp).size(if (vertical) 22.dp else 1.dp, if (vertical) 1.dp else 22.dp).background(cs.outline.copy(alpha = 0.35f)))
    @Composable fun ToolBtn(i: Int) {
        val s = slots[i]; val on = i == active
        val bg by androidx.compose.animation.animateColorAsState(if (on) cs.onSurface.copy(alpha = 0.12f) else Color.Transparent, label = "tb")
        Box(Modifier.padding(2.dp).size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
            Icon(when (s.tool) { Tool.PEN -> Icons.Rounded.Create; Tool.HIGHLIGHTER -> Icons.Rounded.BorderColor; Tool.ERASER -> EraserIcon; Tool.LASSO -> LassoIcon; else -> Icons.Rounded.TextFields },
                s.name, tint = if (on) cs.onSurface else cs.onSurface.copy(alpha = 0.7f))
            if (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER) Box(Modifier.align(Alignment.BottomEnd).padding(7.dp).size(9.dp).clip(CircleShape).background(Color(s.color).copy(alpha = 1f)).border(1.dp, cs.surface, CircleShape))
        }
    }
    @Composable fun Dot(c: Int, k: Int) {
        val on = (c or 0xFF000000.toInt()) == (cur or 0xFF000000.toInt())
        Box(Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Color(c)).border(if (on) 2.5.dp else 1.dp, if (on) cs.onSurface else Color.Black.copy(alpha = 0.12f), CircleShape)
            .combinedClickable(onClick = { onColor(c) }, onLongClick = { onEditSwatch(k) }), contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = if (Color(c).luminance() > 0.6f) Color.Black else Color.White) }
    }
    @Composable fun Items() {
        IconButton(onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Deshacer") }
        IconButton(onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Rehacer") }
        Sep()
        slots.indices.forEach { ToolBtn(it) }
        Sep()
        (if (vertical) swatches.take(2) else swatches).forEachIndexed { k, c -> Dot(c, k) }
        Box(Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).clickable { onPicker() })
        Sep()
        Box { IconButton({ plus = true }) { Icon(Icons.Rounded.Add, "Insertar") }; insertMenu(plus) { plus = false } }
        IconButton(onMore) { Icon(Icons.Rounded.MoreHoriz, "Más") }
        IconButton(onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimizar", tint = cs.outline,
            modifier = Modifier.graphicsLayer { rotationZ = when (dock) { 1 -> 90f; -1 -> -90f; else -> 0f } }) }
    }
    val grip = @Composable { Box(handle.size(if (vertical) 48.dp else 26.dp, if (vertical) 26.dp else 48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (vertical) 24.dp else 4.dp, if (vertical) 4.dp else 24.dp).clip(CircleShape).background(cs.outline.copy(alpha = 0.55f))) } }
    Surface(shape = shape, color = cs.surface.copy(alpha = 0.92f), shadowElevation = 12.dp,
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = if (dark) 0.08f else 0.7f), shape)) {
        if (vertical) Column(Modifier.padding(vertical = 6.dp).widthIn(max = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            grip(); Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { Items() } }
        else Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            grip(); Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { Items() } }
    }
}

/** Panel del lápiz estilo Samsung Notes: tipos de punta, grosor con burbuja, colores con check. */
@Composable
fun ToolSettings(s: Slot, swatches: List<Int>, shapesOn: Boolean, onShapes: (Boolean) -> Unit, onChange: (Slot) -> Unit, onPicker: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 16.dp, color = cs.surface.copy(alpha = 0.96f), modifier = Modifier.padding(bottom = 10.dp).width(360.dp)) {
        Column(Modifier.padding(18.dp, 14.dp)) {
            if (s.tool == Tool.PEN) {
                Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    Slots.penOrder.forEach { st -> ToolArt(s.copy(style = st), st == s.style, label = false) { onChange(s.copy(style = st)) } }
                }
                HorizontalDivider(color = cs.outline.copy(alpha = 0.25f))
                Text(s.name, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, modifier = Modifier.padding(top = 10.dp))
            } else Text(s.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            if (s.tool != Tool.LASSO) {
                val range = when (s.tool) { Tool.HIGHLIGHTER -> 10f..70f; Tool.ERASER -> 6f..80f; else -> 1f..40f }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    IconButton({ onChange(s.copy(width = (s.width - 1).coerceIn(range))) }) { Text("—", color = cs.onSurface) }
                    Slider(s.width, { onChange(s.copy(width = it)) }, Modifier.weight(1f), valueRange = range)
                    IconButton({ onChange(s.copy(width = (s.width + 1).coerceIn(range))) }) { Icon(Icons.Rounded.Add, null) }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Text(s.width.toInt().toString(), style = MaterialTheme.typography.labelLarge, color = cs.onSurface) }
                }
            }
            if (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Estabilización", Modifier.weight(1f), color = cs.onSurface); Text("${(s.stab * 100).toInt()}%", color = cs.outline) }
                Slider(s.stab, { onChange(s.copy(stab = it)) }, steps = 9)
                val op = ((s.color ushr 24) / 255f)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Opacidad", Modifier.weight(1f), color = cs.onSurface); Text("${(op * 100).toInt()}%", color = cs.outline) }
                Slider(op, { onChange(s.copy(color = (s.color and 0x00FFFFFF) or ((it * 255).toInt().coerceIn(20, 255) shl 24))) }, valueRange = 0.1f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enderezar líneas", Modifier.weight(1f), color = cs.onSurface); Switch(shapesOn, onShapes) }
                val quick = (swatches + listOf(0xFFFFFFFF, 0xFF8E8E93, 0xFFAF52DE, 0xFF5AC8FA).map { it.toInt() }).distinct().take(9)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    quick.forEach { c -> val on = (c or 0xFF000000.toInt()) == (s.color or 0xFF000000.toInt())
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                            .clickable { onChange(s.copy(color = (c and 0x00FFFFFF) or (s.color and 0xFF000000.toInt()))) }, contentAlignment = Alignment.Center) {
                            if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = if (Color(c).luminance() > 0.6f) Color.Black else Color.White) } }
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).clickable { onPicker() })
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
