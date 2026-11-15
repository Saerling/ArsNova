package com.arsnova.ui

// Biblioteca de animaciones y funciones de ArsNova: qué hace cada interruptor, dónde actúa y su vista previa con
// las piezas reales de la app (cada previa fuerza su efecto con FxForzar, sin tocar el ajuste guardado).

import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arsnova.data.Template
import com.arsnova.fx.*
import kotlinx.coroutines.launch

/** Los 12 estilos de diálogo (blocks.so Dialogs 01–12) aplicados al diálogo real de la app. */
fun Modifier.estiloDialogoFx(): Modifier = composed {
    if (!fxOn("dialogs")) return@composed Modifier
    when (Fx.variante("dialogs")) {
        0 -> Modifier.hazBorde(30.dp, Brand)
        1 -> Modifier.bordeBrillante(30.dp)
        2 -> Modifier.noise(.06f)
        3 -> Modifier.foco(Ars.Text)
        4 -> Modifier.hazBorde(30.dp, Brand).noise(.05f)
        5 -> Modifier.aparecerDifuso()
        6 -> Modifier.halo(listOf(Brand, Ars.Text, Brand))
        7 -> Modifier.bordeBrillante(30.dp, listOf(Brand, Ars.Text, Brand))
        8 -> Modifier.patron(Patron.Puntos, Ars.Text.copy(alpha = .10f))
        9 -> Modifier.patron(Patron.Cuadricula, Ars.Text.copy(alpha = .06f))
        10 -> Modifier.hazBorde(30.dp, Brand, ms = 1400).aparecerDifuso()
        else -> Modifier.foco(Brand).noise(.04f)
    }
}

/** Por encima de toda la app: revelado circular al cambiar el tema y la bienvenida (Onboarding). */
@Composable
fun FxCapasArsNova() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rev = rememberRevelar()
    val dark = Ars.dark
    var primero by remember { mutableStateOf(true) }
    val conf = LocalConfiguration.current; val d = LocalDensity.current
    val centro = with(d) { Offset(conf.screenWidthDp.dp.toPx() / 2, conf.screenHeightDp.dp.toPx() / 3) }
    val fondo = Ars.Bg
    LaunchedEffect(dark) { if (primero) primero = false else if (Fx.on("themetoggle")) rev.revelar(centro, fondo) }
    CapaRevelar(rev, Modifier.fillMaxSize())
    val prefs = remember { ctx.getSharedPreferences("fx", android.content.Context.MODE_PRIVATE) }
    var vista by remember { mutableStateOf(prefs.getBoolean("onboarding_visto", false)) }
    if (fxOn("onboarding") && !vista) BienvenidaArsNova { vista = true; prefs.edit().putBoolean("onboarding_visto", true).apply() }
}

@Composable
fun BienvenidaArsNova(onTerminar: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ars.Bg)) {
        CompositionLocalProvider(LocalContentColor provides Ars.Text) {
            Bienvenida(listOf(
                Triple("✍️", "Bienvenido a ArsNova", "Tus apuntes a mano, con lápices de verdad y sin anuncios."),
                Triple("🎛", "La rueda", "Toca dos veces con el lápiz para abrir la rueda de herramientas."),
                Triple("∑", "LaTeX y búsqueda", "Convierte fórmulas a LaTeX y busca palabras escritas a mano."),
            ), onTerminar, color = Brand)
        }
    }
}

/** Temporizadores de estudio en la barra del editor: Pomodoro, cronómetro o respiración (según la variante). */
@Composable
fun TemporizadorEditor() {
    var abierto by remember { mutableStateOf(false) }
    Box {
        IconButton({ abierto = !abierto }) { Text(when (Fx.variante("timers")) { 1 -> "⏱"; 2 -> "🫁"; else -> "🍅" }) }
        DropdownMenu(abierto, { abierto = false }) {
            CompositionLocalProvider(LocalContentColor provides Ars.Text) { Box(Modifier.padding(16.dp)) { Temporizador(Fx.variante("timers"), color = Brand) } }
        }
    }
}

@Composable private fun Variantes(id: String, nombres: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        nombres.forEachIndexed { n, t -> val sel = Fx.variante(id) == n
            Text(t, Modifier.clip(RoundedCornerShape(50)).background(if (sel) Brand else Ars.PillFill).bounceClick { Fx.elegirVariante(id, n) }.padding(horizontal = 12.dp, vertical = 7.dp),
                color = if (sel) androidx.compose.ui.graphics.Color.White else Ars.Text, style = MaterialTheme.typography.labelMedium) }
    }
}

