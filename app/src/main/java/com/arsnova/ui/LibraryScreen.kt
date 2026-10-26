package com.arsnova.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsnova.data.NotebookMeta
import com.arsnova.data.Repo
import com.arsnova.data.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: Repo, onSettings: () -> Unit, onOpen: (String) -> Unit) {
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    var upd by remember { mutableStateOf<com.arsnova.data.Updater.Rel?>(null) }
    LaunchedEffect(Unit) {
        if (ctx0.getSharedPreferences("ars", 0).getBoolean("autoUpdate", true)) {
            val r = withContext(Dispatchers.IO) { com.arsnova.data.Updater.latest() }
            if (r != null && r.code > com.arsnova.data.Updater.current(ctx0)) upd = r
        }
    }
    upd?.let { UpdateDialog(it) { upd = null } }
    var items by remember { mutableStateOf(repo.list()) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<NotebookMeta?>(null) }
    var renaming by remember { mutableStateOf<NotebookMeta?>(null) }
    var importing by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val fp = remember { ctx0.getSharedPreferences("ars", 0) }
    var folders by remember { mutableStateOf(fp.getString("folders", "")!!.split("|").filter { it.isNotBlank() }) }
    var folder by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<NotebookMeta?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importing = true; scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "PDF"
            val id = runCatching { withContext(Dispatchers.IO) { repo.importPdf(uri, name, CoverColors[2]) } }.getOrNull()
            importing = false
            if (id != null) onOpen(id) else android.widget.Toast.makeText(ctx, "No se pudo abrir el PDF", android.widget.Toast.LENGTH_SHORT).show()
        } }
    }

    Ambient { Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = { Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton({ pick.launch(arrayOf("application/pdf")) }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(androidx.compose.material.icons.Icons.Rounded.PictureAsPdf, "Importar PDF") }
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nueva nota") }, shape = RoundedCornerShape(50), containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
        } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notas", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp), modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 2.dp).weight(1f))
                Pill(Modifier.padding(top = 20.dp, end = 16.dp)) { IconButton(onSettings) { Icon(androidx.compose.material.icons.Icons.Rounded.Settings, "Ajustes") } }
            }
            Text("${items.size} cuadernos", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            OutlinedTextField(q, { q = it }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Buscar en títulos y escritura a mano") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), unfocusedBorderColor = Color.Transparent), leadingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.Search, null) })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("") + folders).forEach { f -> val on = f == folder
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
                        .clickable { folder = f }.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(f.ifEmpty { "Todas" }, color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) } }
                Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).clickable { newFolder = true }.padding(horizontal = 14.dp, vertical = 8.dp)) { Text("+ Carpeta") }
            }
            val shown = items.filter { (folder.isEmpty() || it.folder == folder) && (q.isBlank() || it.title.contains(q, true) || it.text.contains(q, true)) }
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Crea tu primer cuaderno para empezar a escribir.", color = MaterialTheme.colorScheme.outline)
            }
            LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(shown, key = { it.id }) { m ->
                    Box(Modifier.animateItem()) {
                        NotebookCard(repo, m, Modifier.combinedClickable(onClick = { onOpen(m.id) }, onLongClick = { menuFor = m }))
                        DropdownMenu(menuFor?.id == m.id, { menuFor = null }) {
                            DropdownMenuItem({ Text("Renombrar") }, { renaming = m; menuFor = null })
                            DropdownMenuItem({ Text("Mover a carpeta") }, { moving = m; menuFor = null })
                            DropdownMenuItem({ Text("Eliminar", color = Brand) }, { repo.delete(m.id); items = items - m; menuFor = null })
                        }
                    }
                }
            }
        }
    }

    }
    if (newFolder) { var n by remember { mutableStateOf("") }
        AlertDialog({ newFolder = false }, title = { Text("Nueva carpeta") }, text = { OutlinedTextField(n, { n = it.replace("|", "") }, singleLine = true, shape = RoundedCornerShape(50)) },
            confirmButton = { TextButton({ if (n.isNotBlank()) { folders = folders + n.trim(); fp.edit().putString("folders", folders.joinToString("|")).apply(); folder = n.trim() }; newFolder = false }) { Text("Crear") } },
            dismissButton = { TextButton({ newFolder = false }) { Text("Cancelar") } }) }
    moving?.let { m -> AlertDialog({ moving = null }, title = { Text("Mover a carpeta") }, confirmButton = { TextButton({ moving = null }) { Text("Cerrar") } }, text = {
        Column { (listOf("") + folders).forEach { f -> Text(f.ifEmpty { "Sin carpeta" }, Modifier.fillMaxWidth().clickable {
            repo.setFolder(m.id, f); items = items.map { if (it.id == m.id) it.copy(folder = f) else it }; moving = null }.padding(12.dp)) } } }) }
    if (importing) AlertDialog({}, confirmButton = {}, title = { Text("Importando PDF…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
    if (creating) NewNotebookDialog({ creating = false }) { t, c, tpl, pat, pap, sp -> creating = false; onOpen(repo.create(t, c, tpl, pat, folder, pap, sp)) }
    renaming?.let { m ->
        var t by remember { mutableStateOf(m.title) }
        AlertDialog({ renaming = null }, title = { Text("Renombrar") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ repo.rename(m.id, t); items = items.map { if (it.id == m.id) it.copy(title = t) else it }; renaming = null }) { Text("Guardar") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun NotebookCard(repo: Repo, m: NotebookMeta, modifier: Modifier) {
    val thumb by produceState<ImageBitmap?>(null, m.id, m.updated) {
        value = withContext(Dispatchers.IO) { repo.thumb(m.id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    val shape = RoundedCornerShape(6.dp, 16.dp, 16.dp, 6.dp)
    Column(modifier) {
        Box(Modifier.aspectRatio(0.75f).fillMaxWidth().shadow(10.dp, shape).clip(shape).background(Color.White)) {
            if (m.pattern > 0) {
                CoverArt(m.cover, m.pattern, Modifier.fillMaxSize())
                Pill(Modifier.align(Alignment.BottomStart).padding(12.dp)) { Text(m.title, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            } else {
                thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxHeight().width(12.dp).background(Brush.horizontalGradient(listOf(Color(m.cover), Color(m.cover).copy(alpha = 0.7f)))))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.updated)) + " · ${m.pages} pág.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

private val PaperColors = listOf(0xFFFFFFFF, 0xFFF3F2EE, 0xFFFBF3E2, 0xFFE4E4E6, 0xFF1C1C1E).map { it.toInt() }
private val TemplateCats = listOf(
    "Base" to listOf(Template.BLANK to "En blanco", Template.LINED_N to "Línea estrecha", Template.LINED to "Línea", Template.LINED_W to "Línea ancha",
        Template.GRID_S to "Mini cuadro", Template.GRID to "Cuadro", Template.GRID_L to "Cuadro grande", Template.DOTS to "Puntos"),
    "Estudio" to listOf(Template.CORNELL to "Cornell", Template.MARGIN to "Con margen", Template.HANDWRITING to "Caligrafía"),
    "Plan" to listOf(Template.TODO to "Tareas", Template.WEEKLY to "Semanal"),
    "Música" to listOf(Template.MUSIC to "Pentagrama"))

/** Pantalla "Crear nota" (estilo Huawei/Samsung): tapa + papel, plantillas por categoría, color de papel y espacio de línea. */
@Composable
private fun NewNotebookDialog(onDismiss: () -> Unit, onCreate: (String, Int, Template, Int, Int, Float) -> Unit) {
    val ctxT = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf("") }
    var cover by remember { mutableIntStateOf(CoverColors[0]) }
    var pattern by remember { mutableIntStateOf(1) }
    var coverOn by remember { mutableStateOf(true) }
    var tpl by remember { mutableStateOf(Template.entries[ctxT.getSharedPreferences("ars", 0).getInt("tpl", 1).coerceIn(0, Template.entries.size - 1)]) }
    var paper by remember { mutableIntStateOf(PaperColors[0]) }
    var lsp by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(1) }
    var cat by remember { mutableIntStateOf(0) }
    val spacing = if (lsp == 0) 0f else 30f + lsp * 8f
    @Composable fun thumb(t: Template, scale: Float) = remember(t, paper, lsp) { com.arsnova.ink.Ink.render(com.arsnova.data.Page(0, t, paper = paper, spacing = spacing), scale).asImageBitmap() }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Ambient(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss) { Text("Cancelar") }
                Text("Crear nota", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button({ onCreate(title.ifBlank { "Sin título" }, cover, tpl, if (coverOn) pattern else 0, paper, spacing) }, shape = RoundedCornerShape(50)) { Text("Crear") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                @Composable fun Card(i: Int, label: String, body: @Composable () -> Unit) = Column(Modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .border(if (tab == i) 2.dp else 1.dp, if (tab == i) cs.primary else cs.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).clickable { tab = i }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).aspectRatio(0.72f).shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))) { body() }
                    Text(label, Modifier.padding(top = 8.dp), color = if (tab == i) cs.primary else cs.onBackground)
                }
                Card(0, "Tapa") { if (coverOn && pattern > 0) CoverArt(cover, pattern, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(Color(paper))) { Box(Modifier.fillMaxHeight().width(8.dp).background(Color(cover))) } }
                Card(1, "Papel") { Image(thumb(tpl, 0.12f), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Surface(Modifier.fillMaxHeight().widthIn(max = 380.dp).weight(1f, false), shape = RoundedCornerShape(20.dp), color = cs.surface.copy(alpha = 0.8f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("Título de la nota") }, singleLine = true, shape = RoundedCornerShape(50))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Tapa", Modifier.weight(1f)); Switch(coverOn, { coverOn = it }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Formato", Modifier.weight(1f)); Text("Vertical, A4", color = cs.outline) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = cs.surface.copy(alpha = 0.9f)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    if (tab == 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Color", style = MaterialTheme.typography.labelLarge)
                            PaperColors.forEach { c -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).border(if (c == paper) 3.dp else 1.dp, if (c == paper) cs.primary else cs.outline.copy(alpha = 0.4f), CircleShape).clickable { paper = c }) }
                            Spacer(Modifier.width(14.dp)); Text("Espacio de línea", style = MaterialTheme.typography.labelLarge)
                            Pill { IconButton({ if (lsp > 0) lsp-- }) { Text("—") }; Text(if (lsp == 0) "Auto" else "$lsp", Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                IconButton({ if (lsp < 8) lsp++ }) { Icon(androidx.compose.material.icons.Icons.Rounded.Add, null) } }
                        }
                        Row(Modifier.padding(vertical = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TemplateCats.forEachIndexed { i, (n, _) -> Box(Modifier.clip(RoundedCornerShape(50)).background(if (cat == i) cs.primary.copy(alpha = 0.15f) else cs.onSurface.copy(alpha = 0.06f))
                                .border(1.dp, if (cat == i) cs.primary else Color.Transparent, RoundedCornerShape(50)).clickable { cat = i }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                                Text(n, color = if (cat == i) cs.primary else cs.onSurface) } }
                        }
                        TemplateCats.drop(cat).forEach { (n, list) ->
                            Text(n, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                list.forEach { (t, l) -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp).clickable { tpl = t }) {
                                    Box { Image(thumb(t, 0.07f), l, Modifier.size(104.dp, 140.dp).clip(RoundedCornerShape(8.dp)).border(if (t == tpl) 3.dp else 1.dp, if (t == tpl) cs.primary else cs.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                        if (t == tpl) Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).size(22.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                                            Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.size(14.dp), tint = cs.onPrimary) } }
                                    Text(l, Modifier.padding(top = 6.dp), color = if (t == tpl) cs.primary else cs.onSurface, style = MaterialTheme.typography.labelMedium, maxLines = 1) } }
                            }
                        }
                    } else {
                        Text("Color de tapa", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CoverColors.forEach { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { cover = c; coverOn = true }
                                .then(if (c == cover) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)) }
                        }
                        Text("Diseño", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PatternNames.indices.forEach { p -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(96.dp, 128.dp).clip(RoundedCornerShape(10.dp)).background(Color(cover)).clickable { pattern = p; coverOn = p > 0 }
                                    .border(if (p == pattern && coverOn) 3.dp else 0.dp, cs.primary, RoundedCornerShape(10.dp))) { if (p > 0) CoverArt(cover, p, Modifier.fillMaxSize()) }
                                Text(PatternNames[p], style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) } }
                        }
                    }
                }
            }
        } }
    }
}
