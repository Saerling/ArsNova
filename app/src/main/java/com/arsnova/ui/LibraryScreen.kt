package com.arsnova.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import com.arsnova.data.NotebookMeta
import com.arsnova.data.Repo
import com.arsnova.data.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
    val fp = remember { ctx.getSharedPreferences("ars", 0) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton({ pick.launch(arrayOf("application/pdf")) }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(androidx.compose.material.icons.Icons.Outlined.PictureAsPdf, "Importar PDF") }
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Nueva nota") }, shape = RoundedCornerShape(50), containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
        } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ArsNova", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp).weight(1f))
                IconButton(onSettings, Modifier.padding(top = 16.dp, end = 12.dp)) { Icon(androidx.compose.material.icons.Icons.Outlined.Settings, "Ajustes") }
            }
            Text("${items.size} cuadernos", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            OutlinedTextField(q, { q = it }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Buscar en títulos y escritura a mano") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), unfocusedBorderColor = Color.Transparent), leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Search, null) })
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

    if (newFolder) { var n by remember { mutableStateOf("") }
        AlertDialog({ newFolder = false }, title = { Text("Nueva carpeta") }, text = { OutlinedTextField(n, { n = it.replace("|", "") }, singleLine = true, shape = RoundedCornerShape(50)) },
            confirmButton = { TextButton({ if (n.isNotBlank()) { folders = folders + n.trim(); fp.edit().putString("folders", folders.joinToString("|")).apply(); folder = n.trim() }; newFolder = false }) { Text("Crear") } },
            dismissButton = { TextButton({ newFolder = false }) { Text("Cancelar") } }) }
    moving?.let { m -> AlertDialog({ moving = null }, title = { Text("Mover a carpeta") }, confirmButton = { TextButton({ moving = null }) { Text("Cerrar") } }, text = {
        Column { (listOf("") + folders).forEach { f -> Text(f.ifEmpty { "Sin carpeta" }, Modifier.fillMaxWidth().clickable {
            repo.setFolder(m.id, f); items = items.map { if (it.id == m.id) it.copy(folder = f) else it }; moving = null }.padding(12.dp)) } } }) }
    if (importing) AlertDialog({}, confirmButton = {}, title = { Text("Importando PDF…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
    if (creating) NewNotebookDialog({ creating = false }) { t, c, tpl, pat -> creating = false; onOpen(repo.create(t, c, tpl, pat, folder)) }
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

/** Ventana de nueva nota estilo Samsung Notes: portada con patrón + estilo de página. */
@Composable
private fun NewNotebookDialog(onDismiss: () -> Unit, onCreate: (String, Int, Template, Int) -> Unit) {
    val ctxT = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf("") }
    var cover by remember { mutableIntStateOf(CoverColors[0]) }
    var pattern by remember { mutableIntStateOf(1) }
    var tpl by remember { mutableStateOf(Template.entries[ctxT.getSharedPreferences("ars", 0).getInt("tpl", 1)]) }
    var tab by remember { mutableIntStateOf(0) }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), modifier = Modifier.padding(20.dp).widthIn(max = 520.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Estilo de nota", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(vertical = 16.dp).height(170.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    val sh = RoundedCornerShape(6.dp, 14.dp, 14.dp, 6.dp)
                    Box(Modifier.fillMaxHeight().aspectRatio(0.75f).shadow(8.dp, sh).clip(sh).background(Color.White).clickable { tab = 0 }
                        .border(if (tab == 0) 3.dp else 0.dp, Brand, sh)) { if (pattern > 0) CoverArt(cover, pattern, Modifier.fillMaxSize()) else Box(Modifier.fillMaxHeight().width(10.dp).background(Color(cover))) }
                    val bmp = remember(tpl) { com.arsnova.ink.Ink.render(com.arsnova.data.Page(0, tpl), 0.09f).asImageBitmap() }
                    Image(bmp, null, Modifier.fillMaxHeight().aspectRatio(0.75f).shadow(8.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).clickable { tab = 1 }
                        .border(if (tab == 1) 3.dp else 0.dp, Brand, RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                }
                Pill { listOf("Portada", "Página").forEachIndexed { i, l ->
                    val on = tab == i
                    Box(Modifier.padding(3.dp).clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent).clickable { tab = i }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(l, color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) }
                } }
                Spacer(Modifier.height(14.dp))
                if (tab == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CoverColors.forEach { c -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).clickable { cover = c }
                            .then(if (c == cover) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    PatternNames.indices.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { p -> Box(Modifier.size(70.dp, 60.dp).clip(RoundedCornerShape(12.dp)).background(Color(cover)).clickable { pattern = p }
                            .border(if (p == pattern) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))) {
                            if (p > 0) CoverArt(cover, p, Modifier.fillMaxSize()) else Text("Ninguna", Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        } }
                    } }
                } else TemplateChoices.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { (t, l) -> val b = remember(t) { com.arsnova.ink.Ink.render(com.arsnova.data.Page(0, t), 0.05f).asImageBitmap() }
                        Image(b, l, Modifier.size(70.dp, 60.dp).clip(RoundedCornerShape(10.dp)).clickable { tpl = t }
                            .border(if (t == tpl) 3.dp else 1.dp, if (t == tpl) Brand else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                } }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = { Text("Título") }, singleLine = true, shape = RoundedCornerShape(50))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onDismiss, shape = RoundedCornerShape(50)) { Text("Cancelar") }
                    Button({ onCreate(title.ifBlank { "Sin título" }, cover, tpl, pattern) }, shape = RoundedCornerShape(50)) { Text("Crear") }
                }
            }
        }
    }
}
