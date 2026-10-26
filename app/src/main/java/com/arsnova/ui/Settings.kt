package com.arsnova.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arsnova.data.Template
import com.arsnova.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(theme: Int, onTheme: (Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember { ctx.getSharedPreferences("ars", Context.MODE_PRIVATE) }
    var stylus by remember { mutableStateOf(p.getBoolean("stylusOnly", false)) }
    var tapAction by remember { mutableIntStateOf(p.getInt("tapAction", 0)) }
    var shapes by remember { mutableStateOf(p.getBoolean("shapes", true)) }
    var auto by remember { mutableStateOf(p.getBoolean("autoUpdate", true)) }
    var tpl by remember { mutableIntStateOf(p.getInt("tpl", 1)) }
    var key by remember { mutableStateOf(p.getString("aiKey", "") ?: "") }
    var upd by remember { mutableStateOf<Updater.Rel?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    @Composable fun Header(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
    @Composable fun Toggle(t: String, sub: String, v: Boolean, on: (Boolean) -> Unit) =
        ListItem({ Text(t) }, supportingContent = { Text(sub) }, trailingContent = { Switch(v, on) }, modifier = Modifier.clickable { on(!v) })
    @Composable fun Choice(t: String, opts: List<String>, sel: Int, on: (Int) -> Unit) = Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(t, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opts.forEachIndexed { i, o -> SegmentedButton(sel == i, { on(i) }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o, maxLines = 1) } }
        }
    }

    Scaffold(topBar = { TopAppBar({ Text("Ajustes") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Header("Apariencia"); Choice("Tema", listOf("Sistema", "Claro", "Oscuro"), theme, onTheme) }
            item {
                Header("Lápiz")
                Toggle("Solo lápiz", "Rechazo de palma: el dedo solo mueve y hace zoom", stylus) { stylus = it; p.edit().putBoolean("stylusOnly", it).apply() }
                Choice("Doble toque del lápiz", listOf("Rueda", "Borrador", "Deshacer", "Nada"), tapAction) { tapAction = it; p.edit().putInt("tapAction", it).apply() }
                Text("Compatible con Huawei M-Pencil (2.ª/3.ª gen/Pro), S Pen y lápices USI. Si tu lápiz usa otro gesto, configúralo desde ⋯ en el editor.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp))
            }
            item {
                Header("Escritura")
                Toggle("Enderezar líneas y figuras", "Mantén el trazo quieto para convertirlo en línea, círculo, rectángulo o triángulo", shapes) { shapes = it; p.edit().putBoolean("shapes", it).apply() }
                Choice("Plantilla por defecto", listOf("Blanco", "Rayado", "Cuadrícula", "Puntos"), tpl) { tpl = it; p.edit().putInt("tpl", it).apply() }
            }
            item {
                Header("Inteligencia artificial (opcional)")
                OutlinedTextField(key, { key = it; p.edit().putString("aiKey", it.trim()).apply() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Clave de API de Claude") }, singleLine = true, supportingText = { Text("Se guarda solo en este dispositivo") })
            }
            item {
                Header("Actualizaciones")
                Toggle("Buscar al abrir", "Avisa cuando hay una versión nueva en GitHub", auto) { auto = it; p.edit().putBoolean("autoUpdate", it).apply() }
                ListItem({ Text("Buscar actualización ahora") }, supportingContent = { Text("Versión instalada: ${Updater.current(ctx)}") },
                    trailingContent = { if (checking) CircularProgressIndicator(Modifier.size(20.dp)) },
                    modifier = Modifier.clickable { checking = true; scope.launch {
                        val r = withContext(Dispatchers.IO) { Updater.latest() }; checking = false
                        if (r != null && r.code > Updater.current(ctx)) upd = r
                        else android.widget.Toast.makeText(ctx, if (r == null) "Sin conexión con GitHub" else "Ya tienes la última versión", android.widget.Toast.LENGTH_SHORT).show()
                    } })
            }
            item { Header("Acerca de"); ListItem({ Text("ArsNova") }, supportingContent = { Text("github.com/${Updater.REPO}") }); Spacer(Modifier.height(40.dp)) }
        }
    }
    upd?.let { UpdateDialog(it) { upd = null } }
}

@Composable
fun UpdateDialog(r: Updater.Rel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var prog by remember { mutableStateOf<Float?>(null) }
    AlertDialog(onDismiss, title = { Text("Nueva versión ${r.tag}") },
        text = { Column { Text(r.notes.ifBlank { "Hay una actualización disponible." }, maxLines = 8)
            prog?.let { LinearProgressIndicator({ it }, Modifier.fillMaxWidth().padding(top = 12.dp)) } } },
        confirmButton = { TextButton({ if (prog == null) { prog = 0f; scope.launch {
            val f = runCatching { withContext(Dispatchers.IO) { Updater.download(ctx, r) { v -> prog = v } } }.getOrNull()
            prog = null
            if (f != null) { Updater.install(ctx, f); onDismiss() } else android.widget.Toast.makeText(ctx, "Falló la descarga", android.widget.Toast.LENGTH_SHORT).show()
        } } }, enabled = prog == null) { Text("Actualizar") } },
        dismissButton = { TextButton(onDismiss) { Text("Más tarde") } })
}
