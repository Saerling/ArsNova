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
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
    val defaults = listOf(
        Slot(Tool.PEN, 1, 0xFF1D1B18.toInt(), 6f), Slot(Tool.PEN, 0, 0xFF2D5BD9.toInt(), 4f), Slot(Tool.PEN, 2, 0xFF55524D.toInt(), 5f),
        Slot(Tool.PEN, 3, 0xFFD9481F.toInt(), 12f), Slot(Tool.PEN, 5, 0xFF1F8A5B.toInt(), 8f), Slot(Tool.HIGHLIGHTER, 6, 0xFFF2B705.toInt(), 28f, 0.5f),
        Slot(Tool.ERASER, 0, 0, 16f), Slot(Tool.LASSO, 0, 0, 0f),
    )
    fun load(p: SharedPreferences): List<Slot> = runCatching { json.decodeFromString<List<Slot>>(p.getString("slots", null)!!) }.getOrDefault(defaults)
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

private fun DrawScope.toolArt(s: Slot, dark: Boolean) {
    val w = size.width; val bw = w * 0.52f; val x0 = (w - bw) / 2; val x1 = x0 + bw; val cx = w / 2
    val top = size.height * 0.42f; val tipH = size.height * 0.30f
    val ink = Color(s.color).copy(alpha = 1f)
    val line = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.28f)
    val halo = DStroke(1.2.dp.toPx(), join = StrokeJoin.Round)
    val barrel = Brush.horizontalGradient(listOf(Color(0xFFD9D5CD), Color(0xFFFFFFFF), Color(0xFFF2EFE9), Color(0xFFCFCAC1)), x0, x1)
    fun barrelAt(y: Float) { drawRoundRect(barrel, Offset(x0, y), Size(bw, size.height), CornerRadius(3.dp.toPx())); drawRoundRect(line, Offset(x0, y), Size(bw, size.height), CornerRadius(3.dp.toPx()), style = halo) }
    fun shape(p: Path, b: Brush) { drawPath(p, b); drawPath(p, line, style = halo) }
    fun shape(p: Path, c: Color) { drawPath(p, c); drawPath(p, line, style = halo) }
    when (s.tool) {
        Tool.ERASER -> { val p = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x0, top - tipH * 0.7f, x1, top + 6.dp.toPx(), CornerRadius(bw / 2, bw / 2), CornerRadius(bw / 2, bw / 2), CornerRadius.Zero, CornerRadius.Zero)) }
            shape(p, Brush.verticalGradient(listOf(Color(0xFFF7B5B5), Color(0xFFE88E8E)))); barrelAt(top + 6.dp.toPx()); return }
        Tool.LASSO -> { barrelAt(top)
            val p = Path().apply { moveTo(x0, top); lineTo(cx, top - tipH); lineTo(x1, top); close() }; shape(p, Color(0xFFEDEAE4))
            drawLine(line, Offset(cx, top - tipH), Offset(cx, top - tipH * 0.2f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))); return }
        else -> {}
    }
    val cone = Path().apply { moveTo(x0, top); lineTo(cx - bw * 0.14f, top - tipH); lineTo(cx + bw * 0.14f, top - tipH); lineTo(x1, top); close() }
    when (s.style) {
        1 -> { val nib = Path().apply { moveTo(x0 + bw * 0.08f, top); cubicTo(x0, top - tipH * 0.5f, cx - bw * 0.1f, top - tipH * 0.85f, cx, top - tipH * 1.05f)
                cubicTo(cx + bw * 0.1f, top - tipH * 0.85f, x1, top - tipH * 0.5f, x1 - bw * 0.08f, top); close() }
            shape(nib, Brush.horizontalGradient(listOf(Color(0xFF8E8E93), Color(0xFFF2F2F7), Color(0xFF8E8E93)), x0, x1))
            drawLine(Color(0xFF3A3A3C), Offset(cx, top - tipH * 1.02f), Offset(cx, top - tipH * 0.35f), 1.dp.toPx())
            drawCircle(Color(0xFF3A3A3C), 1.6.dp.toPx(), Offset(cx, top - tipH * 0.32f)); drawCircle(ink, 2.dp.toPx(), Offset(cx, top - tipH * 1.02f)) }
        2 -> { shape(cone, Brush.horizontalGradient(listOf(Color(0xFFD9B48A), Color(0xFFF3DCBE), Color(0xFFCFA67A)), x0, x1))
            shape(Path().apply { moveTo(cx - bw * 0.22f, top - tipH * 0.6f); lineTo(cx, top - tipH - 1.dp.toPx()); lineTo(cx + bw * 0.22f, top - tipH * 0.6f); close() }, ink) }
        3 -> { drawRect(Brush.horizontalGradient(listOf(Color(0xFF9A9A9A), Color(0xFFE5E5E5), Color(0xFF8A8A8A)), x0, x1), Offset(x0 + bw * 0.08f, top - tipH * 0.35f), Size(bw * 0.84f, tipH * 0.35f))
            shape(Path().apply { moveTo(x0 + bw * 0.12f, top - tipH * 0.35f); cubicTo(x0 + bw * 0.1f, top - tipH * 0.9f, cx - 1f, top - tipH * 1.1f, cx, top - tipH * 1.2f)
                cubicTo(cx + 1f, top - tipH * 1.1f, x1 - bw * 0.1f, top - tipH * 0.9f, x1 - bw * 0.12f, top - tipH * 0.35f); close() }, ink) }
        5, 6 -> { shape(Path().apply { moveTo(x0, top); lineTo(x0 + bw * 0.12f, top - tipH * 0.45f); lineTo(x1 - bw * 0.12f, top - tipH * 0.45f); lineTo(x1, top); close() }, Color(0xFFEDEAE4))
            shape(Path().apply { moveTo(x0 + bw * 0.14f, top - tipH * 0.45f); lineTo(x0 + bw * (if (s.style == 6) 0.14f else 0.3f), top - tipH * 0.95f); lineTo(x1 - bw * 0.14f, top - tipH * (if (s.style == 6) 1.15f else 0.95f)); lineTo(x1 - bw * 0.14f, top - tipH * 0.45f); close() }, ink) }
        4 -> { shape(cone, Brush.horizontalGradient(listOf(Color(0xFFE5E2DC), Color.White, Color(0xFFDAD6CF)), x0, x1))
            drawLine(ink, Offset(cx, top - tipH), Offset(cx, top - tipH * 0.75f), 2.2.dp.toPx(), StrokeCap.Round) }
        else -> { shape(cone, Brush.horizontalGradient(listOf(Color(0xFFE5E2DC), Color.White, Color(0xFFDAD6CF)), x0, x1))
            drawCircle(Color(0xFF8E8E93), 1.8.dp.toPx(), Offset(cx, top - tipH)); drawCircle(ink, 1.2.dp.toPx(), Offset(cx, top - tipH)) }
    }
    barrelAt(top)
    drawRect(ink, Offset(x0, top + 10.dp.toPx()), Size(bw, 4.dp.toPx()))
    if (s.style == 2) drawRect(Color(0xFF2C2C2E), Offset(x0, top + 16.dp.toPx()), Size(bw, 1.5.dp.toPx()))
}

