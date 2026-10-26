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
import androidx.compose.ui.unit.dp
import com.arsnova.data.NotebookMeta
import com.arsnova.data.Repo
import com.arsnova.data.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.PictureAsPdf
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: Repo, onOpen: (String) -> Unit) {
    var items by remember { mutableStateOf(repo.list()) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<NotebookMeta?>(null) }
    var renaming by remember { mutableStateOf<NotebookMeta?>(null) }
    var importing by remember { mutableStateOf(false) }
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
                text = { Text("Nuevo cuaderno") }, containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
        } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text("ArsNova", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp))
            Text("${items.size} cuadernos", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Crea tu primer cuaderno para empezar a escribir.", color = MaterialTheme.colorScheme.outline)
            }
            LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(items, key = { it.id }) { m ->
                    Box(Modifier.animateItem()) {
                        NotebookCard(repo, m, Modifier.combinedClickable(onClick = { onOpen(m.id) }, onLongClick = { menuFor = m }))
                        DropdownMenu(menuFor?.id == m.id, { menuFor = null }) {
                            DropdownMenuItem({ Text("Renombrar") }, { renaming = m; menuFor = null })
                            DropdownMenuItem({ Text("Eliminar", color = Brand) }, { repo.delete(m.id); items = items - m; menuFor = null })
                        }
                    }
                }
            }
        }
    }

    if (importing) AlertDialog({}, confirmButton = {}, title = { Text("Importando PDF…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
    if (creating) NewNotebookDialog({ creating = false }) { t, c, tpl -> creating = false; onOpen(repo.create(t, c, tpl)) }
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
    Column(modifier) {
        Box(Modifier.aspectRatio(0.75f).fillMaxWidth().shadow(6.dp, RoundedCornerShape(4.dp, 12.dp, 12.dp, 4.dp))
            .clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 4.dp)).background(Color.White)) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxHeight().width(10.dp).background(Color(m.cover)))
        }
        Spacer(Modifier.height(10.dp))
        Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.updated)) + " · ${m.pages} pág.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewNotebookDialog(onDismiss: () -> Unit, onCreate: (String, Int, Template) -> Unit) {
    var title by remember { mutableStateOf("") }
    var cover by remember { mutableIntStateOf(CoverColors[0]) }
    var tpl by remember { mutableStateOf(Template.LINED) }
    AlertDialog(onDismiss, title = { Text("Nuevo cuaderno") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(title, { title = it }, placeholder = { Text("Sin título") }, singleLine = true)
            Text("Plantilla", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Template.BLANK to "En blanco", Template.LINED to "Rayado", Template.GRID to "Cuadrícula", Template.DOTS to "Puntos").forEach { (t, l) ->
                    FilterChip(tpl == t, { tpl = t }, { Text(l) })
                }
            }
            Text("Portada", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverColors.forEach { c ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { cover = c }
                        .then(if (c == cover) Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape).border(5.dp, Color(c).copy(alpha = .4f), CircleShape) else Modifier))
                }
            }
        }
    }, confirmButton = { TextButton({ onCreate(title.ifBlank { "Sin título" }, cover, tpl) }) { Text("Crear") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}
