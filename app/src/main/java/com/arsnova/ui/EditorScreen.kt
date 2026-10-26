package com.arsnova.ui

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.arsnova.data.*
import com.arsnova.ink.Ink as InkR
import com.arsnova.ink.InkView
import kotlinx.coroutines.delay
import java.io.File

val EraserIcon = ImageVector.Builder("eraser", 24.dp, 24.dp, 24f, 24f).path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
    moveTo(16.24f, 3.56f); lineTo(21.19f, 8.5f); curveTo(21.97f, 9.29f, 21.97f, 10.55f, 21.19f, 11.34f)
    lineTo(12f, 20.53f); horizontalLineTo(20f); verticalLineTo(22.5f); horizontalLineTo(8f); lineTo(2.81f, 17.3f)
    curveTo(2.03f, 16.52f, 2.03f, 15.26f, 2.81f, 14.47f); lineTo(13.41f, 3.56f); curveTo(14.2f, 2.78f, 15.46f, 2.78f, 16.24f, 3.56f); close()
    moveTo(4.22f, 15.89f); lineTo(8.81f, 20.5f); horizontalLineTo(9.1f); lineTo(12.6f, 17f); lineTo(7.7f, 12.1f); close()
}.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(repo: Repo, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ars", Context.MODE_PRIVATE) }
    val nb = remember(id) { repo.load(id) }
    var pageIdx by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableIntStateOf(PenColors[0]) }
    var penW by remember { mutableFloatStateOf(prefs.getFloat("penW", 5f)) }
    var hlW by remember { mutableFloatStateOf(prefs.getFloat("hlW", 28f)) }
    var stylusOnly by remember { mutableStateOf(prefs.getBoolean("stylusOnly", false)) }
    var showWidth by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var hasSel by remember { mutableStateOf(false) }
    var dirty by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var wheelAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var learning by remember { mutableStateOf(false) }
    var learned by remember { mutableIntStateOf(prefs.getInt("tapCode", -1)) }

    val view = remember { InkView(ctx) }
    val deskColor = if (androidx.compose.foundation.isSystemInDarkTheme()) 0xFF151413.toInt() else InkView.DESK
    fun save() = repo.saveAsync(nb, InkR.render(nb.pages[0], 0.25f))

    SideEffect {
        view.onState = { u, r, s -> canUndo = u; canRedo = r; hasSel = s }
        view.onChange = { dirty++ }
        view.onWheel = { x, y -> wheelAt = if (wheelAt == null) androidx.compose.ui.geometry.Offset(x, y) else null }
        view.onStylusButton = { code ->
            if (learning) { learned = code; prefs.edit().putInt("tapCode", code).apply(); learning = false; true } else false
        }
        view.page = nb.pages[pageIdx]; view.tool = tool; view.color = color
        view.desk = deskColor
        view.penWidth = penW; view.hlWidth = hlW; view.stylusOnly = stylusOnly
    }
    LaunchedEffect(dirty) { if (dirty > 0) { delay(1200); save() } }
    DisposableEffect(Unit) {
        StylusKeys.handler = { e ->
            val c = e.keyCode
            val match = (learning && e.action == android.view.KeyEvent.ACTION_DOWN && c !in setOf(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_VOLUME_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) ||
                c == learned || c in StylusKeys.NATIVE
            if (!match) false else {
                if (e.action == android.view.KeyEvent.ACTION_UP) {
                    if (learning) { learned = c; prefs.edit().putInt("tapCode", c).apply(); learning = false }
                    else wheelAt = if (wheelAt == null) androidx.compose.ui.geometry.Offset(
                        if (view.hoverX >= 0) view.hoverX else view.width / 2f, if (view.hoverY >= 0) view.hoverY else view.height / 2f) else null
                }
                true
            }
        }
        onDispose { StylusKeys.handler = null
 save(); prefs.edit().putFloat("penW", penW).putFloat("hlW", hlW).putBoolean("stylusOnly", stylusOnly).apply() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(nb.title, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(view::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
                    IconButton(view::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
                    IconButton({ if (pageIdx > 0) pageIdx-- }, enabled = pageIdx > 0) { Icon(Icons.Outlined.ChevronLeft, "Anterior") }
                    Text("${pageIdx + 1}/${nb.pages.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton({ if (pageIdx < nb.pages.size - 1) pageIdx++ }, enabled = pageIdx < nb.pages.size - 1) { Icon(Icons.Outlined.ChevronRight, "Siguiente") }
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Más") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Añadir página") }, { nb.pages.add(pageIdx + 1, Page(System.nanoTime(), nb.pages[pageIdx].template)); pageIdx++; dirty++; menu = false },
                            leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) })
                        if (nb.pages.size > 1) DropdownMenuItem({ Text("Eliminar página") }, {
                            nb.pages.removeAt(pageIdx); pageIdx = pageIdx.coerceAtMost(nb.pages.size - 1); dirty++; menu = false
                        }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                        DropdownMenuItem({ Text("Plantilla: ${nb.pages[pageIdx].template.label()}") }, {
                            val p = nb.pages[pageIdx]; p.template = Template.entries[(p.template.ordinal + 1) % Template.entries.size]
                            view.fit(); dirty++
                        }, leadingIcon = { Icon(Icons.Outlined.GridOn, null) })
                        DropdownMenuItem({ Text("Solo lápiz (rechazo de palma)") }, { stylusOnly = !stylusOnly },
                            leadingIcon = { Icon(Icons.Outlined.Draw, null) }, trailingIcon = { Checkbox(stylusOnly, null) })
                        DropdownMenuItem({ Text("Configurar doble toque del lápiz") }, { learning = true; menu = false },
                            leadingIcon = { Icon(Icons.Outlined.TouchApp, null) })
                        DropdownMenuItem({ Text("Ajustar a pantalla") }, { view.fit(); menu = false }, leadingIcon = { Icon(Icons.Outlined.FitScreen, null) })
                        DropdownMenuItem({ Text("Exportar PDF") }, { exportPdf(ctx, nb); menu = false }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView({ view }, Modifier.fillMaxSize())
            if (learning) AlertDialog({ learning = false }, confirmButton = { TextButton({ learning = false }) { Text("Cancelar") } },
                title = { Text("Doble toque") }, text = { Text("Haz doble toque en tu lápiz ahora (o pulsa su botón). ArsNova lo usará para abrir la rueda de herramientas.") })
            wheelAt?.let { at ->
                ToolWheel(at, tool, color, { tool = it; wheelAt = null }, { color = it; if (hasSel) view.recolorSelection(it); wheelAt = null },
                    view::undo, view::redo) { wheelAt = null }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(showWidth && (tool == Tool.PEN || tool == Tool.HIGHLIGHTER)) {
                    Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp, modifier = Modifier.padding(bottom = 10.dp).width(260.dp)) {
                        Column(Modifier.padding(16.dp, 8.dp)) {
                            Text("Grosor", style = MaterialTheme.typography.labelMedium)
                            if (tool == Tool.PEN) Slider(penW, { penW = it }, valueRange = 1.5f..24f)
                            else Slider(hlW, { hlW = it }, valueRange = 10f..60f)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 10.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        @Composable fun T(t: Tool, icon: ImageVector, d: String) {
                            val on = tool == t
                            IconButton({ if (on) showWidth = !showWidth else { tool = t; showWidth = false } },
                                Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent)) {
                                Icon(icon, d, tint = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        T(Tool.PEN, Icons.Outlined.Draw, "Bolígrafo")
                        T(Tool.HIGHLIGHTER, Icons.Outlined.BorderColor, "Resaltador")
                        T(Tool.ERASER, EraserIcon, "Borrador")
                        T(Tool.LASSO, Icons.Outlined.Gesture, "Lazo")
                        IconButton({ wheelAt = androidx.compose.ui.geometry.Offset(view.width / 2f, view.height / 2f) }) { Icon(Icons.Outlined.DonutLarge, "Rueda") }
                        VerticalDivider(Modifier.height(28.dp).padding(horizontal = 8.dp))
                        PenColors.forEach { c ->
                            Box(Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Color(c))
                                .then(if (c == color) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                .clickable { color = c; if (hasSel) view.recolorSelection(c) })
                        }
                        if (hasSel) {
                            VerticalDivider(Modifier.height(28.dp).padding(horizontal = 8.dp))
                            IconButton(view::deleteSelection) { Icon(Icons.Outlined.Delete, "Eliminar selección", tint = Brand) }
                        }
                    }
                }
            }
        }
    }
}

private fun Template.label() = when (this) { Template.BLANK -> "en blanco"; Template.LINED -> "rayado"; Template.GRID -> "cuadrícula"; Template.DOTS -> "puntos" }

private fun exportPdf(ctx: Context, nb: Notebook) {
    val doc = PdfDocument(); val s = 595f / InkR.PAGE_W
    nb.pages.forEachIndexed { i, p ->
        val pg = doc.startPage(PdfDocument.PageInfo.Builder(595, (InkR.PAGE_H * s).toInt(), i + 1).create())
        pg.canvas.scale(s, s); InkR.drawPage(pg.canvas, p); doc.finishPage(pg)
    }
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, nb.title.replace(Regex("[^\\w\\- ]"), "_") + ".pdf")
    f.outputStream().use { doc.writeTo(it) }; doc.close()
    val uri = FileProvider.getUriForFile(ctx, "com.arsnova.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Exportar PDF"))
}
