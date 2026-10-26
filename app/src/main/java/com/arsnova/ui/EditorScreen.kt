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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.arsnova.data.*
import com.arsnova.ink.Ink as InkR
import com.arsnova.ink.InkView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val view = remember { InkView(ctx) }
    var editing by remember { mutableStateOf<Pair<Item, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    val pickImg = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                var ss = 1; while (maxOf(o.outWidth, o.outHeight) / ss > 2000) ss *= 2
                val b = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = ss }) }!!
                val name = "${nb.id}-img${System.currentTimeMillis()}.jpg"
                File(repo.dir, name).outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                Triple(name, b.width, b.height)
            }.getOrNull() }
            r?.let { (n, w, h) -> view.addImage(n, w, h) }
        }
    }
    val audio = remember { AudioCtl(ctx, repo.dir, nb) }
    val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) runCatching { audio.startRec() } }
    var wheelAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var learning by remember { mutableStateOf(false) }
    var learned by remember { mutableIntStateOf(prefs.getInt("tapCode", -1)) }

    val deskColor = if (androidx.compose.foundation.isSystemInDarkTheme()) 0xFF151413.toInt() else InkView.DESK
    fun save() = repo.saveAsync(nb, InkR.render(nb.pages[0], 0.25f))
    var indexed by remember { mutableIntStateOf(-1) }
    var pages by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    // Indexa el texto manuscrito de la página actual en segundo plano (búsqueda).
    LaunchedEffect(dirty, pageIdx) { delay(4000); val p = nb.pages[pageIdx]; val t = com.arsnova.ink.Recognizer.recognize(p.strokes.toList()); if (t != p.ocr) { p.ocr = t; save() } }

    SideEffect {
        view.onState = { u, r, s -> canUndo = u; canRedo = r; hasSel = s }
        view.onChange = { dirty++ }
        view.onTextEdit = { it, isNew -> editing = it to isNew }
        view.tapMode = audio.open
        view.replayTime = audio.replayTime()
        view.onStrokeTap = { s -> audio.jumpTo(s.t) }
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
        onDispose { StylusKeys.handler = null; audio.release()
 save(); prefs.edit().putFloat("penW", penW).putFloat("hlW", hlW).putBoolean("stylusOnly", stylusOnly).apply() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(nb.title, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") } },
                actions = {
                    IconButton({ if (audio.recording) { audio.stopRec(); dirty++ } else micPerm.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                        Icon(if (audio.recording) Icons.Outlined.StopCircle else Icons.Outlined.Mic, "Grabar", tint = if (audio.recording) Brand else LocalContentColor.current) }
                    if (nb.recs.isNotEmpty()) IconButton({ audio.open = !audio.open }) { Icon(Icons.Outlined.Headphones, "Reproducir") }
                    IconButton(view::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
                    IconButton(view::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
                    IconButton({ if (pageIdx > 0) pageIdx-- }, enabled = pageIdx > 0) { Icon(Icons.Outlined.ChevronLeft, "Anterior") }
                    TextButton({ pages = true }) { Text("${pageIdx + 1}/${nb.pages.size}", style = MaterialTheme.typography.labelLarge) }
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
            if (pages) PageGrid(nb, pageIdx, { pageIdx = it; pages = false }, { nb.pages.add(Page(System.nanoTime(), nb.pages.last().template)); pageIdx = nb.pages.size - 1; dirty++; pages = false }) { pages = false }
            editing?.let { (item, isNew) ->
                var txt by remember(item) { mutableStateOf(item.text) }
                var size by remember(item) { mutableFloatStateOf(item.size) }
                AlertDialog({ editing = null }, title = { Text(if (isNew) "Nuevo texto" else "Editar texto") },
                    text = { Column {
                        OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), minLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            listOf(28f to "S", 40f to "M", 60f to "L", 88f to "XL").forEach { (v, l) -> FilterChip(size == v, { size = v }, { Text(l) }) }
                        }
                    } },
                    confirmButton = { TextButton({ item.size = size; view.commitText(item, txt, isNew); editing = null }) { Text("Listo") } },
                    dismissButton = { TextButton({ editing = null }) { Text("Cancelar") } })
            }
            if (learning) AlertDialog({ learning = false }, confirmButton = { TextButton({ learning = false }) { Text("Cancelar") } },
                title = { Text("Doble toque") }, text = { Text("Haz doble toque en tu lápiz ahora (o pulsa su botón). ArsNova lo usará para abrir la rueda de herramientas.") })
            wheelAt?.let { at ->
                ToolWheel(at, tool, color, { tool = it; wheelAt = null }, { color = it; if (hasSel) view.recolorSelection(it); wheelAt = null },
                    view::undo, view::redo) { wheelAt = null }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(audio.open) { AudioBar(audio, nb.recs) }
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
                        T(Tool.TEXT, Icons.Outlined.TextFields, "Texto")
                        IconButton({ pickImg.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Outlined.Image, "Imagen") }
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
                            IconButton({ converting = true; scope.launch {
                                val t = com.arsnova.ink.Recognizer.recognize(view.selectedStrokes())
                                converting = false
                                if (t.isBlank()) android.widget.Toast.makeText(ctx, "No se reconoció texto (se necesita el modelo, ~20 MB, con internet la primera vez)", android.widget.Toast.LENGTH_LONG).show()
                                else view.replaceSelectionWithText(t)
                            } }, enabled = !converting) { Icon(Icons.Outlined.TextFields, "Convertir a texto") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageGrid(nb: Notebook, cur: Int, onPick: (Int) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.heightIn(max = 560.dp)) {
            items(nb.pages.size) { i ->
                val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, i) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { InkR.render(nb.pages[i], 0.1f).asImageBitmap() } }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(i) }) {
                    Box(Modifier.aspectRatio(0.75f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White)
                        .border(if (i == cur) 3.dp else 1.dp, if (i == cur) Brand else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))) {
                        bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) }
                    }
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Box(Modifier.aspectRatio(0.75f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).clickable { onAdd() },
                    contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Añadir página") }
            }
        }
    }
}
