package com.arsnova.ui

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.arsnova.data.*
import com.arsnova.ink.Ink as InkR
import com.arsnova.ink.InkView
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(repo: Repo, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ars", Context.MODE_PRIVATE) }
    val loaded = remember(id) { runCatching { repo.load(id) }.getOrNull() }
    if (loaded == null) { LaunchedEffect(Unit) { android.widget.Toast.makeText(ctx, "No se pudo abrir este cuaderno", android.widget.Toast.LENGTH_LONG).show(); onBack() }; return }
    val nb: Notebook = loaded
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

    val deskColor = Ars.Desk
    var slots by remember { mutableStateOf(Slots.load(prefs)) }
    var active by remember { mutableIntStateOf(prefs.getInt("slot", 0).coerceIn(0, slots.size - 1)) }
    var swatches by remember { mutableStateOf(Slots.loadSwatches(prefs)) }
    var textTool by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Int?>(null) } // -1 = color actual, 0..4 = editar muestra
    var shapesOn by remember { mutableStateOf(prefs.getBoolean("shapes", true)) }
    val slot = slots[active.coerceIn(0, slots.size - 1)]
    var lastPen by remember { mutableIntStateOf(slots.indexOfFirst { it.tool == Tool.PEN }.coerceAtLeast(0)) }
    if (slot.tool == Tool.PEN && lastPen != active) lastPen = active
    var grouped by remember { mutableStateOf(prefs.getBoolean("grouped", false)) }
    var barArt by remember { mutableStateOf(prefs.getBoolean("barArt", true)) }
    var editBar by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(100) }
    var zoomMenu by remember { mutableStateOf(false) }
    var zoomLock by remember { mutableStateOf(prefs.getBoolean("zoomLock", false)) }
    var zooms by remember { mutableStateOf(prefs.getString("zooms", "")!!.split(",").mapNotNull { it.toIntOrNull() }) }
    var boxH by remember { mutableFloatStateOf(1f) }
    val tool = if (textTool) Tool.TEXT else slot.tool
    val color = slot.color
    fun updSlot(s: Slot) { slots = slots.toMutableList().also { it[active] = s }; Slots.save(prefs, slots) }
    fun pick(i: Int) { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); textTool = false; if (i == active) showSettings = !showSettings && slots[i].tool != Tool.LASSO && slots[i].tool != Tool.LASER else { active = i; showSettings = false; prefs.edit().putInt("slot", i).apply() } }
    fun setColor(c: Int) { if (slot.tool == Tool.PEN || slot.tool == Tool.HIGHLIGHTER) updSlot(slot.copy(color = (c and 0x00FFFFFF) or (slot.color and 0xFF000000.toInt()).let { if (it == 0) 0xFF000000.toInt() else it })) else { val k = lastPen.coerceIn(0, slots.size - 1); if (slots[k].tool == Tool.PEN) { active = k; updSlot(slots[k].copy(color = c)) } }; if (hasSel) view.recolorSelection(c) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var inking by remember { mutableStateOf(false) }
    var palMenu by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf(false) }
    var autoMin by remember { mutableStateOf(prefs.getBoolean("autoMin", false)) }
    var beautify by remember { mutableStateOf(prefs.getBoolean("beautify", true)) }
    var dock by remember { mutableIntStateOf(prefs.getInt("dock", 0)) }
    var boxW by remember { mutableFloatStateOf(1f) }
    var collapsed by remember { mutableStateOf(false) }
    var lastPage by remember { mutableIntStateOf(0) }
    var prevSlot by remember { mutableIntStateOf(0) }
    fun onDoubleTap() {
        val now = System.currentTimeMillis(); if (now - lastTap < 450) return; lastTap = now
        when (prefs.getInt("tapAction", 0)) {
            0 -> wheelAt = if (wheelAt == null) androidx.compose.ui.geometry.Offset(if (view.lastPenX >= 0) view.lastPenX else view.width / 2f, if (view.lastPenY >= 0) view.lastPenY else view.height / 2f) else null
            1 -> { val er = slots.indexOfFirst { it.tool == Tool.ERASER }; if (er < 0) return; if (active == er) active = prevSlot else { prevSlot = active; active = er } }
            2 -> view.undo()
            3 -> { val la = slots.indexOfFirst { it.tool == Tool.LASSO }; if (la < 0) return; if (active == la) active = prevSlot else { prevSlot = active; active = la } }
        }
    }
    // La miniatura se redibuja al salir (y mientras la primera hoja sea ligera); guardar ya no frena la escritura.
    fun save(thumb: Boolean = nb.pages[0].strokes.size < 300) = repo.saveAsync(nb, if (thumb) InkR.render(nb.pages[0], 0.25f) else null)
    fun exportTex(tex: String) {
        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, nb.title.replace(Regex("[^\\w\\- ]"), "_") + ".tex").apply { writeText(tex) }
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, "com.arsnova.files", f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Exportar LaTeX"))
    }
    var latexView by remember { mutableStateOf(false) }
    var texBusy by remember { mutableStateOf(false) }
    // ---- buscador dentro de la nota
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(listOf<InkView.Mark>()) }
    var hitIdx by remember { mutableIntStateOf(0) }
    var indexing by remember { mutableStateOf<String?>(null) }
    fun norm(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    fun go(k: Int) { if (hits.isEmpty()) return; hitIdx = ((k % hits.size) + hits.size) % hits.size; view.markSel = hitIdx; hits[hitIdx].let { view.showRect(it.page, it.r) } }
    fun search() {
        val q = norm(query.trim())
        hits = if (q.length < 2) emptyList() else nb.pages.flatMap { p ->
            p.lines.filter { norm(it.text).contains(q) }.map { InkView.Mark(p, android.graphics.RectF(it.l, it.t, it.r, it.b)) } +
                p.items.filter { it.kind != 1 && norm(it.text).contains(q) }.map { InkView.Mark(p, it.bounds()) } }
        view.marks = hits; if (hits.isEmpty()) view.markSel = -1 else go(0)
    }
    var indexed by remember { mutableIntStateOf(-1) }
    var pages by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var aiOut by remember { mutableStateOf<String?>(null) }
    var askKey by remember { mutableStateOf(false) }
    fun runAi() {
        val key = prefs.getString("aiKey", null); val local = com.arsnova.data.LocalAi.ready(ctx)
        if (!local && key.isNullOrBlank()) { askKey = true; return }
        aiOut = "…"
        scope.launch {
            val text = nb.pages.joinToString("\n\n") { p -> (if (p.ocr.isBlank()) "" else p.ocr) + p.items.joinToString("\n", "\n") { it.text } }
                .ifBlank { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { "" } }
            val src = if (text.isBlank()) com.arsnova.ink.Recognizer.recognize(nb.pages.flatMap { it.strokes }) else text
            aiOut = if (src.isBlank()) "No hay texto reconocible en este cuaderno todavía."
                else runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (local) com.arsnova.data.LocalAi.generate(ctx, com.arsnova.data.LocalAi.studyPrompt(src)) else Ai.ask(key!!, Ai.studyPrompt(src)) } }.getOrElse { "Error: ${it.message}" }
        }
    }
    // Indexa el texto manuscrito de la página actual en segundo plano (búsqueda).
    LaunchedEffect(dirty, pageIdx) { delay(4000); val p = nb.pages.getOrNull(pageIdx) ?: return@LaunchedEffect
        val l = com.arsnova.ink.Recognizer.lines(p.strokes.toList()); if (com.arsnova.ink.Recognizer.error != null) return@LaunchedEffect
        val t = l.joinToString("\n") { it.text }; if (t != p.ocr || p.lines.size != l.size) { p.ocr = t; p.lines = l; save() } }
    // al abrir el buscador se reconocen las páginas que aún no lo estén
    LaunchedEffect(finding) { if (finding) {
        nb.pages.forEachIndexed { i, p -> if (p.strokes.isNotEmpty() && p.lines.isEmpty()) { indexing = "${i + 1}/${nb.pages.size}"
            val l = com.arsnova.ink.Recognizer.lines(p.strokes.toList()); if (com.arsnova.ink.Recognizer.error == null) { p.lines = l; p.ocr = l.joinToString("\n") { it.text } } } }
        if (indexing != null) { indexing = null; dirty++ }; search() } else { view.marks = emptyList(); view.markSel = -1 } }
    LaunchedEffect(query) { if (finding) { delay(220); search() } }

    SideEffect {
        view.onState = { u, r, s -> canUndo = u; canRedo = r; hasSel = s }
        view.onChange = { dirty++ }
        view.onPage = { pageIdx = it }
        view.onNeedPage = { nb.pages.add(nb.pages.last().next()); view.pagesChanged(); dirty++ }
        view.onInking = { inking = it; if (it) showSettings = false }
        view.onTextEdit = { it, isNew -> editing = it to isNew }
        view.tapMode = audio.open
        view.replayTime = audio.replayTime()
        view.onStrokeTap = { s -> audio.jumpTo(s.t) }
        view.onWheel = { x, y -> wheelAt = if (wheelAt == null) androidx.compose.ui.geometry.Offset(x, y) else null }
        view.onStylusButton = { code ->
            if (learning) { learned = code; prefs.edit().putInt("tapCode", code).apply(); learning = false } else onDoubleTap(); true
        }
        view.pages = nb.pages; view.tool = tool; view.color = color
        view.desk = deskColor
        view.penWidth = if (slot.tool == Tool.PEN) slot.width else view.penWidth
        view.hlWidth = if (slot.tool == Tool.HIGHLIGHTER) slot.width else view.hlWidth
        if (slot.tool == Tool.ERASER) { view.eraserRadius = slot.width; view.eraserPixel = slot.style == 1 }
        if (slot.tool == Tool.TAPE) { view.tapeWidth = slot.width; view.tapePattern = slot.style }
        view.penStyle = slot.style; view.stabilize = if (beautify) maxOf(slot.stab, 0.4f) else slot.stab; view.shapesOn = shapesOn; view.stylusOnly = stylusOnly
        view.tapGestures = prefs.getBoolean("tapGestures", true); view.holdErase = prefs.getBoolean("holdErase", true); view.zoomLock = zoomLock; view.onZoom = { zoom = it }; view.pressCurve = when (prefs.getInt("press", 1)) { 0 -> 0.7f; 2 -> 1.45f; else -> 1f }
    }
    LaunchedEffect(dirty) { if (dirty > 0) { delay(1200); save() } }
    DisposableEffect(Unit) {
        val keyHandler: (android.view.KeyEvent) -> Boolean = { e ->
            val c = e.keyCode
            val sys = c == android.view.KeyEvent.KEYCODE_BACK || c == android.view.KeyEvent.KEYCODE_VOLUME_UP || c == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            when {
                learning && !sys -> { if (e.action == android.view.KeyEvent.ACTION_UP) { learned = c; prefs.edit().putInt("tapCode", c).apply(); learning = false }; true }
                c == learned || c in StylusKeys.NATIVE -> { if (e.action == android.view.KeyEvent.ACTION_UP) onDoubleTap(); true }
                else -> false
            }
        }
        StylusKeys.handler = keyHandler
        onDispose { if (StylusKeys.handler === keyHandler) StylusKeys.handler = null; audio.release()
 save(true); prefs.edit().putBoolean("stylusOnly", stylusOnly).apply() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Pill { Text(nb.title, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), maxLines = 1, style = MaterialTheme.typography.titleMedium) } },
                navigationIcon = { Pill(Modifier.padding(start = 8.dp)) { IconButton(onBack) { Icon(ArsIcons.Back, "Atrás") } } },
                actions = { Pill(Modifier.padding(end = 8.dp)) {
                    IconButton({ if (audio.recording) { audio.stopRec(); dirty++ } else micPerm.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                        Icon(if (audio.recording) ArsIcons.Stop else ArsIcons.Mic, "Grabar", tint = if (audio.recording) Brand else LocalContentColor.current) }
                    if (nb.recs.isNotEmpty()) IconButton({ audio.open = !audio.open }) { Icon(ArsIcons.Headphones, "Reproducir") }
                    IconButton({ finding = !finding }) { Icon(ArsIcons.Search, "Buscar en la nota") }
                    Box { TextButton({ zoomMenu = true }) { Text("$zoom %", style = MaterialTheme.typography.labelLarge) }
                        DropdownMenu(zoomMenu, { zoomMenu = false }) {
                            DropdownMenuItem({ Text("Ajustar al ancho") }, { view.zoomTo(100); zoomMenu = false }, leadingIcon = { Icon(ArsIcons.Fit, null) })
                            (listOf(150, 200, 300) + zooms).distinct().sorted().forEach { z ->
                                DropdownMenuItem({ Text("$z %") }, { view.zoomTo(z); zoomMenu = false },
                                    trailingIcon = if (z in zooms) { { IconButton({ zooms = zooms - z; prefs.edit().putString("zooms", zooms.joinToString(",")).apply() }) { Icon(ArsIcons.Close, "Quitar", Modifier.size(16.dp)) } } } else null) }
                            DropdownMenuItem({ Text("Guardar zoom actual ($zoom %)") }, { zooms = (zooms + zoom).distinct(); prefs.edit().putString("zooms", zooms.joinToString(",")).apply(); zoomMenu = false }, leadingIcon = { Icon(ArsIcons.Star, null) })
                            DropdownMenuItem({ Text("Bloquear zoom") }, { zoomLock = !zoomLock; prefs.edit().putBoolean("zoomLock", zoomLock).apply() }, trailingIcon = { Checkbox(zoomLock, null) })
                        } }
                    TextButton({ pages = true }) { Text("${pageIdx + 1}/${nb.pages.size}", style = MaterialTheme.typography.labelLarge) }
                    IconButton({ menu = true }) { Icon(ArsIcons.MoreV, "Más") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Añadir página") }, { nb.pages.add(pageIdx + 1, nb.pages[pageIdx].next()); view.pagesChanged(); view.scrollToPage(pageIdx + 1); dirty++; menu = false },
                            leadingIcon = { Icon(ArsIcons.PageAdd, null) })
                        if (nb.pages.size > 1) DropdownMenuItem({ Text("Eliminar página") }, {
                            nb.pages.removeAt(pageIdx); pageIdx = pageIdx.coerceAtMost(nb.pages.size - 1); view.refresh(); view.pagesChanged(); dirty++; menu = false
                        }, leadingIcon = { Icon(ArsIcons.Trash, null) })
                        DropdownMenuItem({ Text("Plantilla: ${nb.pages[pageIdx].template.label()}") }, {
                            val p = nb.pages[pageIdx]; p.template = Template.entries[(p.template.ordinal + 1) % Template.entries.size]
                            view.fit(); dirty++
                        }, leadingIcon = { Icon(ArsIcons.Grid, null) })
                        DropdownMenuItem({ Text("Solo lápiz (rechazo de palma)") }, { stylusOnly = !stylusOnly },
                            leadingIcon = { Icon(ArsIcons.Pen, null) }, trailingIcon = { Checkbox(stylusOnly, null) })
                        DropdownMenuItem({ Text("Configurar doble toque del lápiz") }, { learning = true; menu = false },
                            leadingIcon = { Icon(ArsIcons.Edit, null) })
                        DropdownMenuItem({ Text("Ajustar a pantalla") }, { view.fit(); menu = false }, leadingIcon = { Icon(ArsIcons.Fit, null) })
                        DropdownMenuItem({ Text("Resumen y tarjetas (IA)") }, { menu = false; runAi() }, leadingIcon = { Icon(ArsIcons.Sparkle, null) })
                        DropdownMenuItem({ Text("Vista LaTeX") }, { menu = false; latexView = true }, leadingIcon = { Icon(ArsIcons.Sigma, null) })
                        DropdownMenuItem({ Text("Exportar PDF") }, { exportPdf(ctx, nb); menu = false }, leadingIcon = { Icon(ArsIcons.Export, null) })
                    }
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { pad ->
        val topPx = with(androidx.compose.ui.platform.LocalDensity.current) { pad.calculateTopPadding().toPx() }
        SideEffect { if (view.topInset != topPx) { view.topInset = topPx; view.fit() } }
        val topPad = pad.calculateTopPadding()
        Box(Modifier.fillMaxSize().onSizeChanged { boxW = it.width.toFloat(); boxH = it.height.toFloat() }) {
            AndroidView({ view }, Modifier.fillMaxSize())
            if (pages) PageGrid(nb, pageIdx, { view.scrollToPage(it); pages = false }, { nb.pages.add(nb.pages.last().next()); view.pagesChanged(); view.scrollToPage(nb.pages.size - 1); dirty++; pages = false }) { pages = false }
            editing?.takeIf { it.first.kind == 2 }?.let { (item, isNew) ->
                FormulaDialog(item.text, { editing = null }) { t -> view.commitText(item, t, isNew); view.refresh(); dirty++; editing = null } }
            editing?.takeIf { it.first.kind != 2 }?.let { (item, isNew) ->
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
                            listOf(ArsIcons.AlignLeft, ArsIcons.AlignCenter, ArsIcons.AlignRight).forEachIndexed { i, ic ->
                                IconToggleButton(align == i, { align = i }) { Icon(ic, null) } }
                            IconToggleButton(bold, { bold = it }) { Icon(ArsIcons.Bold, "Negrita") }
                        }
                    } },
                    confirmButton = { TextButton({ item.size = size; item.align = align; item.bold = bold; view.commitText(item, txt, isNew); view.refresh(); dirty++; editing = null }) { Text("Listo") } },
                    dismissButton = { TextButton({ editing = null }) { Text("Cancelar") } })
            }
            if (askKey) {
                var k by remember { mutableStateOf("") }
                AlertDialog({ askKey = false }, title = { Text("Clave de API de Claude") },
                    text = { Column { Text("Descarga la IA en el dispositivo desde Ajustes (sin internet, gratis) o pega una clave de API de Claude (console.anthropic.com)."); OutlinedTextField(k, { k = it }, singleLine = true, modifier = Modifier.padding(top = 8.dp)) } },
                    confirmButton = { TextButton({ prefs.edit().putString("aiKey", k.trim()).apply(); askKey = false; runAi() }) { Text("Guardar") } },
                    dismissButton = { TextButton({ askKey = false }) { Text("Cancelar") } })
            }
            aiOut?.let { out ->
                AlertDialog({ aiOut = null }, title = { Text("Resumen y tarjetas") },
                    text = { if (out == "…") LinearProgressIndicator(Modifier.fillMaxWidth()) else Text(out, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
                    confirmButton = { if (out != "…" && !out.startsWith("Error") ) TextButton({
                        val p = Page(System.nanoTime(), Template.BLANK); p.items.add(Item(System.nanoTime(), 0, 80f, 80f, InkR.PAGE_W - 160f, text = out, size = 30f))
                        nb.pages.add(p); view.pagesChanged(); view.scrollToPage(nb.pages.size - 1); dirty++; aiOut = null }) { Text("Insertar como página") } },
                    dismissButton = { TextButton({ aiOut = null }) { Text("Cerrar") } })
            }
            if (learning) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = squircle(20), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.primary) {
                Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Haz doble toque en tu lápiz ahora…", color = MaterialTheme.colorScheme.onPrimary)
                    TextButton({ learning = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            if (editBar) BarEditor(slots, grouped, barArt, { l -> slots = l; Slots.save(prefs, l); active = active.coerceIn(0, l.size - 1); lastPen = l.indexOfFirst { it.tool == Tool.PEN }.coerceAtLeast(0) },
                { grouped = it; prefs.edit().putBoolean("grouped", it).apply() }, { barArt = it; prefs.edit().putBoolean("barArt", it).apply() }) { editBar = false }
            if (palMenu) PaletteMenu(autoMin, !stylusOnly, beautify, { autoMin = it; prefs.edit().putBoolean("autoMin", it).apply() },
                { stylusOnly = !it; prefs.edit().putBoolean("stylusOnly", !it).apply() }, { beautify = it; prefs.edit().putBoolean("beautify", it).apply() },
                { palMenu = false; lines = true }, { palMenu = false; showSettings = true }, { palMenu = false; editBar = true },
                dockAlign(dock), dockPad(dock, topPad)) { palMenu = false }
            if (stickers) AlertDialog({ stickers = false }, confirmButton = { TextButton({ stickers = false }) { Text("Cerrar") } }, title = { Text("Stickers") }, text = {
                Column { "⭐️🌟✨💡📌📎✏️📚📝✅❌❗️❓💯🔥❤️💙💚💛💜🌸🌿🍀☀️🌙⚡️🎯🎓🧪🧬⚗️🔬📐📏🧠💭🗓️⏰🎉👍👀🙌".let { str ->
                    val list = java.text.BreakIterator.getCharacterInstance().let { bi -> bi.setText(str); var st = bi.first(); val out = ArrayList<String>(); var en = bi.next(); while (en != java.text.BreakIterator.DONE) { out += str.substring(st, en); st = en; en = bi.next() }; out }
                    list.chunked(8).forEach { row -> Row { row.forEach { e -> Text(e, Modifier.weight(1f).clickable { view.addSticker(e); stickers = false }.padding(6.dp), style = MaterialTheme.typography.headlineSmall) } } }
                } }
            })
            if (lines) LinesSheet(nb.pages[pageIdx].template, { t -> nb.pages.forEach { it.template = t }; view.refresh(); dirty++; lines = false }) { lines = false }
            val panelAlign = dockAlign(dock)
            val panelPad = dockPad(dock, topPad)
            Column(Modifier.align(panelAlign).navigationBarsPadding().padding(panelPad).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(audio.open) { AudioBar(audio, nb.recs) }
                AnimatedVisibility(picker != null, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 500f), 0.85f, androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.9f)) {
                    val k = picker ?: -1
                    ColorPopover(if (k < 0) color else swatches[k], swatches, { c ->
                        if (k >= 0) { swatches = swatches.toMutableList().also { it[k] = c or 0xFF000000.toInt() }; Slots.saveSwatches(prefs, swatches) }
                        else if (slot.tool == Tool.PEN || slot.tool == Tool.HIGHLIGHTER) { updSlot(slot.copy(color = c)); if (hasSel) view.recolorSelection(c) } else setColor(c)
                    }, { c -> swatches = (swatches.drop(1) + c); Slots.saveSwatches(prefs, swatches) }) { picker = null }
                }
                AnimatedVisibility(showSettings && !collapsed,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), 0.85f, androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f))) {
                    ToolSettings(slot, swatches, shapesOn, prefs, { shapesOn = it; prefs.edit().putBoolean("shapes", it).apply() }, ::updSlot,
                        onFav = { slots = slots + slot.copy(); Slots.save(prefs, slots); android.widget.Toast.makeText(ctx, "Guardado como favorito: está en la barra y en la rueda", android.widget.Toast.LENGTH_SHORT).show() }) { picker = -1 }
                }
                AnimatedVisibility(hasSel, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 520f), 0.85f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.9f)) {
                    Pill(Modifier.padding(bottom = 10.dp)) {
                        TextButton(view::duplicateSelection) { Icon(ArsIcons.Copy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Duplicar") }
                        TextButton({ converting = true; scope.launch {
                            val t = com.arsnova.ink.Recognizer.recognize(view.selectedStrokes())
                            converting = false
                            if (t.isBlank()) android.widget.Toast.makeText(ctx, "No se reconoció texto (la primera vez se descarga el modelo, ~20 MB)", android.widget.Toast.LENGTH_LONG).show()
                            else view.replaceSelectionWithText(t)
                        } }, enabled = !converting) { Icon(ArsIcons.Text, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("A texto") }
                        TextButton({
                            val key = prefs.getString("aiKey", null)
                            if (key.isNullOrBlank()) android.widget.Toast.makeText(ctx, "Para convertir escritura a fórmula, añade tu clave de API de Claude en Ajustes", android.widget.Toast.LENGTH_LONG).show()
                            else { converting = true; scope.launch {
                                val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Ai.latex(key, view.selectionPng()!!) } }
                                converting = false
                                r.onSuccess { view.replaceSelectionWithFormula(it) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo convertir: ${it.message}", android.widget.Toast.LENGTH_LONG).show() } } }
                        }, enabled = !converting) { Icon(ArsIcons.Sigma, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("A fórmula") }
                        TextButton(view::deleteSelection) { Icon(ArsIcons.Trash, null, Modifier.size(18.dp), tint = Brand); Spacer(Modifier.width(6.dp)); Text("Eliminar", color = Brand) }
                    }
                }
            }
            AnimatedVisibility(finding, Modifier.align(Alignment.TopCenter).padding(top = topPad + 6.dp),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 }, exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Pill {
                        Icon(ArsIcons.Search, null, Modifier.padding(start = 12.dp).size(20.dp), tint = Ars.Muted)
                        val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                        Box(Modifier.width(260.dp).padding(horizontal = 10.dp, vertical = 12.dp)) {
                            if (query.isEmpty()) Text("Buscar en esta nota", color = Ars.Muted)
                            androidx.compose.foundation.text.BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ars.Text), cursorBrush = androidx.compose.ui.graphics.SolidColor(Ars.Text),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go(hitIdx + 1) }))
                        }
                        Text(if (hits.isEmpty()) (if (query.trim().length < 2) "" else "0") else "${hitIdx + 1}/${hits.size}", color = Ars.Muted, style = MaterialTheme.typography.labelLarge)
                        IconButton({ go(hitIdx - 1) }, enabled = hits.size > 1) { Icon(ArsIcons.Up, "Anterior") }
                        IconButton({ go(hitIdx + 1) }, enabled = hits.size > 1) { Icon(ArsIcons.Down, "Siguiente") }
                        IconButton({ finding = false }) { Icon(ArsIcons.Close, "Cerrar") }
                    }
                    val note = indexing?.let { "Leyendo tu letra… página $it" } ?: com.arsnova.ink.Recognizer.error?.let { "El reconocimiento de escritura no está disponible ($it). Revísalo en Ajustes." }
                    if (note != null) Pill(Modifier.padding(top = 6.dp)) { Text(note, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = Ars.Muted) }
                }
            }
            // ---- Barra: se arrastra del tirador a cualquiera de los cuatro bordes; minimizada es un botón que se mueve libremente.
            var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            var gripAt by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            var finger by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            fun zone(f: androidx.compose.ui.geometry.Offset) = when { f.x < boxW * 0.16f -> 1; f.x > boxW * 0.84f -> -1; f.y < boxH * 0.4f -> 2; else -> 0 }
            val handle = Modifier.onGloballyPositioned { gripAt = it.positionInRoot() }.pointerInput(Unit) {
                detectDragGestures(onDragStart = { finger = gripAt + it; showSettings = false; picker = null }, onDragCancel = { drag = androidx.compose.ui.geometry.Offset.Zero; finger = null }, onDragEnd = {
                    finger?.let { f -> val z = zone(f); if (z != dock) { dock = z; prefs.edit().putInt("dock", z).apply(); view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } }
                    drag = androidx.compose.ui.geometry.Offset.Zero; finger = null
                }) { c, d -> c.consume(); drag += d; finger = gripAt + c.position }
            }
            fun dockMod(d: Int) = Modifier.align(dockAlign(d)).navigationBarsPadding().padding(top = if (d == 2) topPad else 0.dp).padding(12.dp)
            val target = finger?.let { zone(it) }
            AnimatedVisibility(target != null && target != dock, dockMod(target ?: 0),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.9f), exit = androidx.compose.animation.fadeOut()) {
                val horiz = target == 0 || target == 2
                Box(Modifier.size(if (horiz) 420.dp else 60.dp, if (horiz) 60.dp else 420.dp).clip(squircle(28)).background(Ars.Text.copy(alpha = 0.10f)).border(1.5.dp, Ars.Text.copy(alpha = 0.35f), squircle(28)))
            }
            val lift by androidx.compose.animation.core.animateFloatAsState(if (finger != null) 1.04f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
            AnimatedVisibility(!collapsed, dockMod(dock).offset { androidx.compose.ui.unit.IntOffset(drag.x.toInt(), drag.y.toInt()) }.graphicsLayer { scaleX = lift; scaleY = lift },
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 480f), 0.7f),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.scaleOut(targetScale = 0.7f)) {
                AppleToolbar(slots, if (textTool) -1 else active, swatches, canUndo, canRedo, ::pick, view::undo, view::redo, ::setColor, { picker = it }, { picker = -1 },
                    insertMenu = { open, close ->
                        DropdownMenu(open, close) {
                            DropdownMenuItem({ Text("Texto") }, { textTool = true; showSettings = false; close() }, leadingIcon = { Icon(ArsIcons.Text, null) })
                            DropdownMenuItem({ Text("Fórmula (LaTeX)") }, { close(); editing = view.centerItem(2, 46f, if (slot.tool == Tool.PEN) slot.color or 0xFF000000.toInt() else 0xFF1D1B18.toInt()) to true }, leadingIcon = { Icon(ArsIcons.Sigma, null) })
                            DropdownMenuItem({ Text("Imagen") }, { close(); pickImg.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, leadingIcon = { Icon(ArsIcons.Image, null) })
                            DropdownMenuItem({ Text("Página") }, { close(); nb.pages.add(pageIdx + 1, nb.pages[pageIdx].next()); view.pagesChanged(); view.scrollToPage(pageIdx + 1); dirty++ }, leadingIcon = { Icon(ArsIcons.PageAdd, null) })
                            DropdownMenuItem({ Text("Líneas y cuadrículas") }, { close(); lines = true }, leadingIcon = { Icon(ArsIcons.Grid, null) })
                            DropdownMenuItem({ Text("Stickers") }, { close(); stickers = true }, leadingIcon = { Icon(ArsIcons.Smile, null) })
                        }
                    }, onMore = { palMenu = true }, onCollapse = { collapsed = true; showSettings = false; picker = null }, dock = dock, handle = handle, grouped = grouped, penIdx = lastPen, art = barArt)
            }
            wheelAt?.let { at ->
                ToolWheel(at, slots, if (textTool) -1 else active, swatches, canUndo, canRedo,
                    onSlot = { j -> textTool = false; active = j; showSettings = false; prefs.edit().putInt("slot", j).apply() },
                    onColor = { setColor(it) }, onPicker = { picker = -1 }, onWidth = { w -> updSlot(slots[active.coerceIn(0, slots.size - 1)].copy(width = w)) }, onUndo = view::undo, onRedo = view::redo) { wheelAt = null } }
            if (latexView) {
                val pg = nb.pages[pageIdx.coerceIn(0, nb.pages.size - 1)]
                var tex by remember(pg) { mutableStateOf(pg.tex) }
                val sheet = remember(pg) { InkR.render(pg, 0.7f).asImageBitmap() }
                LatexPane(nb.title, sheet, tex, texBusy, { tex = it; pg.tex = it; dirty++ }, onConvert = {
                    val key = prefs.getString("aiKey", null)
                    if (key.isNullOrBlank()) android.widget.Toast.makeText(ctx, "Para convertir la página añade tu clave de API de Claude en Ajustes. También puedes escribir el LaTeX a mano en «Código».", android.widget.Toast.LENGTH_LONG).show()
                    else { texBusy = true; val bmp = InkR.render(pg, 0.85f); scope.launch {
                        val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { val o = java.io.ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); bmp.recycle(); Ai.latexDoc(key, o.toByteArray()) } }
                        texBusy = false
                        r.onSuccess { tex = it; pg.tex = it; dirty++ }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo convertir: ${it.message}", android.widget.Toast.LENGTH_LONG).show() } } }
                }, onExport = { exportTex(tex) }) { latexView = false }
            }
            if (collapsed) {
                // posición libre, guardada como fracción de la pantalla
                var mp by remember { mutableStateOf(androidx.compose.ui.geometry.Offset(prefs.getFloat("miniX", 0.5f), prefs.getFloat("miniY", 0.93f))) }
                val pop = remember { androidx.compose.animation.core.Animatable(0.5f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 460f)) }
                val szPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
                Surface(Modifier.offset { androidx.compose.ui.unit.IntOffset(((boxW - szPx) * mp.x).toInt(), ((boxH - szPx) * mp.y).toInt()) }.size(60.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .pointerInput(Unit) { detectDragGestures(onDragEnd = { prefs.edit().putFloat("miniX", mp.x).putFloat("miniY", mp.y).apply() }) { c, d -> c.consume()
                        mp = androidx.compose.ui.geometry.Offset((mp.x + d.x / (boxW - szPx).coerceAtLeast(1f)).coerceIn(0f, 1f), (mp.y + d.y / (boxH - szPx).coerceAtLeast(1f)).coerceIn(0.08f, 0.97f)) } }
                    .bounceClick { collapsed = false }, shape = CircleShape, shadowElevation = 12.dp, color = Ars.Glass, border = androidx.compose.foundation.BorderStroke(1.dp, Ars.GlassBorder)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(toolIcon(tool), "Mostrar herramientas")
                        if (slot.tool == Tool.PEN || slot.tool == Tool.HIGHLIGHTER) Box(Modifier.align(Alignment.BottomEnd).padding(11.dp).size(11.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(slot.color).copy(alpha = 1f)).border(1.5.dp, Ars.Glass.copy(alpha = 1f), CircleShape))
                    }
                }
            }
        }
    }
}

private fun dockAlign(d: Int) = when (d) { 1 -> Alignment.CenterStart; -1 -> Alignment.CenterEnd; 2 -> Alignment.TopCenter; else -> Alignment.BottomCenter }
private fun dockPad(d: Int, top: androidx.compose.ui.unit.Dp) = when (d) { 1 -> PaddingValues(start = 96.dp); -1 -> PaddingValues(end = 96.dp); 2 -> PaddingValues(top = top + 92.dp); else -> PaddingValues(bottom = 98.dp) }

private fun Template.label() = when (this) { Template.BLANK -> "en blanco"; Template.LINED -> "rayado"; Template.GRID -> "cuadrícula"; Template.DOTS -> "puntos"; else -> "líneas y cuadrícula" }

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
                        .border(if (i == cur) 2.5.dp else 1.dp, if (i == cur) Ars.Text else Ars.Line, RoundedCornerShape(6.dp))) {
                        bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) }
                    }
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Box(Modifier.aspectRatio(0.75f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).clickable { onAdd() },
                    contentAlignment = Alignment.Center) { Icon(ArsIcons.Add, "Añadir página") }
            }
        }
    }
}
