package com.arsnova.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var press by remember { mutableIntStateOf(p.getInt("press", 1)) }
    var gestures by remember { mutableStateOf(p.getBoolean("tapGestures", true)) }
    var auto by remember { mutableStateOf(p.getBoolean("autoUpdate", true)) }
    var tpl by remember { mutableIntStateOf(p.getInt("tpl", 1)) }
    var key by remember { mutableStateOf(p.getString("aiKey", "") ?: "") }
    var upd by remember { mutableStateOf<Updater.Rel?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    @Composable fun Header(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = Ars.Muted, modifier = Modifier.padding(start = 30.dp, top = 26.dp, bottom = 8.dp))
    @Composable fun Toggle(t: String, sub: String, v: Boolean, on: (Boolean) -> Unit) =
        ListItem({ Text(t) }, supportingContent = { Text(sub) }, trailingContent = { Switch(v, on) }, modifier = Modifier.clickable { on(!v) })
    @Composable fun Choice(t: String, opts: List<String>, sel: Int, on: (Int) -> Unit) = Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(t, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opts.forEachIndexed { i, o -> SegmentedButton(sel == i, { on(i) }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o, maxLines = 1) } }
        }
    }

    Ambient { Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { TopAppBar({ Text("Ajustes", style = MaterialTheme.typography.headlineLarge) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent), navigationIcon = { IconButton(onBack) { Icon(ArsIcons.Back, "Atrás") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Header("Personalización"); Group {
                Choice("Tema", listOf("Sistema", "Claro", "Oscuro"), theme, onTheme)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Transparencia de barras y paneles", Modifier.weight(1f)); Text("${Math.round((1f - Look.glass) * 100)} %", color = Ars.Muted) }
                    Slider(1f - Look.glass, { Look.glass = 1f - it; Look.save(p) }, valueRange = 0f..0.5f)
                    // muestra en vivo
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(squircle(18)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2D5BD9), androidx.compose.ui.graphics.Color(0xFFF2B705), androidx.compose.ui.graphics.Color(0xFFE5484D)))), contentAlignment = Alignment.Center) {
                        Pill { Icon(ArsIcons.Pen, null, Modifier.padding(start = 14.dp)); Text("Así se ven las barras", Modifier.padding(horizontal = 12.dp, vertical = 11.dp), style = MaterialTheme.typography.labelLarge) } }
                }
                Choice("Esquinas", listOf("Mochy", "Continuas (BitChord)", "Redondas"), Look.corners) { Look.corners = it; Look.save(p) }
                Choice("Mesa bajo la hoja", listOf("Según el tema", "Clara", "Oscura"), Look.desk) { Look.desk = it; Look.save(p) }
                Toggle("Rebote al pulsar", "Los botones se encogen bajo el dedo y vuelven con un pequeño rebote", Look.bounce) { Look.bounce = it; Look.save(p) }
                var art by remember { mutableStateOf(p.getBoolean("barArt", true)) }; var grp by remember { mutableStateOf(p.getBoolean("grouped", false)) }
                Toggle("Lápices con forma en la barra", "Cada lápiz asoma de pie, como en Apple; apagado, iconos", art) { art = it; p.edit().putBoolean("barArt", it).apply() }
                Toggle("Agrupar los lápices en un botón", "La barra muestra solo el lápiz en uso; los demás siguen en la rueda", grp) { grp = it; p.edit().putBoolean("grouped", it).apply() } } }
            item {
                Header("Lápiz"); Group {
                Toggle("Solo lápiz", "Rechazo de palma: el dedo solo mueve y hace zoom", stylus) { stylus = it; p.edit().putBoolean("stylusOnly", it).apply() }
                Toggle("Gestos con los dedos", "Toque con dos dedos para deshacer, con tres para rehacer", gestures) { gestures = it; p.edit().putBoolean("tapGestures", it).apply() }
                Choice("Presión del lápiz", listOf("Suave", "Normal", "Firme"), press) { press = it; p.edit().putInt("press", it).apply() }
                Choice("Doble toque del lápiz", listOf("Rueda", "Borrador", "Deshacer", "Lazo", "Nada"), tapAction) { tapAction = it; p.edit().putInt("tapAction", it).apply() }
                Text("Compatible con Huawei M-Pencil (2.ª/3.ª gen/Pro), S Pen y lápices USI. Si tu lápiz usa otro gesto, configúralo desde ⋯ en el editor.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp)) } }
            item {
                Header("Escritura"); Group {
                Toggle("Enderezar líneas y figuras", "Mantén el trazo quieto para convertirlo en línea, círculo, rectángulo o triángulo", shapes) { shapes = it; p.edit().putBoolean("shapes", it).apply() }
                var rec by remember { mutableStateOf("Comprobando…") }
                suspend fun check() { rec = if (com.arsnova.ink.Recognizer.ensure()) "Listo · en el dispositivo, sin internet" else "No disponible: ${com.arsnova.ink.Recognizer.error}" }
                LaunchedEffect(Unit) { check() }
                ListItem({ Text("Reconocimiento de escritura") }, supportingContent = { Text("$rec\nSirve para buscar dentro de tus notas y convertir tinta a texto (modelo de ~20 MB que se descarga una vez)") },
                    trailingContent = { TextButton({ rec = "Descargando…"; scope.launch { check() } }) { Text("Reintentar") } })
                Choice("Plantilla por defecto", listOf("Blanco", "Rayado", "Cuadrícula", "Puntos"), tpl) { tpl = it; p.edit().putInt("tpl", it).apply() } } }
            item {
                Header("IA en el dispositivo"); Group {
                var have by remember { mutableStateOf(com.arsnova.data.LocalAi.ready(ctx)) }
                var dl by remember { mutableStateOf<Float?>(null) }
                ListItem({ Text(com.arsnova.data.LocalAi.NAME) },
                    supportingContent = { Column {
                        Text(if (have) "Descargado · funciona sin internet" else "≈547 MB · resúmenes y preguntas de estudio sin conexión")
                        dl?.let { LinearProgressIndicator({ it }, Modifier.fillMaxWidth().padding(top = 6.dp)) } } },
                    trailingContent = {
                        if (have) TextButton({ com.arsnova.data.LocalAi.delete(ctx); have = false }) { Text("Borrar") }
                        else TextButton({ dl = 0f; scope.launch {
                            val ok = runCatching { withContext(Dispatchers.IO) { com.arsnova.data.LocalAi.download(ctx) { dl = it } } }.isSuccess
                            dl = null; have = com.arsnova.data.LocalAi.ready(ctx)
                            if (!ok) android.widget.Toast.makeText(ctx, "Falló la descarga", android.widget.Toast.LENGTH_SHORT).show()
                        } }, enabled = dl == null) { Text("Descargar") }
                    }) } }
            item {
                Header("IA en la nube (para LaTeX y resúmenes)"); Group {
                OutlinedTextField(key, { key = it; p.edit().putString("aiKey", it.trim()).apply() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Clave de API de Gemini o de Claude") }, singleLine = true, supportingText = { Text("Gemini tiene nivel gratuito: crea la clave en aistudio.google.com/apikey. Se guarda solo en este dispositivo.") }) } }
            item {
                Header("Copia de seguridad"); Group {
                val repo = remember { com.arsnova.data.Repo(ctx.applicationContext) }
                var busy by remember { mutableStateOf<String?>(null) }
                val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                    if (uri != null) { busy = "Exportando…"; scope.launch {
                        val ok = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { repo.exportZip(it) } } }.isSuccess; busy = null
                        android.widget.Toast.makeText(ctx, if (ok) "Copia guardada" else "No se pudo guardar la copia", android.widget.Toast.LENGTH_SHORT).show() } } }
                val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) { busy = "Restaurando…"; scope.launch {
                        val n = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { repo.importZip(it) } } }.getOrNull(); busy = null
                        android.widget.Toast.makeText(ctx, if (n != null) "Copia restaurada" else "Ese archivo no es una copia de ArsNova", android.widget.Toast.LENGTH_LONG).show() } } }
                ListItem({ Text("Exportar todo") }, supportingContent = { Text(busy ?: "Guarda todos tus cuadernos en un archivo .zip (tus notas solo viven en esta tablet)") },
                    modifier = Modifier.clickable(enabled = busy == null) { save.launch("ArsNova-copia.zip") })
                ListItem({ Text("Restaurar una copia") }, supportingContent = { Text("Añade los cuadernos de un .zip exportado antes") },
                    modifier = Modifier.clickable(enabled = busy == null) { open.launch(arrayOf("application/zip", "application/octet-stream")) }) } }
            item {
                Header("Actualizaciones"); Group {
                Toggle("Buscar al abrir", "Avisa cuando hay una versión nueva en GitHub", auto) { auto = it; p.edit().putBoolean("autoUpdate", it).apply() }
                ListItem({ Text("Buscar actualización ahora") }, supportingContent = { Text("Versión instalada: ${Updater.current(ctx)}") },
                    trailingContent = { if (checking) CircularProgressIndicator(Modifier.size(20.dp)) },
                    modifier = Modifier.clickable { checking = true; scope.launch {
                        val r = withContext(Dispatchers.IO) { Updater.latest() }; checking = false
                        if (r != null && r.code > Updater.current(ctx)) upd = r
                        else android.widget.Toast.makeText(ctx, if (r == null) "Sin conexión con GitHub" else "Ya tienes la última versión", android.widget.Toast.LENGTH_SHORT).show()
                    } }) } }
            item { Header("Acerca de"); Group {; ListItem({ Text("ArsNova") }, supportingContent = { Text("github.com/${Updater.REPO}") }) }; Spacer(Modifier.height(40.dp)) }
        }
    }
    }
    upd?.let { UpdateDialog(it) { upd = null } }
}

