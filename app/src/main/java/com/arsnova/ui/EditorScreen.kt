package com.arsnova.ui

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var stylusOnly by remember { mutableStateOf(prefs.getBoolean("stylusOnly", false)) }
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
    var slots by remember { mutableStateOf(Slots.load(prefs)) }
    var active by remember { mutableIntStateOf(prefs.getInt("slot", 0).coerceIn(0, Slots.defaults.size - 1)) }
    var swatches by remember { mutableStateOf(Slots.loadSwatches(prefs)) }
    var textTool by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Int?>(null) } // -1 = color actual, 0..4 = editar muestra
    var shapesOn by remember { mutableStateOf(prefs.getBoolean("shapes", true)) }
    val slot = slots[active]
    val tool = if (textTool) Tool.TEXT else slot.tool
    val color = slot.color
    fun updSlot(s: Slot) { slots = slots.toMutableList().also { it[active] = s }; Slots.save(prefs, slots) }
    fun pick(i: Int) { textTool = false; if (i == active) showSettings = !showSettings else { active = i; showSettings = false; prefs.edit().putInt("slot", i).apply() } }
    fun setColor(c: Int) { if (slot.tool == Tool.PEN || slot.tool == Tool.HIGHLIGHTER) updSlot(slot.copy(color = (c and 0x00FFFFFF) or (slot.color and 0xFF000000.toInt()).let { if (it == 0) 0xFF000000.toInt() else it })) else { val k = slots.indexOfFirst { it.tool == Tool.PEN }; active = k; updSlot(slots[k].copy(color = c)) }; if (hasSel) view.recolorSelection(c) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var prevSlot by remember { mutableIntStateOf(0) }
    fun onDoubleTap() {
        val now = System.currentTimeMillis(); if (now - lastTap < 450) return; lastTap = now
        when (prefs.getInt("tapAction", 0)) {
            0 -> wheelAt = if (wheelAt == null) androidx.compose.ui.geometry.Offset(if (view.hoverX >= 0) view.hoverX else view.width / 2f, if (view.hoverY >= 0) view.hoverY else view.height / 2f) else null
            1 -> { val er = slots.indexOfFirst { it.tool == Tool.ERASER }; if (active == er) active = prevSlot else { prevSlot = active; active = er } }
            2 -> view.undo()
        }
    }
    fun save() = repo.saveAsync(nb, InkR.render(nb.pages[0], 0.25f))
    var indexed by remember { mutableIntStateOf(-1) }
    var pages by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var aiOut by remember { mutableStateOf<String?>(null) }
    var askKey by remember { mutableStateOf(false) }
    fun runAi() {
        val key = prefs.getString("aiKey", null); if (key.isNullOrBlank()) { askKey = true; return }
        aiOut = "…"
        scope.launch {
            val text = nb.pages.joinToString("\n\n") { p -> (if (p.ocr.isBlank()) "" else p.ocr) + p.items.joinToString("\n", "\n") { it.text } }
                .ifBlank { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { "" } }
            val src = if (text.isBlank()) com.arsnova.ink.Recognizer.recognize(nb.pages.flatMap { it.strokes }) else text
            aiOut = if (src.isBlank()) "No hay texto reconocible en este cuaderno todavía."
                else runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Ai.ask(key, Ai.studyPrompt(src)) } }.getOrElse { "Error: ${it.message}" }
        }
    }
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
            if (learning) { learned = code; prefs.edit().putInt("tapCode", code).apply(); learning = false } else onDoubleTap(); true
        }
        view.page = nb.pages[pageIdx]; view.tool = tool; view.color = color
        view.desk = deskColor
        view.penWidth = if (slot.tool == Tool.PEN) slot.width else view.penWidth
        view.hlWidth = if (slot.tool == Tool.HIGHLIGHTER) slot.width else view.hlWidth
        if (slot.tool == Tool.ERASER) view.eraserRadius = slot.width
        view.penStyle = slot.style; view.stabilize = slot.stab; view.shapesOn = shapesOn; view.stylusOnly = stylusOnly
    }
    LaunchedEffect(dirty) { if (dirty > 0) { delay(1200); save() } }
    DisposableEffect(Unit) {
        StylusKeys.handler = { e ->
            val c = e.keyCode
            val sys = c == android.view.KeyEvent.KEYCODE_BACK || c == android.view.KeyEvent.KEYCODE_VOLUME_UP || c == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            when {
                learning && !sys -> { if (e.action == android.view.KeyEvent.ACTION_UP) { learned = c; prefs.edit().putInt("tapCode", c).apply(); learning = false }; true }
                c == learned || c in StylusKeys.NATIVE -> { if (e.action == android.view.KeyEvent.ACTION_UP) onDoubleTap(); true }
                else -> false
            }
        }
        onDispose { StylusKeys.handler = null; audio.release()
 save(); prefs.edit().putBoolean("stylusOnly", stylusOnly).apply() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(nb.title, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") } },
                actions = {
                    IconButton({ if (audio.recording) { audio.stopRec(); dirty++ } else micPerm.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                        Icon(if (audio.recording) Icons.Outlined.StopCircle else Icons.Outlined.Mic, "Grabar", tint = if (audio.recording) Brand else LocalContentColor.current) }
                    if (nb.recs.isNotEmpty()) IconButton({ audio.open = !audio.open }) { Icon(Icons.Outlined.Headphones, "Reproducir") }
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
                        DropdownMenuItem({ Text("Resumen y tarjetas (IA)") }, { menu = false; runAi() }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) })
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
                var align by remember(item) { mutableIntStateOf(item.align) }
                var bold by remember(item) { mutableStateOf(item.bold) }
                AlertDialog({ editing = null }, title = { Text(if (isNew) "Nuevo texto" else "Editar texto") },
                    text = { Column {
                        OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), minLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            listOf(28f to "S", 40f to "M", 60f to "L", 88f to "XL").forEach { (v, l) -> FilterChip(size == v, { size = v }, { Text(l) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            listOf(Icons.Outlined.FormatAlignLeft, Icons.Outlined.FormatAlignCenter, Icons.Outlined.FormatAlignRight).forEachIndexed { i, ic ->
                                IconToggleButton(align == i, { align = i }) { Icon(ic, null) } }
                            IconToggleButton(bold, { bold = it }) { Icon(Icons.Outlined.FormatBold, "Negrita") }
                        }
                    } },
                    confirmButton = { TextButton({ item.size = size; item.align = align; item.bold = bold; view.commitText(item, txt, isNew); view.refresh(); dirty++; editing = null }) { Text("Listo") } },
                    dismissButton = { TextButton({ editing = null }) { Text("Cancelar") } })
            }
            if (askKey) {
                var k by remember { mutableStateOf("") }
                AlertDialog({ askKey = false }, title = { Text("Clave de API de Claude") },
                    text = { Column { Text("La IA es opcional. Tu clave se guarda solo en este dispositivo (console.anthropic.com)."); OutlinedTextField(k, { k = it }, singleLine = true, modifier = Modifier.padding(top = 8.dp)) } },
                    confirmButton = { TextButton({ prefs.edit().putString("aiKey", k.trim()).apply(); askKey = false; runAi() }) { Text("Guardar") } },
                    dismissButton = { TextButton({ askKey = false }) { Text("Cancelar") } })
            }
            aiOut?.let { out ->
                AlertDialog({ aiOut = null }, title = { Text("Resumen y tarjetas") },
                    text = { if (out == "…") LinearProgressIndicator(Modifier.fillMaxWidth()) else Text(out, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
                    confirmButton = { if (out != "…" && !out.startsWith("Error") ) TextButton({
                        val p = Page(System.nanoTime(), Template.BLANK); p.items.add(Item(System.nanoTime(), 0, 80f, 80f, InkR.PAGE_W - 160f, text = out, size = 30f))
                        nb.pages.add(p); pageIdx = nb.pages.size - 1; dirty++; aiOut = null }) { Text("Insertar como página") } },
                    dismissButton = { TextButton({ aiOut = null }) { Text("Cerrar") } })
            }
            if (learning) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.primary) {
                Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Haz doble toque en tu lápiz ahora…", color = MaterialTheme.colorScheme.onPrimary)
                    TextButton({ learning = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            wheelAt?.let { at ->
                ToolWheel(at, tool, color, { t -> textTool = false; slots.indexOfFirst { it.tool == t }.takeIf { it >= 0 }?.let { active = it }; wheelAt = null },
                    { setColor(it); wheelAt = null }, view::undo, view::redo) { wheelAt = null }
            }
            picker?.let { k ->
                ColorPickerDialog(if (k < 0) color else swatches[k], { picker = null }) { c ->
                    if (k < 0) setColor(c) else { swatches = swatches.toMutableList().also { it[k] = c }; Slots.saveSwatches(prefs, swatches) }
                    picker = null
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(audio.open) { AudioBar(audio, nb.recs) }
                AnimatedVisibility(showSettings) {
                    ToolSettings(slot, swatches, shapesOn, { shapesOn = it; prefs.edit().putBoolean("shapes", it).apply() }, ::updSlot) { picker = -1 }
                }
                if (hasSel) Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp, modifier = Modifier.padding(bottom = 10.dp)) {
                    Row {
                        TextButton(view::deleteSelection) { Icon(Icons.Outlined.Delete, null, tint = Brand); Spacer(Modifier.width(6.dp)); Text("Eliminar", color = Brand) }
                        TextButton({ converting = true; scope.launch {
                            val t = com.arsnova.ink.Recognizer.recognize(view.selectedStrokes())
                            converting = false
                            if (t.isBlank()) android.widget.Toast.makeText(ctx, "No se reconoció texto (la primera vez se descarga el modelo, ~20 MB)", android.widget.Toast.LENGTH_LONG).show()
                            else view.replaceSelectionWithText(t)
                        } }, enabled = !converting) { Icon(Icons.Outlined.TextFields, null); Spacer(Modifier.width(6.dp)); Text("A texto") }
                    }
                }
                AppleToolbar(slots, if (textTool) -1 else active, swatches, canUndo, canRedo, ::pick, view::undo, view::redo, ::setColor, { picker = it }, { picker = -1 },
                    insertMenu = { open, close ->
                        DropdownMenu(open, close) {
                            DropdownMenuItem({ Text("Texto") }, { textTool = true; showSettings = false; close() }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) })
                            DropdownMenuItem({ Text("Imagen") }, { close(); pickImg.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, leadingIcon = { Icon(Icons.Outlined.Image, null) })
                            DropdownMenuItem({ Text("Página") }, { close(); nb.pages.add(pageIdx + 1, Page(System.nanoTime(), nb.pages[pageIdx].template)); pageIdx++; dirty++ }, leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) })
                        }
                    }, onMore = { menu = true })
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
