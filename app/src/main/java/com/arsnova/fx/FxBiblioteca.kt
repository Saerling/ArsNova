package com.arsnova.fx

// Pantalla "Biblioteca de animaciones y funciones" (Ajustes → Personalización)
// y las vistas previas en vivo de cada componente.

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Un componente de la biblioteca. [id] es la clave del interruptor; [uso] dice dónde actúa en esta app. */
data class FxItem(val id: String, val nombre: String, val origen: String, val uso: String, val demo: String = id.substringAfter('.'))

val OrigenesFx = listOf("blocks.so", "Magic UI", "coss ui", "opensourceui", "NumberFlow", "Motion Primitives")
val UrlOrigenFx = mapOf(
    "blocks.so" to "https://blocks.so/", "Magic UI" to "https://magicui.design/docs/components",
    "coss ui" to "https://coss.com/ui/docs/components", "opensourceui" to "https://opensourceui.in/components",
    "NumberFlow" to "https://number-flow.barvian.me/", "Motion Primitives" to "https://motion-primitives.com/docs",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaFxPantalla(items: List<FxItem>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { Fx.init(ctx) }
    var q by remember { mutableStateOf("") }
    var abierto by remember { mutableStateOf<String?>(null) }
    val filtrados = remember(q, items) { if (q.isBlank()) items else items.filter { it.nombre.contains(q, true) || it.uso.contains(q, true) || it.origen.contains(q, true) } }
    val acento = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground) }
            Column(Modifier.weight(1f)) {
                Text("Biblioteca de animaciones y funciones", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                Text("${Fx.count(items.map { it.id })} de ${items.size} activas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(.6f))
            }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Buscar (p. ej. desenfoque, números, lápiz)") }, singleLine = true, shape = RoundedCornerShape(18.dp))
        Text("Todo empieza apagado. Toca un componente para ver la vista previa; el interruptor lo activa en la app.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(.6f))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            OrigenesFx.forEach { origen ->
                val grupo = filtrados.filter { it.origen == origen }
                if (grupo.isNotEmpty()) {
                    item(key = "h_$origen") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(origen, style = MaterialTheme.typography.titleMedium, color = acento, fontWeight = FontWeight.SemiBold)
                                Text(UrlOrigenFx[origen] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(.5f))
                            }
                            val ids = grupo.map { it.id }; val todos = ids.all { Fx.on(it) }
                            TextButton({ Fx.setAll(ids, !todos) }) { Text(if (todos) "Apagar todo" else "Activar todo") }
                        }
                    }
                    items(grupo, key = { it.id }) { it ->
                        val on = Fx.on(it.id); val exp = abierto == it.id
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onBackground.copy(.05f)).animateContentSize()) {
                            Row(Modifier.fillMaxWidth().clickable { abierto = if (exp) null else it.id }.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(it.nombre, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                                    Text(it.uso, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(.6f))
                                }
                                Switch(on, { v -> Fx.set(it.id, v) })
                            }
                            if (exp) Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp).fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111114)).padding(14.dp), contentAlignment = Alignment.Center) {
                                MaterialTheme(colorScheme = darkColorScheme(primary = acento)) { CompositionLocalProvider(LocalContentColor provides Color.White) { DemoFx(it.demo) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val C = Color(0xFF7C9CFF)
@Composable private fun Caja(m: Modifier = Modifier, c: Color = Color(0xFF2A2A34), content: @Composable BoxScope.() -> Unit = {}) = Box(m.clip(RoundedCornerShape(16.dp)).background(c), contentAlignment = Alignment.Center, content = content)
@Composable private fun Portada(m: Modifier = Modifier, i: Int = 0) = Box(m.aspectRatio(.7f).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(listOf(Color(0xFFFF8FAB), Color(0xFF7C3AED)), listOf(Color(0xFF38BDF8), Color(0xFF0F766E)), listOf(Color(0xFFFFD166), Color(0xFFEF476F)))[i % 3])))

/** Vista previa en vivo de cada componente. */
@Composable
fun DemoFx(id: String) {
    val t = MaterialTheme.typography
    when (id) {
        // ── blocks.so
        "chat", "ai", "composer", "chatui" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(18.dp)).background(C).padding(12.dp)) { Text("Resume esta página") }
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A2A34)).padding(12.dp)) { TextoEscribiendo("Claro: la página explica tres ideas principales…", t.bodyMedium) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0xFF1C1C22)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp)) { Text("Pregunta lo que quieras…", Modifier.weight(1f).alpha(.5f)); Text("↑") }
        }
        "cmd", "command", "spotlight" -> { var v by remember { mutableStateOf(false) }; Button({ v = true }) { Text("Abrir menú de comandos") }; MenuComandos(v, listOf("Nueva pestaña" to {}, "Buscar en la biblioteca" to {}, "Ajustes" to {}, "Cambiar tema" to {}, "Historial" to {}), { v = false }) }
        "dash" -> Bento(Modifier.fillMaxWidth(), listOf({ TarjetaEstadistica("Pendientes", 12, 4f, 0) }, { TarjetaEstadistica("Hoy", 3, -2f, 2) }, { TarjetaEstadistica("Aprobadas", 41, 9f, 1) }, { TarjetaEstadistica("Total", 56, 1f, 3) }))
        "dialogs", "dialog", "alertdialog" -> { var v by remember { mutableIntStateOf(-1) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Estilos 01–12", style = t.labelMedium); FlowRowSimple((0 until 12).toList()) { k -> OutlinedButton({ v = k }, contentPadding = PaddingValues(8.dp)) { Text("%02d".format(k + 1)) } } }
            DialogoFx(v >= 0, "¿Borrar elemento?", "Esta acción no se puede deshacer.", v.coerceAtLeast(0), { v = -1 }, "Borrar", peligro = v % 2 == 1) }
        "upload" -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, C.copy(.6f), RoundedCornerShape(18.dp)).hazBorde(18.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("⬆", fontSize = 28.sp); Text("Arrastra o elige un archivo"); Text("PDF, CBZ, imágenes", style = t.labelSmall, modifier = Modifier.alpha(.6f)) }
        "form", "field", "fieldset", "inputs", "floatinglabel", "switchfield", "searchinput", "password", "autocomplete", "combobox" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var vis by remember { mutableStateOf(false) }
            OutlinedTextField(a, { a = it }, Modifier.fillMaxWidth(), label = { Text(if (id == "searchinput") "Buscar" else "Nombre") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            OutlinedTextField(b, { b = it }, Modifier.fillMaxWidth(), label = { Text(if (id == "password") "Contraseña" else "Enlace") }, singleLine = true, shape = RoundedCornerShape(14.dp), visualTransformation = if (id == "password" && !vis) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, trailingIcon = { if (id == "password") TextButton({ vis = !vis }) { Text(if (vis) "Ocultar" else "Ver") } })
            if (id == "autocomplete" || id == "combobox") listOf("github.com", "google.com", "gitlab.com").filter { it.startsWith(a) && a.isNotEmpty() }.forEach { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { a = it }.padding(10.dp)) }
        }
        "grid", "gallerygrid" -> RejillaEscalonada(6, 3, Modifier.fillMaxWidth()) { Portada(Modifier.fillMaxWidth(), it) }
        "login", "loginform" -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("Inicia sesión", style = t.titleLarge); Spacer(Modifier.height(10.dp)); var e by remember { mutableStateOf("") }; OutlinedTextField(e, { e = it }, Modifier.fillMaxWidth(), label = { Text("Correo") }, shape = RoundedCornerShape(14.dp)); Spacer(Modifier.height(10.dp)); BotonMagico("Continuar", Modifier.fillMaxWidth()) {} }
        "onboarding" -> Box(Modifier.height(320.dp)) { Bienvenida(listOf(Triple("👋", "Bienvenido", "Así empieza la app."), Triple("✨", "Personaliza", "Activa lo que te guste."), Triple("🚀", "Listo", "A disfrutar.")), {}) }
        "sidebar" -> { var v by remember { mutableStateOf(false) }; Box(Modifier.fillMaxWidth().height(260.dp)) { Button({ v = true }, Modifier.align(Alignment.Center)) { Text("Abrir panel lateral") }; PanelLateral(v, { v = false }, ancho = 200.dp) { Text("Espacios", style = t.titleMedium); listOf("Personal", "Trabajo", "Universidad").forEach { Text(it, Modifier.padding(vertical = 10.dp)) } } } }
        "stats" -> { var k by remember { mutableIntStateOf(0) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { TarjetaEstadistica("Capítulos leídos", 120 + k * 7, 3.5f, k % 15, Modifier.fillMaxWidth()); TextButton({ k++ }) { Text("Estilo ${k % 15 + 1} de 15 →") } } }
        "tables", "table", "taskstable", "usersselect" -> TablaFx(listOf("Nombre", "Estado"), listOf(listOf("manhwa-online", "Aprobada"), listOf("otra-fuente", "Pendiente"), listOf("ejemplo", "Rechazada")), Modifier.fillMaxWidth(), seleccionable = id == "usersselect" || id == "tables")
        // ── Magic UI
        "progblur" -> Box(Modifier.fillMaxWidth().height(170.dp).desenfoqueProgresivo(70.dp)) { Column { repeat(8) { Text("Línea de contenido número $it que pasa bajo la barra", Modifier.padding(4.dp)) } } }
        "noise", "finegrain" -> Caja(Modifier.fillMaxWidth().height(110.dp).noise(.12f), Color(0xFF3B3B4A)) { Text("Vidrio con grano") }
        "backlight" -> Contraluz(Modifier.size(110.dp, 150.dp)) { Portada(Modifier.fillMaxSize(), 1) }
        "blurfade", "inview", "reveal" -> { var k by remember { mutableIntStateOf(0) }; Column(Modifier.clickable { k++ }) { key(k) { repeat(4) { Text("Elemento ${it + 1}", Modifier.aparecerDifuso(it, 90).padding(6.dp)) } }; Text("Toca para repetir", style = t.labelSmall, modifier = Modifier.alpha(.5f)) } }
        "themetoggle" -> { val r = rememberRevelar(); val sc = rememberCoroutineScope(); var oscuro by remember { mutableStateOf(true) }
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(if (oscuro) Color(0xFF111114) else Color(0xFFF4F4F6))) { CapaRevelar(r, Modifier.matchParentSize()); IconButton({ sc.launch { r.revelar(androidx.compose.ui.geometry.Offset(60f, 60f), if (oscuro) Color(0xFFF4F4F6) else Color(0xFF111114)) }; oscuro = !oscuro }, Modifier.align(Alignment.Center)) { Text(if (oscuro) "☀" else "☾", fontSize = 26.sp, color = if (oscuro) Color.White else Color.Black) } } }
        "scrollprogress" -> { val s = rememberScrollState(); Column { BarraProgresoScroll(if (s.maxValue == 0) 0f else s.value / s.maxValue.toFloat()); Column(Modifier.height(120.dp).verticalScroll(s)) { repeat(20) { Text("Página $it") } } } }
        "marquee", "infiniteslider", "velocity" -> Marquesina(Modifier.fillMaxWidth()) { repeat(8) { Portada(Modifier.width(60.dp), it) } }
        "bento" -> Bento(Modifier.fillMaxWidth(), listOf({ Text("Continuar leyendo") }, { Text("Racha") }, { Text("A") }, { Text("B") }, { Text("C") }))
        "magiccard", "spotlightmp" -> Caja(Modifier.fillMaxWidth().height(120.dp).foco()) { Text("Toca y arrastra") }
        "glare" -> Portada(Modifier.width(100.dp).destello(), 2)
        "numberticker", "slidingnumber", "animatednumber", "numberflow" -> { var n by remember { mutableIntStateOf(1240) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { NumeroFlow(n, t.displaySmall); Row { TextButton({ n -= 37 }) { Text("−") }; TextButton({ n += 137 }) { Text("+") } } } }
        "numbergroup" -> { var p by remember { mutableIntStateOf(12) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { NumeroFlowGrupo(p, 40, t.headlineMedium); Row { TextButton({ p = (p - 1).coerceAtLeast(1) }) { Text("‹") }; TextButton({ p = (p + 1).coerceAtMost(40) }) { Text("›") } } } }
        "numberdecimal" -> { var v by remember { mutableFloatStateOf(1.25f) }; CampoNumero(v, { v = it }, paso = .05f, decimales = 2, sufijo = " mm") }
        "shinytext", "textshimmer", "diareveal" -> TextoBrillo(if (id == "diareveal") "Pensando la respuesta…" else "Nuevo capítulo", t.titleLarge)
        "shimmerwave" -> TextoBrillo("Cargando capítulos", t.titleLarge, ola = true)
        "comictext" -> TextoComic("¡BOOM!")
        "pulsating" -> Box(Modifier.pulso(C, 50.dp).clip(RoundedCornerShape(50)).background(C).padding(horizontal = 22.dp, vertical = 12.dp)) { Text("Continuar leyendo") }
        "circularprogress", "progressring", "meter" -> { var f by remember { mutableFloatStateOf(.62f) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { AnilloProgreso(f, Modifier.size(90.dp)); Slider(f, { f = it }) } }
        "confetti" -> { val c = rememberConfeti(); Box(Modifier.fillMaxWidth().height(160.dp)) { Button({ c.disparar() }, Modifier.align(Alignment.Center)) { Text("¡Terminado!") }; Confeti(c, Modifier.matchParentSize()) } }
        "lens" -> Box(Modifier.fillMaxWidth().height(160.dp).lupa()) { Column { repeat(6) { Text("Mantén pulsado y mueve el dedo para ampliar este texto pequeño", style = t.bodySmall) } } }
        "filetree" -> ArbolArchivos(listOf(Nodo("Biblioteca", listOf(Nodo("Manga", listOf(Nodo("Tomo 1.cbz"), Nodo("Tomo 2.cbz"))), Nodo("Apuntes.pdf"))), Nodo("Descargas", listOf(Nodo("cap-12.cbz")))))
        "ripple" -> Box(Modifier.clip(RoundedCornerShape(50)).background(C).ondaAlTocar().padding(horizontal = 26.dp, vertical = 14.dp)) { Text("Tócame") }
        "borderbeam", "bordertrail" -> Caja(Modifier.fillMaxWidth().height(70.dp).hazBorde(16.dp)) { Text("fyx.app/cargando…") }
        "typing" -> TextoEscribiendo("Escribiendo la respuesta como una IA…", t.titleMedium)
        "hypertext", "scramble" -> { var k by remember { mutableIntStateOf(0) }; Box(Modifier.clickable { k++ }) { key(k) { TextoBarajado("FYX BROWSER", t.headlineSmall) } } }
        "wordrotate", "textloop" -> Row { Text("Buscar ", style = t.titleLarge); TextoRotando(listOf("noticias", "recetas", "manga", "música"), t.titleLarge, color = C) }
        "morphingtext" -> TextoRotando(listOf("Rápido", "Ligero", "Tuyo"), t.headlineMedium, morph = true)
        "textmorph", "textroll" -> { var b by remember { mutableStateOf(false) }; Button({ b = !b }) { TextoMorph(if (b) "Guardado" else "Guardar", t.labelLarge) } }
        "lightrays" -> Box(Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF0B0B12))) { RayosDeLuz(Modifier.matchParentSize()) }
        "dock", "macdock", "appdock", "editordock" -> DockMagnificado(if (id == "editordock") listOf("✏️", "🖍", "🧽", "➰", "🔷", "T", "🖼") else listOf("🏠", "🔍", "📚", "⬇", "⚙"))
        "smoothcursor", "pointer" -> Caja(Modifier.fillMaxWidth().height(130.dp).cursorSuave()) { Text("Mueve el dedo o el lápiz") }
        "avatars", "presencedock", "avatar" -> AvataresSuperpuestos(listOf("J", "A", "M", "S"), extra = 3)
        "animatedlist", "notifications" -> { var k by remember { mutableIntStateOf(0) }; Column(Modifier.clickable { k++ }) { key(k) { ListaAnimada(listOf("Nuevo capítulo de Solo Leveling", "Descarga terminada", "Copia de seguridad lista")) } } }
        "dotpattern", "dotgrid" -> Caja(Modifier.fillMaxWidth().height(110.dp).patron(Patron.Puntos, Color(0x66FFFFFF)), Color(0xFF1C1C22))
        "gridpattern", "graphpaper" -> Caja(Modifier.fillMaxWidth().height(110.dp).patron(if (id == "graphpaper") Patron.Milimetrado else Patron.Cuadricula, Color(0x55FFFFFF)), Color(0xFF1C1C22))
        "linegrid", "notepad" -> Caja(Modifier.fillMaxWidth().height(110.dp).patron(Patron.Lineas, Color(0x6690A4FF)), if (id == "notepad") Color(0xFFFFF8DC) else Color(0xFF1C1C22)) { if (id == "notepad") Text("Mis apuntes de química", color = Color(0xFF333333)) }
        "stripedpattern" -> Caja(Modifier.fillMaxWidth().height(110.dp).patron(Patron.Rayas, Color(0x22FFFFFF)), Color(0xFF1C1C22))
        "hexpattern" -> Caja(Modifier.fillMaxWidth().height(130.dp).patron(Patron.Hexagonos, Color(0x66FFFFFF)), Color(0xFF1C1C22)) { Text("Papel hexagonal") }
        "diagonal" -> Caja(Modifier.fillMaxWidth().height(110.dp).patron(Patron.Diagonal, Color(0x44FFFFFF)), Color(0xFF1C1C22))
        "highlighter" -> { var k by remember { mutableIntStateOf(0) }; Box(Modifier.clickable { k++ }) { key(k) { TextoResaltado("La entalpía es una función de estado", t.titleMedium) } } }
        "devicemocks" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { MarcoDispositivo(0, Modifier.width(70.dp)) { Portada(Modifier.fillMaxSize()) }; MarcoDispositivo(1, Modifier.width(170.dp)) { RayosDeLuz(Modifier.fillMaxSize()) } }
        "gradienttext", "auroratext" -> TextoDegradado(if (id == "auroratext") "Fyx" else "Alejandria", t.displaySmall.copy(fontWeight = FontWeight.Black))
        "shimmerbutton" -> BotonMagico("Shimmer", estilo = 0) {}
        "shinybutton" -> BotonMagico("Shiny", estilo = 1) {}
        "rainbowbutton" -> BotonMagico("Rainbow", estilo = 2) {}
        "hoverbutton" -> BotonMagico("Mantén pulsado", estilo = 3) {}
        "particles" -> Box(Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF07070C))) { Particulas(Modifier.matchParentSize()) }
        "retrogrid" -> Box(Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF07070C))) { RejillaAnimada(Modifier.matchParentSize(), 0) }
        "animgrid" -> Box(Modifier.fillMaxWidth().height(140.dp)) { RejillaAnimada(Modifier.matchParentSize(), 1) }
        "flickergrid" -> Box(Modifier.fillMaxWidth().height(140.dp)) { RejillaAnimada(Modifier.matchParentSize(), 2) }
        "interactivegrid" -> Box(Modifier.fillMaxWidth().height(140.dp)) { RejillaAnimada(Modifier.matchParentSize(), 3) }
        "ripplebg" -> Box(Modifier.fillMaxWidth().height(140.dp)) { FondoOndas(Modifier.matchParentSize()) }
        "neoncard", "glow" -> Box(Modifier.padding(20.dp).halo()) { Caja(Modifier.size(150.dp, 90.dp)) { Text("Brillo") } }
        "shineborder" -> Caja(Modifier.fillMaxWidth().height(80.dp).bordeBrillante(16.dp)) { Text("Borde brillante") }
        "animatedbeam" -> HazEntre(Modifier.fillMaxWidth().height(90.dp))
        "coolmode" -> Box(Modifier.clip(RoundedCornerShape(50)).background(C).modoGenial().padding(horizontal = 24.dp, vertical = 12.dp)) { Text("Toca") }
        "pixelimage" -> { var k by remember { mutableIntStateOf(0) }; Box(Modifier.clickable { k++ }) { key(k) { Portada(Modifier.width(100.dp).pixelarAlEntrar(), 0) } } }
        // ── coss ui (componentes base)
        "switch" -> { var a by remember { mutableStateOf(true) }; Switch(a, { a = it }) }
        "drawer", "sheet" -> { var v by remember { mutableStateOf(false) }; Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))) { Button({ v = !v }, Modifier.align(Alignment.Center)) { Text("Panel inferior") }
            androidx.compose.animation.AnimatedVisibility(v, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF26262E)).noise(.04f).padding(18.dp)) { Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray)); Text("Opciones", style = t.titleMedium); Text("Compartir · Descargar · Borrar", modifier = Modifier.alpha(.7f)) } } } }
        "contextmenu", "menu", "kebab", "filemenu", "sharemenu", "usermenu", "notificationmenu" -> { var v by remember { mutableStateOf(false) }; Box { OutlinedButton({ v = true }) { Text(when (id) { "filemenu" -> "Archivo"; "sharemenu" -> "Compartir"; "usermenu" -> "Perfil"; else -> "⋯ Más" }) }
            DropdownMenu(v, { v = false }, shape = RoundedCornerShape(16.dp)) { when (id) { "sharemenu" -> listOf("Copiar enlace", "Enviar a otro dispositivo", "Compartir…"); "usermenu" -> listOf("Juan", "Cambiar de perfil", "Cerrar sesión"); "filemenu" -> listOf("Nuevo cuaderno", "Importar PDF", "Exportar"); else -> listOf("Editar", "Duplicar", "Borrar") }.forEach { s -> DropdownMenuItem({ Text(s) }, { v = false }) } } } }
        "toast", "toastnotif", "systemalert", "notificationalert" -> { val a = rememberAvisoFx(); Box(Modifier.fillMaxWidth().height(140.dp)) { AvisoFx(a, Modifier.align(Alignment.TopCenter)); Button({ a.mostrar(if (id == "systemalert") "Batería baja" else "Guardado", if (id == "systemalert") "⚠️" else "✅") }, Modifier.align(Alignment.BottomCenter)) { Text("Mostrar aviso") } } }
        "empty" -> EstadoVacio("📭", "Nada por aquí", "Cuando añadas algo aparecerá en esta lista.")
        "skeleton" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Box(Modifier.size(70.dp, 100.dp).esqueleto()) } }
        "slider" -> { var f by remember { mutableFloatStateOf(.4f) }; Column { Slider(f, { f = it }); NumeroFlow((f * 100).toInt(), t.titleMedium, sufijo = " %") } }
        "segmented", "segmentedcontrol", "togglegroup", "tabs", "animatedbg" -> { var s by remember { mutableIntStateOf(0) }; SegmentadoDeslizante(when (id) { "togglegroup" -> listOf("Lápiz", "Marcador", "Borrador"); "tabs" -> listOf("Capítulos", "Info", "Similares"); else -> listOf("Paginado", "Vertical", "Webtoon") }, s, { s = it }) }
        "accordion", "collapsible", "disclosure", "transitionpanel" -> Column(Modifier.fillMaxWidth()) { Desplegable("Lector") { Text("Dirección, zoom, filtros", Modifier.alpha(.7f)) }; Desplegable("Descargas") { Text("Carpeta, solo Wi-Fi", Modifier.alpha(.7f)) } }
        "tooltip" -> { var v by remember { mutableStateOf(false) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { AnimatedVisibility(v) { Text("Pista útil", Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A44)).padding(8.dp)) }; OutlinedButton({ v = !v }) { Text("?") } } }
        "popover", "previewcard", "morphpopover" -> PopoverMorph(if (id == "previewcard") "Mantén un enlace" else "Color") { cerrar -> if (id == "morphpopover" || id == "popover") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(Color(0xFF111111), Color(0xFFE5484D), Color(0xFF2D5BD9), Color(0xFFF2B705), Color(0xFF30A46C)).forEach { c -> Box(Modifier.size(30.dp).clip(CircleShape).background(c).clickable { cerrar() }) } } else { Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(C, Color(0xFF7C3AED))))); Text("wikipedia.org — Termodinámica", Modifier.padding(top = 8.dp)); TextButton(cerrar) { Text("Abrir") } } }
        "progress" -> { var f by remember { mutableFloatStateOf(.3f) }; LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(600); f = (f + .1f) % 1f } }; LinearProgressIndicator({ f }, Modifier.fillMaxWidth()) }
        "spinner", "spinloader" -> Girador(Modifier.size(40.dp))
        "textloader" -> TextoCargando()
        "badge" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { InsigniaBrillo("Nuevo"); InsigniaBrillo("3 sin leer", Color(0xFFFF8FAB)) }
        "select", "radio", "checkbox", "checkboxgroup", "filtersort" -> Column { var s by remember { mutableIntStateOf(0) }; val ch = remember { mutableStateListOf(true, false, true) }
            listOf("Sin leer", "Descargados", "Terminados").forEachIndexed { i, o -> Row(verticalAlignment = Alignment.CenterVertically) { if (id == "radio" || id == "select") RadioButton(s == i, { s = i }) else Checkbox(ch[i], { ch[i] = it }); Text(o) } } }
        "scrollarea" -> Column(Modifier.height(110.dp).fillMaxWidth().verticalScroll(rememberScrollState())) { repeat(15) { Text("Fila $it", Modifier.padding(4.dp)) } }
        "calendar", "datepicker", "daterange" -> { var s by remember { mutableIntStateOf(6) }; TiraSemana(s, { s = it }, conteo = { if (it % 2 == 0) 1 else 0 }) }
        "otp", "otpboxed", "otpunderline" -> { var v by remember { mutableStateOf("") }; CampoOTP(v, { v = it }, subrayado = id == "otpunderline") }
        "kbd", "shortcuts" -> ListaAtajos(listOf("Paleta de comandos" to listOf("Ctrl", "K"), "Nueva pestaña" to listOf("Ctrl", "T"), "Deshacer" to listOf("Ctrl", "Z")), Modifier.fillMaxWidth())
        "numberfield", "stepper" -> { var v by remember { mutableFloatStateOf(2.4f) }; CampoNumero(v, { v = it }, paso = if (id == "stepper") 1f else .1f, decimales = if (id == "stepper") 0 else 1) }
        "toolbar", "toolbardynamic", "toolbarexpandable" -> { var e by remember { mutableStateOf(false) }; BarraDinamica(e, { e = it }, compacta = { listOf("✏️", "🖍", "🧽").forEach { Text(it, Modifier.padding(10.dp), fontSize = 20.sp) }; TextButton({ e = !e }) { Text(if (e) "×" else "+") } }) { Text("Grosor"); var g by remember { mutableFloatStateOf(.4f) }; Slider(g, { g = it }, Modifier.width(220.dp)); Text("Más lápices: pluma, caligrafía, lápiz grafito") } }
        "breadcrumb" -> Row { listOf("Cuadernos", "Universidad", "Termodinámica").forEachIndexed { i, s -> if (i > 0) Text("  ›  ", Modifier.alpha(.5f)); Text(s, color = if (i == 2) C else Color.White) } }
        "copyclip" -> { var c by remember { mutableStateOf(false) }; OutlinedButton({ c = true }) { TextoMorph(if (c) "Copiado ✓" else "Copiar enlace", t.labelLarge) } }
        // ── opensourceui
        "download" -> { var p by remember { mutableStateOf<Float?>(null) }; var l by remember { mutableStateOf(false) }; LaunchedEffect(p != null) { if (p != null) { while ((p ?: 1f) < 1f) { kotlinx.coroutines.delay(120); p = (p ?: 0f) + .05f }; p = null; l = true } }; BotonDescarga(p, l, onClick = { p = 0f }) }
        "follow" -> { var a by remember { mutableStateOf(false) }; BotonEstado(a, "Siguiendo", "Seguir", "＋", color = C) { a = it } }
        "bookmark" -> { var a by remember { mutableStateOf(false) }; BotonEstado(a, "Guardado", "Guardar", "🔖", color = Color(0xFFFFD166)) { a = it } }
        "like" -> { var a by remember { mutableStateOf(false) }; BotonEstado(a, "Me gusta", "Me gusta", "♥") { a = it } }
        "holddelete" -> { var n by remember { mutableIntStateOf(0) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { BotonMantener("Mantén para borrar") { n++ }; Text("Borrados: $n", style = t.labelSmall) } }
        "slideconfirm" -> { var n by remember { mutableIntStateOf(0) }; Column { DeslizarConfirmar("Desliza para borrar el cuaderno") { n++ }; Text("Confirmado $n veces", style = t.labelSmall) } }
        "weekstrip" -> { var s by remember { mutableIntStateOf(6) }; TiraSemana(s, { s = it }, conteo = { listOf(2, 0, 1, 3, 0, 1, 4)[it] }) }
        "activity", "github" -> MapaActividad(List(140) { (0..4).random() })
        "battery", "clock" -> RelojBateria(estilo = t.titleMedium)
        "filmstrip", "contactsheet" -> LazyRowSimple(12) { Portada(Modifier.width(if (id == "filmstrip") 54.dp else 70.dp), it) }
        "glassoverlay" -> VidrioSobreImagen(Modifier.fillMaxWidth().height(170.dp), { Portada(Modifier.fillMaxSize(), 0) }) { Text("Solo Leveling", style = t.titleMedium); Text("Cap. 179 · Acción", style = t.labelSmall) }
        "stackedcards" -> TarjetasApiladas(4, Modifier.fillMaxWidth(), 90.dp) { i -> Caja(Modifier.fillMaxSize(), listOf(Color(0xFF3A3F8F), Color(0xFF8F3A6B), Color(0xFF3A8F6B), Color(0xFF8F6B3A))[i]) { Text("Pestaña ${i + 1}") } }
        "workspaces" -> { var s by remember { mutableIntStateOf(0) }; SegmentadoDeslizante(listOf("🏠 Personal", "💼 Trabajo", "🎓 U"), s, { s = it }) }
        "userprofile", "contactprofile" -> Row(verticalAlignment = Alignment.CenterVertically) { AvataresSuperpuestos(listOf("J")); Spacer(Modifier.width(10.dp)); Column { Text("Juan", style = t.titleMedium); Text("Perfil personal · sincronizado", style = t.labelSmall, modifier = Modifier.alpha(.6f)) } }
        "dropcap", "editorialquote" -> if (id == "dropcap") Row { Text("E", style = t.displayMedium.copy(fontWeight = FontWeight.Black), color = C); Text("l modo lectura pone una letra capital grande al inicio del artículo, como en las revistas.", Modifier.padding(start = 6.dp)) } else Row { Box(Modifier.width(3.dp).height(60.dp).background(C)); Text("“La simplicidad es la máxima sofisticación.”", Modifier.padding(start = 12.dp), style = t.titleMedium.copy(fontWeight = FontWeight.Light)) }
        "ioslock" -> Column(horizontalAlignment = Alignment.CenterHorizontally) { RelojBateria(estilo = t.labelMedium); Text(java.text.SimpleDateFormat("HH:mm").format(java.util.Date()), style = t.displayLarge.copy(fontWeight = FontWeight.Thin)) }
        "hello" -> HolaApple()
        "voice", "recorder", "audiorecorder" -> { var a by remember { mutableStateOf(false) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { OndaAudio(a); TextButton({ a = !a }) { Text(if (a) "■ Detener" else "● Grabar") } } }
        "annotated" -> { var k by remember { mutableIntStateOf(0) }; val tipos = Anotacion.entries; Column(horizontalAlignment = Alignment.CenterHorizontally) { TextoAnotado("ΔG = ΔH − TΔS", tipos[k % tipos.size], t.titleLarge); TextButton({ k++ }) { Text(tipos[k % tipos.size].name + " →") } } }
        "transformbox", "transformplus" -> Box(Modifier.padding(30.dp).size(140.dp, 80.dp).marcoTransformar(mas = id == "transformplus")) { Text("Trazo seleccionado", Modifier.align(Alignment.Center)) }
        "pomodoro" -> Temporizador(0)
        "stopwatch" -> Temporizador(1)
        "breath" -> Temporizador(2)
        "paperfold", "linenweave", "inkwash" -> Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))) { FondoGradiente(FondosGradiente[when (id) { "paperfold" -> "Paper Fold"; "linenweave" -> "Linen Weave"; else -> "Ink Wash" }]!!, Modifier.matchParentSize().noise(.08f), animado = false) }
        "journal" -> Caja(Modifier.fillMaxWidth().height(120.dp).patron(Patron.Lineas, Color(0x55A0522D)), Color(0xFFF5ECD7)) { Text("Querido diario…", color = Color(0xFF5B3A1E), style = t.titleMedium.copy(fontWeight = FontWeight.Light)) }
        "stickypolaroid" -> MarcoPolaroid("Fórmula clave", nota = true, modifier = Modifier.width(130.dp)) { Text("E = mc²", Modifier.align(Alignment.Center), color = Color.Black) }
        "stackedfolder", "foldertab" -> TarjetaCarpeta("Universidad", "12 cuadernos", Color(0xFF2D5BD9), Modifier.width(170.dp))
        "floatingpill" -> Box(Modifier.clip(RoundedCornerShape(50)).background(C).ondaAlTocar().padding(horizontal = 20.dp, vertical = 14.dp)) { Text("＋ Nuevo") }
        "imagecompare" -> Comparador(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)), { Portada(Modifier.fillMaxSize(), 0) }, { Portada(Modifier.fillMaxSize(), 1) })
        "morphdialog" -> TarjetaMorph(cerrada = { Portada(Modifier.width(90.dp), 2) }) { cerrar -> Column(Modifier.fillMaxSize().padding(24.dp)) { Portada(Modifier.width(160.dp), 2); Spacer(Modifier.height(16.dp)); Text("Ficha del manga", style = MaterialTheme.typography.headlineSmall, color = Color.White); Spacer(Modifier.weight(1f)); Button(cerrar) { Text("Cerrar") } } }
        "carousel" -> CarruselEscala(6, Modifier.fillMaxWidth().height(170.dp)) { Portada(Modifier.fillMaxHeight(), it) }
        "tilt" -> Portada(Modifier.width(110.dp).inclinar())
        "magnetic" -> Box(Modifier.magnetico().clip(RoundedCornerShape(50)).background(C).padding(horizontal = 22.dp, vertical = 12.dp)) { Text("Arrástrame") }
        "textanimate", "texteffect" -> { var k by remember { mutableIntStateOf(0) }; Box(Modifier.clickable { k++ }) { key(k) { TextoEntrada("Shiori", t.displaySmall) } } }
        "spinningtext" -> TextoCircular("• FYX • NAVEGADOR • RÁPIDO ")
        "textreveal" -> TextoGiro3D("Alejandria", t.headlineMedium)
        "bgs" -> { var k by remember { mutableIntStateOf(0) }; val ks = FondosGradiente.keys.toList(); Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp))) { FondoGradiente(FondosGradiente[ks[k % ks.size]]!!, Modifier.matchParentSize()) }; TextButton({ k++ }) { Text(ks[k % ks.size] + " →") } } }
        else -> FondoGradiente(FondosGradiente.getValue(FondosGradiente.keys.firstOrNull { it.lowercase().replace(" ", "") == id } ?: "Aurora Background"), Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)))
    }
}

@Composable private fun FlowRowSimple(items: List<Int>, item: @Composable (Int) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { items.chunked(4).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { r.forEach { item(it) } } } }
@Composable private fun LazyRowSimple(n: Int, item: @Composable (Int) -> Unit) = Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(n) { item(it) } }