/**
 * Ventana de actualización con la forma de la de BitChord (github.com/kushagrasinghx/BitChord, GPL-3.0,
 * ui/components/UpdateAvailableDialog.kt): tarjeta estrecha tipo alerta de iOS, líneas finísimas, acciones a
 * todo el ancho apiladas bajo el mensaje y el progreso de la descarga como un relleno fino.
 */
@Composable
fun UpdateDialog(r: Updater.Rel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var prog by remember { mutableStateOf<Float?>(null) }
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(220)) }
    androidx.compose.ui.window.Dialog({ if (prog == null) onDismiss() }) {
        Column(Modifier.graphicsLayer { val k = 1.12f - 0.12f * appear.value; scaleX = k; scaleY = k; alpha = appear.value }
            .width(280.dp).clip(RoundedCornerShape(18.dp)).background(Ars.Sheet)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nueva versión disponible", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), textAlign = TextAlign.Center)
                Text("ArsNova ${r.tag}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Ars.Muted, textAlign = TextAlign.Center)
                if (r.notes.isNotBlank()) Text(r.notes, Modifier.padding(top = 10.dp).heightIn(max = 200.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp), textAlign = TextAlign.Center)
                prog?.let { v ->
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Ars.Text.copy(alpha = 0.14f))) {
                        Box(Modifier.fillMaxWidth(v.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Ars.Text)) }
                }
            }
            @Composable fun Action(t: String, strong: Boolean, enabled: Boolean, on: () -> Unit) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ars.Line))
                Box(Modifier.fillMaxWidth().height(46.dp).clickable(enabled = enabled, onClick = on), contentAlignment = Alignment.Center) {
                    Text(t, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium), color = Ars.Text.copy(alpha = if (enabled) 1f else 0.4f)) }
            }
            Action(if (prog == null) "Actualizar" else "Descargando…", true, prog == null) { prog = 0f; scope.launch {
                val f = runCatching { withContext(Dispatchers.IO) { Updater.download(ctx, r) { v -> prog = v } } }.getOrNull()
                prog = null
                if (f != null) { Updater.install(ctx, f); onDismiss() } else android.widget.Toast.makeText(ctx, "Falló la descarga", android.widget.Toast.LENGTH_SHORT).show()
            } }
            Action("Más tarde", false, prog == null, onDismiss)
        }
    }
}