@Composable
fun ToolArt(s: Slot, sel: Boolean, label: Boolean = true, onClick: () -> Unit) {
    val lift by animateDpAsState(if (sel) 0.dp else 14.dp, spring(dampingRatio = 0.55f, stiffness = 380f), label = "lift")
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    Box(Modifier.width(40.dp).height(60.dp).clipToBounds().clickable(onClick = onClick)) {
        Canvas(Modifier.fillMaxSize().offset(y = lift)) { toolArt(s, dark) }
        if (label && sel && (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER))
            Text(s.width.toInt().toString(), Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF3A3A3C))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleToolbar(
    slots: List<Slot>, active: Int, swatches: List<Int>, canUndo: Boolean, canRedo: Boolean,
    onSelect: (Int) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onColor: (Int) -> Unit, onEditSwatch: (Int) -> Unit,
    onPicker: () -> Unit, insertMenu: @Composable (Boolean, () -> Unit) -> Unit, onMore: () -> Unit, onCollapse: () -> Unit = {},
    dock: Int = 0, handle: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    var plus by remember { mutableStateOf(false) }
    val vertical = dock != 0
    val shape = RoundedCornerShape(30.dp)
    @Composable fun Items() {
        Box(handle.size(if (vertical) 40.dp else 28.dp, if (vertical) 28.dp else 40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(if (vertical) 22.dp else 4.dp, if (vertical) 4.dp else 22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
        }
        IconButton(onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
        IconButton(onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
        slots.forEachIndexed { i, s ->
            if (vertical) Box(Modifier.size(60.dp, 40.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.requiredSize(40.dp, 60.dp).graphicsLayer { rotationZ = if (dock > 0) 90f else -90f }) { ToolArt(s, i == active, label = false) { onSelect(i) } }
            } else Box(Modifier.padding(top = 6.dp)) { ToolArt(s, i == active) { onSelect(i) } }
        }
        Spacer(Modifier.size(8.dp))
        val cur = slots.getOrNull(active)?.color ?: 0
        val rows = if (vertical) 3 else 2; val cols = if (vertical) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(6.dp)) {
            for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (cIdx in 0 until cols) {
                    val k = r * cols + cIdx
                    if (k < 5) {
                        val c = swatches[k]; val on = (c or 0xFF000000.toInt()) == (cur or 0xFF000000.toInt())
                        val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.5f), label = "sw")
                        Box(Modifier.size(24.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(Color(c))
                            .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .combinedClickable(onClick = { onColor(c) }, onLongClick = { onEditSwatch(k) }))
                    } else Box(Modifier.size(24.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).clickable { onPicker() })
                }
            }
        }
        Box { IconButton({ plus = true }) { Icon(Icons.Outlined.Add, "Insertar") }; insertMenu(plus) { plus = false } }
        IconButton(onMore) { Icon(Icons.Outlined.MoreHoriz, "Más") }
        IconButton(onCollapse, Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "Minimizar", tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.graphicsLayer { rotationZ = when (dock) { 1 -> 90f; -1 -> -90f; else -> 0f } }) }
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), shadowElevation = 14.dp,
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.7f), shape)) {
        if (vertical) Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Items() }
        else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { Items() }
    }
}