/** La hoja tal como la dibuja el editor, con la plantilla real. */
@Composable private fun HojaPlantilla(t: Template, grano: Boolean = false) {
    val bmp = remember(t, grano) {
        com.arsnova.ink.Ink.granoPrevia = grano
        val b = com.arsnova.ink.Ink.paperPreview(t, -1, 0f, 351, 468).asImageBitmap()
        com.arsnova.ink.Ink.granoPrevia = false; b
    }
    Image(bmp, null, Modifier.width(150.dp).aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)))
}

val IdsFxArsNova = listOf("blurfade", "themetoggle", "noise", "ripple", "switch", "checkbox", "segmented", "dialogs", "progress", "spinner", "textshimmer", "tilt", "magiccard", "glare",
    "hexpattern", "graphpaper", "finegrain", "nf_zoom", "nf_width", "nf_page", "timers", "audiorecorder", "onboarding")

/** El catálogo de ArsNova. Cada vista usa las piezas reales de la app. */
fun catalogoArsNova(prefs: SharedPreferences): List<FxItem> {
    fun i(id: String, n: String, o: String, u: String, v: @Composable () -> Unit) = FxItem(id, n, o, u, vista = v)
    @Composable fun Cuaderno() {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val repo = remember { com.arsnova.data.Repo(ctx.applicationContext) }
        val m = remember { repo.list().firstOrNull() }
        if (m == null) Text("Crea un cuaderno para verlo aquí.", color = Ars.Muted) else NotebookCard(repo, m, Modifier.width(140.dp))
    }
    return listOf(
        i("blurfade", "Blur Fade", "Magic UI", "Los grupos de Ajustes aparecen subiendo con desenfoque") { var k by remember { mutableIntStateOf(0) }; Column { key(k) { Group { Text("Tema", Modifier.padding(18.dp)) } }; TextButton({ k++ }) { Text("Repetir") } } },
        i("themetoggle", "Animated Theme Toggler", "Magic UI", "Al cambiar el tema, el nuevo color se expande en círculo") {
            val r = rememberRevelar(); val sc = rememberCoroutineScope(); val bg = Ars.Bg
            Box(Modifier.fillMaxWidth().height(110.dp)) { CapaRevelar(r, Modifier.matchParentSize()); TextButton({ sc.launch { r.revelar(Offset(200f, 120f), if (Ars.dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black) } }, Modifier.align(Alignment.Center)) { Text("Probar el revelado") } }
        },
        i("noise", "Noise Texture", "Magic UI", "Grano fino en todo el vidrio: barras del editor, la biblioteca y las píldoras") { Pill { IconButton({}) { androidx.compose.material3.Icon(ArsIcons.Pen, null) }; Text("Barra de vidrio", Modifier.padding(end = 14.dp)) } },
        i("ripple", "Ripple Button", "Magic UI", "Onda desde el dedo en todos los botones e íconos de la app") { TextButton({}) { Text("Tócame") } },
        i("switch", "Switch", "coss ui", "Todos los interruptores (Ajustes, barra, lápices, tapa) con el estilo de coss") { var a by remember { mutableStateOf(true) }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Solo lápiz", Modifier.weight(1f)); Switch(a, { a = it }) } },
        i("segmented", "Segmented Control + Animated Background", "coss ui", "Los selectores de Ajustes con indicador que se desliza") { var s by remember { mutableIntStateOf(1) }; CompositionLocalProvider(LocalContentColor provides Ars.Text) { if (fxOn("segmented")) SegmentadoDeslizante(listOf("Suave", "Normal", "Firme"), s, { s = it }, color = Brand) } },
        i("dialogs", "Dialogs 01–12", "blocks.so", "Todos los diálogos de la app con el estilo que elijas (12)") {
            var v by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Variantes("dialogs", (1..12).map { "Estilo %02d".format(it) }); TextButton({ v = true }) { Text("Abrir un diálogo de verdad") } }
            if (v) AlertDialog({ v = false }, { TextButton({ v = false }) { Text("Guardar") } }, dismissButton = { TextButton({ v = false }) { Text("Cancelar") } }, title = { Text("Renombrar") }, text = { Text("Termodinámica · Clase 4") })
        },
        i("checkbox", "Checkbox", "coss ui", "Las casillas de los menús (p. ej. «Solo lápiz») con el estilo de coss") { var a by remember { mutableStateOf(true) }; Row(Modifier.bounceClick { a = !a }, verticalAlignment = Alignment.CenterVertically) { CasillaCoss(a, color = Brand); Text("  Solo lápiz (rechazo de palma)", color = Ars.Text) } },
        i("progress", "Progress", "coss ui", "Las barras de progreso (descarga de la IA, importar PDF, resúmenes) con degradado") { var f by remember { mutableFloatStateOf(.3f) }; LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(600); f = (f + .12f) % 1f } }; Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { ProgresoLinea(f); ProgresoLinea(null) } },
        i("spinner", "Spinner", "coss ui", "El indicador de «Buscar actualización» como girador") { Girador(Modifier.size(32.dp), Ars.Text) },
        i("textshimmer", "Text Shimmer", "Motion Primitives", "«Importando PDF…» con un brillo que lo recorre") { TextoBrillo("Importando PDF…", MaterialTheme.typography.titleLarge, base = Ars.Muted, brillo = Ars.Text) },
        i("finegrain", "Fine Grain", "opensourceui", "Grano de papel en todas las hojas de tus cuadernos") { HojaPlantilla(Template.LINED, grano = true) },
        i("nf_page", "Número de página", "NumberFlow", "El «3/12» del editor rueda al cambiar de página") { var p by remember { mutableIntStateOf(3) }; TextButton({ p = p % 12 + 1 }) { NumeroFlowGrupo(p, 12, MaterialTheme.typography.labelLarge, sep = "/") } },
        i("tilt", "Tilt", "Motion Primitives", "Las tapas de los cuadernos se inclinan bajo el dedo") { Cuaderno() },
        i("magiccard", "Magic Card", "Magic UI", "Un foco de luz sigue al dedo sobre la tapa") { Cuaderno() },
        i("glare", "Glare Hover", "Magic UI", "Destello que cruza la tapa al tocarla") { Cuaderno() },
        i("hexpattern", "Hexagon Pattern", "Magic UI", "Plantilla «Hexagonal» para química orgánica en Líneas y cuadrículas") { HojaPlantilla(Template.HEX) },
        i("graphpaper", "Graph Paper", "opensourceui", "Plantilla «Milimetrado» en Líneas y cuadrículas") { HojaPlantilla(Template.MM) },
        i("nf_zoom", "Zoom con NumberFlow", "NumberFlow", "El % de zoom del editor rueda dígito a dígito") { var z by remember { mutableIntStateOf(100) }; TextButton({ z = if (z >= 300) 100 else z + 25 }) { NumeroFlow(z, MaterialTheme.typography.labelLarge, sufijo = " %") } },
        i("nf_width", "Grosor con NumberFlow", "NumberFlow", "El grosor del lápiz (con decimales) rueda al cambiarlo") { var w by remember { mutableFloatStateOf(1.25f) }; Column(Modifier.width(320.dp)) { Stepper("Grosor", w, 0.1f..20f, 0.05f, " mm") { w = it } } },
        i("timers", "Pomodoro · Stopwatch · Focus Breath", "opensourceui", "Botón de temporizador de estudio en la barra del editor") { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Variantes("timers", listOf("Pomodoro", "Cronómetro", "Respiración")); CompositionLocalProvider(LocalContentColor provides Ars.Text) { Temporizador(Fx.variante("timers"), color = Brand) } } },
        i("audiorecorder", "Audio Recorder", "opensourceui", "Onda animada junto al micrófono mientras grabas audio en una nota") { OndaAudio(true, Modifier.size(90.dp, 28.dp), Brand) },
        i("onboarding", "Onboarding 01–07", "blocks.so", "Bienvenida de 3 pasos la próxima vez que abras ArsNova") { var v by remember { mutableStateOf(false) }; TextButton({ v = true }) { Text("Ver la bienvenida") }
            if (v) androidx.compose.ui.window.Dialog({ v = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { BienvenidaArsNova { v = false } } },
    )
}

/** Barras de progreso (descarga de la IA, importar PDF, resumen): Material o, con «Progress», la de coss. */
@Composable
fun ProgresoLinea(progreso: Float?, modifier: Modifier = Modifier) {
    if (fxOn("progress")) BarraProgresoCoss(progreso, modifier, Brand, Ars.PillFill)
    else if (progreso != null) androidx.compose.material3.LinearProgressIndicator({ progreso }, modifier)
    else androidx.compose.material3.LinearProgressIndicator(modifier)
}