/** Panel de ajustes del lápiz activo (estilo Huawei/Samsung). */
@Composable
fun ToolSettings(s: Slot, swatches: List<Int>, shapesOn: Boolean, onShapes: (Boolean) -> Unit, onChange: (Slot) -> Unit, onPicker: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 12.dp, modifier = Modifier.padding(bottom = 12.dp).widthIn(max = 380.dp)) {
        Column(Modifier.padding(18.dp, 14.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (s.tool == Tool.PEN) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(1, 0, 2, 3, 4, 5).forEach { st -> ToolArt(s.copy(style = st), st == s.style) { onChange(s.copy(style = st)) } }
            }
            if (s.tool != Tool.LASSO) {
                val mm = if (s.tool == Tool.ERASER) "${s.width.toInt()} px" else "%.2f mm".format(s.width * 0.15f)
                Row { Text("Espesor", Modifier.weight(1f)); Text(mm, color = MaterialTheme.colorScheme.outline) }
                Slider(s.width, { onChange(s.copy(width = it)) }, valueRange = when (s.tool) { Tool.HIGHLIGHTER -> 10f..70f; Tool.ERASER -> 6f..80f; else -> 1f..40f })
            }
            if (s.tool == Tool.PEN || s.tool == Tool.HIGHLIGHTER) {
                Row { Text("Estabilización de trazo", Modifier.weight(1f)); Text("${(s.stab * 100).toInt()}%", color = MaterialTheme.colorScheme.outline) }
                Slider(s.stab, { onChange(s.copy(stab = it)) }, steps = 9)
                val op = ((s.color ushr 24) / 255f)
                Row { Text("Opacidad", Modifier.weight(1f)); Text("${(op * 100).toInt()}%", color = MaterialTheme.colorScheme.outline) }
                Slider(op, { onChange(s.copy(color = (s.color and 0x00FFFFFF) or ((it * 255).toInt().coerceIn(20, 255) shl 24))) }, valueRange = 0.1f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enderezar líneas y figuras", Modifier.weight(1f)); Switch(shapesOn, onShapes) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    swatches.forEach { c -> Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(c)).clickable { onChange(s.copy(color = (c and 0x00FFFFFF) or (s.color and 0xFF000000.toInt()))) }) }
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).clickable { onPicker() })
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
