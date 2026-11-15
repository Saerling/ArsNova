package com.arsnova.fx

// Kit de animaciones y componentes portados a Jetpack Compose desde:
// blocks.so, Magic UI, coss ui, opensourceui, NumberFlow y Motion Primitives.
// Sin dependencias extra: solo Compose (foundation, ui, material3).
// Cada efecto es un Modifier o un @Composable y se activa desde "Biblioteca de animaciones y funciones".

import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/* ───────────────────────── Estado: interruptores ───────────────────────── */

/** Interruptores de la biblioteca (SharedPreferences "fx"). Todo empieza apagado. */
object Fx {
    private val mapa = mutableStateMapOf<String, Boolean>()
    private var prefs: android.content.SharedPreferences? = null
    fun init(ctx: android.content.Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("fx", android.content.Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) -> if (v is Boolean) mapa[k] = v }
    }
    fun on(id: String): Boolean = mapa[id] == true
    fun set(id: String, v: Boolean) { mapa[id] = v; prefs?.edit()?.putBoolean(id, v)?.apply() }
    fun setAll(ids: List<String>, v: Boolean) { ids.forEach { mapa[it] = v }; prefs?.edit()?.apply { ids.forEach { putBoolean(it, v) } }?.apply() }
    fun count(ids: List<String>) = ids.count { on(it) }
}

/** Aplica [block] solo si el efecto [id] está activado. */
fun Modifier.fx(id: String, block: Modifier.() -> Modifier): Modifier = if (Fx.on(id)) this.block() else this

private val Acc = Color(0xFF7C9CFF)

/* ───────────────────────── Fondos y texturas ───────────────────────── */

/** Grano fino (Magic UI Noise Texture / opensourceui Fine Grain). Se genera una vez y se reutiliza. */
fun Modifier.noise(alpha: Float = 0.06f, seed: Int = 7): Modifier = composed {
    val pts = remember(seed) { val r = Random(seed); List(1400) { Offset(r.nextFloat(), r.nextFloat()) to r.nextFloat() } }
    drawWithCache {
        onDrawWithContent {
            drawContent()
            pts.forEach { (o, b) -> drawCircle(if (b > .5f) Color.White else Color.Black, 0.8f, Offset(o.x * size.width, o.y * size.height), alpha * (0.5f + b)) }
        }
    }
}

enum class Patron { Puntos, Cuadricula, Rayas, Hexagonos, Diagonal, Milimetrado, Lineas }

/** Patrones de fondo: Dot / Grid / Striped / Hexagon Pattern (Magic UI) y Dot Grid, Graph Paper, Line Grid, Diagonal Box (opensourceui). */
fun Modifier.patron(tipo: Patron, color: Color = Color(0x33888888), paso: Dp = 22.dp): Modifier = drawBehind {
    val s = paso.toPx()
    when (tipo) {
        Patron.Puntos -> { var y = s / 2; while (y < size.height) { var x = s / 2; while (x < size.width) { drawCircle(color, 1.6f, Offset(x, y)); x += s }; y += s } }
        Patron.Cuadricula, Patron.Milimetrado -> {
            if (tipo == Patron.Milimetrado) { val f = s / 5; var x = 0f; while (x < size.width) { drawLine(color.copy(alpha = color.alpha * .4f), Offset(x, 0f), Offset(x, size.height), .6f); x += f }; var y = 0f; while (y < size.height) { drawLine(color.copy(alpha = color.alpha * .4f), Offset(0f, y), Offset(size.width, y), .6f); y += f } }
            var x = 0f; while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f); x += s }
            var y = 0f; while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += s }
        }
        Patron.Lineas -> { var y = s * 1.5f; while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1.2f); y += s } }
        Patron.Rayas, Patron.Diagonal -> { var x = -size.height; while (x < size.width) { drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), if (tipo == Patron.Rayas) 6f else 1f); x += s } }
        Patron.Hexagonos -> {
            val r = s / 1.7f; val w = sqrt(3f) * r; var fila = 0; var y = 0f
            while (y < size.height + r) {
                var x = if (fila % 2 == 0) 0f else w / 2
                while (x < size.width + w) {
                    val p = Path(); for (i in 0..5) { val a = Math.toRadians((60 * i - 30).toDouble()); val px = x + r * cos(a).toFloat(); val py = y + r * sin(a).toFloat(); if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }; p.close()
                    drawPath(p, color, style = Stroke(1f)); x += w
                }
                y += r * 1.5f; fila++
            }
        }
    }
}

/** Fondos degradados animados (Aurora, mallas y gradientes de opensourceui + Light Rays de Magic UI). */
@Composable
fun FondoGradiente(colores: List<Color>, modifier: Modifier = Modifier, animado: Boolean = true) {
    val t = if (animado) rememberInfiniteTransition(label = "aur").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "t").value else 0f
    Canvas(modifier) {
        drawRect(colores.first().copy(alpha = 1f))
        colores.drop(1).forEachIndexed { i, c ->
            val a = t * 2 * PI.toFloat() + i * 1.7f
            val cx = size.width * (0.5f + 0.35f * cos(a)); val cy = size.height * (0.5f + 0.35f * sin(a * .8f))
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), size.maxDimension * .6f), size.maxDimension * .6f, Offset(cx, cy))
        }
    }
}

/** Paletas de los fondos de opensourceui (Background-gradient). */
val FondosGradiente: Map<String, List<Color>> = mapOf(
    "Aurora Background" to listOf(Color(0xFF0B1026), Color(0xFF3DDC97), Color(0xFF6C63FF), Color(0xFF00C2FF)),
    "Dark Aurora" to listOf(Color(0xFF05060A), Color(0xFF2E7D6B), Color(0xFF4B3B8F)),
    "Dark Midnight Mesh" to listOf(Color(0xFF070B1A), Color(0xFF1B2A6B), Color(0xFF3A1C71)),
    "Frost Mesh" to listOf(Color(0xFFE8F1FF), Color(0xFFB8D8FF), Color(0xFFDCCBFF)),
    "Mint Lagoon" to listOf(Color(0xFF032A2A), Color(0xFF2EC4B6), Color(0xFF7BF1A8)),
    "Sunset Bloom" to listOf(Color(0xFF2B0A3D), Color(0xFFFF6B6B), Color(0xFFFFB86B)),
    "Sunrise Horizon" to listOf(Color(0xFF1A1033), Color(0xFFFF9A62), Color(0xFFFFE29A)),
    "Rain Prism" to listOf(Color(0xFF101418), Color(0xFF5EE7DF), Color(0xFFB490CA)),
    "Soft Spotlight" to listOf(Color(0xFF0E0E10), Color(0xFF3A3A44)),
    "Arc Bands" to listOf(Color(0xFFF4EFE6), Color(0xFFFFB4A2), Color(0xFFB5E2FA)),
    "Dark Arc Bands" to listOf(Color(0xFF0D0D12), Color(0xFF7F5AF0), Color(0xFF2CB67D)),
    "Dark Teal Depth" to listOf(Color(0xFF02161A), Color(0xFF0E5E6F), Color(0xFF3AB0B8)),
    "Dark Rose Noir" to listOf(Color(0xFF120509), Color(0xFF8E1F3E), Color(0xFFE05780)),
    "Coral Glow" to listOf(Color(0xFF1C0F0F), Color(0xFFFF7F50), Color(0xFFFFC2A8)),
    "Tropical Tide" to listOf(Color(0xFF00202E), Color(0xFF00B4D8), Color(0xFFFFD166)),
    "Cherry Petal" to listOf(Color(0xFFFFF0F3), Color(0xFFFFB3C6), Color(0xFFFF8FAB)),
    "Paper Fold" to listOf(Color(0xFFF3EFE7), Color(0xFFE2DBCD)),
    "Linen Weave" to listOf(Color(0xFFEFE9DD), Color(0xFFD9CFBC)),
    "Ink Wash" to listOf(Color(0xFFE9E7E2), Color(0xFF6B6F76)),
)

/** Light Rays (Magic UI): haces de luz desde arriba. */
@Composable
fun RayosDeLuz(modifier: Modifier = Modifier, color: Color = Color.White) {
    val t by rememberInfiniteTransition(label = "ray").animateFloat(-1f, 1f, infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "r")
    Canvas(modifier) {
        for (i in 0 until 6) {
            val x = size.width * (i / 5f) + t * 30f
            val p = Path().apply { moveTo(x - 10, 0f); lineTo(x + 10, 0f); lineTo(x + 120 + i * 20, size.height); lineTo(x - 60, size.height); close() }
            drawPath(p, Brush.verticalGradient(listOf(color.copy(alpha = .14f), Color.Transparent)))
        }
    }
}

/** Retro Grid / Animated Grid / Flickering Grid / Interactive Grid (Magic UI). */
@Composable
fun RejillaAnimada(modifier: Modifier = Modifier, modo: Int = 0, color: Color = Acc) {
    val t by rememberInfiniteTransition(label = "g").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "g")
    var toque by remember { mutableStateOf<Offset?>(null) }
    Canvas(modifier.pointerInput(Unit) { detectDragGestures(onDragEnd = { toque = null }) { c, _ -> toque = c.position } }) {
        val s = 28.dp.toPx()
        when (modo) {
            0 -> { // retro: perspectiva
                val hz = size.height * .35f
                for (i in -10..10) drawLine(color.copy(.4f), Offset(size.width / 2 + i * s * .4f, hz), Offset(size.width / 2 + i * s * 3f, size.height), 1f)
                for (j in 0..12) { val f = ((j + t) / 12f).pow(2); val y = hz + (size.height - hz) * f; drawLine(color.copy(.4f * f), Offset(0f, y), Offset(size.width, y), 1f) }
            }
            else -> {
                val r = Random(((t * 10).toInt()))
                var y = 0f; var fy = 0
                while (y < size.height) { var x = 0f; var fx = 0
                    while (x < size.width) {
                        val a = when (modo) { 1 -> if ((fx + fy + (t * 20).toInt()) % 9 == 0) .5f else .06f; 2 -> r.nextFloat() * .35f; else -> toque?.let { (1f - (it - Offset(x, y)).getDistance() / (s * 4)).coerceIn(0f, 1f) * .6f } ?: 0.05f }
                        drawRect(color.copy(alpha = a), Offset(x + 1, y + 1), Size(s - 2, s - 2)); x += s; fx++ }
                    y += s; fy++ }
            }
        }
    }
}

/** Ripple (fondo de ondas) y Particles (Magic UI). */
@Composable
fun FondoOndas(modifier: Modifier = Modifier, color: Color = Acc) {
    val t by rememberInfiniteTransition(label = "o").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "o")
    Canvas(modifier) { for (i in 0 until 5) { val f = (t + i / 5f) % 1f; drawCircle(color.copy(alpha = (1 - f) * .3f), size.minDimension * f * .7f, style = Stroke(1.5f)) } }
}

@Composable
fun Particulas(modifier: Modifier = Modifier, n: Int = 40, color: Color = Color.White) {
    val ps = remember { List(n) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * .002f + .0005f, Random.nextFloat() * 2f + 1f) } }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (isActive) { withFrameMillis { tick = it } } }
    Canvas(modifier) { tick; ps.forEach { p -> p[1] = (p[1] - p[2] + 1f) % 1f; drawCircle(color.copy(alpha = .5f), p[3], Offset(p[0] * size.width, p[1] * size.height)) } }
}

/* ───────────────────────── Vidrio y luz ───────────────────────── */

/** Progressive Blur (Magic UI / Motion Primitives): desenfoque que crece hacia un borde. Android 12+; antes, velo degradado. */
fun Modifier.desenfoqueProgresivo(alto: Dp = 90.dp, abajo: Boolean = true, radio: Dp = 18.dp): Modifier = composed {
    if (Build.VERSION.SDK_INT < 31) return@composed drawWithContent {
        drawContent(); val h = alto.toPx()
        drawRect(Brush.verticalGradient(if (abajo) listOf(Color.Transparent, Color.Black.copy(.55f)) else listOf(Color.Black.copy(.55f), Color.Transparent), if (abajo) size.height - h else 0f, if (abajo) size.height else h), Offset(0f, if (abajo) size.height - h else 0f), Size(size.width, h))
    }
    val capa = rememberGraphicsLayer(); val borrosa = rememberGraphicsLayer()
    drawWithContent {
        capa.record { this@drawWithContent.drawContent() }
        drawLayer(capa)
        val h = alto.toPx(); val r = radio.toPx()
        borrosa.renderEffect = BlurEffect(r, r, TileMode.Clamp)
        borrosa.record { drawLayer(capa) }
        drawContext.canvas.saveLayer(Rect(0f, if (abajo) size.height - h else 0f, size.width, if (abajo) size.height else h), Paint())
        drawLayer(borrosa)
        drawRect(Brush.verticalGradient(if (abajo) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent), if (abajo) size.height - h else 0f, if (abajo) size.height else h), blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
    }
}

/** Backlight (Magic UI): resplandor de colores detrás de una imagen, hecho con la propia imagen desenfocada. */
@Composable
fun Contraluz(modifier: Modifier = Modifier, radio: Dp = 40.dp, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (Build.VERSION.SDK_INT >= 31) Box(Modifier.matchParentSize().scale(1.08f).graphicsLayer { renderEffect = BlurEffect(radio.toPx(), radio.toPx(), TileMode.Decal); alpha = .8f }) { content() }
        else Box(Modifier.matchParentSize().scale(1.1f).alpha(.35f)) { content() }
        content()
    }
}

/** Glow Effect (Motion Primitives) / Neon Gradient Card (Magic UI): halo de color girando detrás. */
fun Modifier.halo(colores: List<Color> = listOf(Color(0xFFFF2975), Color(0xFF00FFF1), Color(0xFF7C3AED)), radio: Dp = 22.dp): Modifier = composed {
    val a by rememberInfiniteTransition(label = "h").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "h")
    drawBehind { rotate(a) { drawCircle(Brush.sweepGradient(colores + colores.first()), size.maxDimension * .62f, alpha = .35f) } }
}

/** Border Beam (Magic UI) / Border Trail (Motion Primitives): un haz de luz que recorre el borde. */
fun Modifier.hazBorde(radio: Dp = 20.dp, color: Color = Acc, ancho: Dp = 2.dp, ms: Int = 2600): Modifier = composed {
    val a by rememberInfiniteTransition(label = "b").animateFloat(0f, 360f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "b")
    drawWithContent {
        drawContent()
        val w = ancho.toPx(); val r = radio.toPx()
        rotate(0f) {
            val b = Brush.sweepGradient(0f to Color.Transparent, ((a / 360f) - .08f).coerceAtLeast(0f) to Color.Transparent, (a / 360f) to color, ((a / 360f) + .001f).coerceAtMost(1f) to Color.Transparent, 1f to Color.Transparent, center = center)
            drawRoundRect(b, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(r), style = Stroke(w))
        }
    }
}

/** Shine Border / Shimmer (Magic UI): borde degradado que brilla. */
fun Modifier.bordeBrillante(radio: Dp = 20.dp, colores: List<Color> = listOf(Color(0xFFA07CFE), Color(0xFFFE8FB5), Color(0xFFFFBE7B))): Modifier = composed {
    val t by rememberInfiniteTransition(label = "s").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "s")
    drawWithContent { drawContent(); val o = t * size.width * 2 - size.width
        drawRoundRect(Brush.linearGradient(colores + colores.first(), Offset(o, 0f), Offset(o + size.width, size.height), TileMode.Mirror), cornerRadius = CornerRadius(radio.toPx()), style = Stroke(1.5.dp.toPx())) }
}

/** Magic Card (Magic UI) / Spotlight (Motion Primitives): foco de luz que sigue al dedo. */
fun Modifier.foco(color: Color = Color.White, radio: Dp = 140.dp): Modifier = composed {
    var p by remember { mutableStateOf<Offset?>(null) }
    val a by animateFloatAsState(if (p != null) 1f else 0f, label = "f")
    pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val ch = e.changes.first(); p = if (ch.pressed) ch.position else null } } }
        .drawWithContent { drawContent(); p?.let { drawCircle(Brush.radialGradient(listOf(color.copy(.22f * a), Color.Transparent), it, radio.toPx()), radio.toPx(), it) } }
}

/** Glare Hover (Magic UI): destello diagonal al pulsar. */
fun Modifier.destello(): Modifier = composed {
    val x = remember { Animatable(-1f) }; val sc = rememberCoroutineScope()
    pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); if (e.changes.any { it.pressed && !it.previousPressed }) sc.launch { x.snapTo(-1f); x.animateTo(2f, tween(700)) } } } }
        .drawWithContent { drawContent(); if (x.value > -1f && x.value < 2f) { val o = x.value * size.width
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(.35f), Color.Transparent), Offset(o - 80, 0f), Offset(o + 80, size.height))) } }
}

/** Tilt (Motion Primitives) / Lens 3D: inclinación según dónde toca el dedo. */
fun Modifier.inclinar(max: Float = 10f): Modifier = composed {
    var rx by remember { mutableFloatStateOf(0f) }; var ry by remember { mutableFloatStateOf(0f) }
    val ax by animateFloatAsState(rx, spring(stiffness = 300f), label = "x"); val ay by animateFloatAsState(ry, spring(stiffness = 300f), label = "y")
    pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val c = e.changes.first()
        if (c.pressed) { ry = ((c.position.x / size.width) - .5f) * 2 * max; rx = -((c.position.y / size.height) - .5f) * 2 * max } else { rx = 0f; ry = 0f } } } }
        .graphicsLayer { rotationX = ax; rotationY = ay; cameraDistance = 12 * density }
}

/** Magnetic (Motion Primitives): el elemento se desplaza un poco hacia el dedo. */
fun Modifier.magnetico(fuerza: Float = .25f): Modifier = composed {
    var o by remember { mutableStateOf(Offset.Zero) }
    val ao by animateOffsetAsState(o, spring(dampingRatio = .5f), label = "m")
    pointerInput(Unit) { detectDragGestures(onDragEnd = { o = Offset.Zero }, onDragCancel = { o = Offset.Zero }) { c, _ -> o = (c.position - Offset(size.width / 2f, size.height / 2f)) * fuerza } }
        .graphicsLayer { translationX = ao.x; translationY = ao.y }
}

/** Lens (Magic UI): lupa al mantener pulsado. Dibuja el contenido ampliado dentro de un círculo. */
fun Modifier.lupa(zoom: Float = 2f, radio: Dp = 70.dp): Modifier = composed {
    var p by remember { mutableStateOf<Offset?>(null) }
    val capa = rememberGraphicsLayer()
    pointerInput(Unit) { detectDragGesturesAfterLongPress(onDragStart = { p = it }, onDragEnd = { p = null }, onDragCancel = { p = null }) { c, _ -> p = c.position } }
        .drawWithContent {
            capa.record { this@drawWithContent.drawContent() }
            drawLayer(capa)
            p?.let { c -> val r = radio.toPx(); val centro = Offset(c.x, c.y - r * 1.2f)
                clipPath(Path().apply { addOval(Rect(centro, r)) }) { translate(centro.x - c.x * zoom, centro.y - c.y * zoom) { scale(zoom, Offset.Zero) { drawLayer(capa) } } }
                drawCircle(Color.White.copy(.85f), r, centro, style = Stroke(2.dp.toPx())) }
        }
}

/** Pixel Image (Magic UI): la imagen aparece pixelada y se aclara. */
fun Modifier.pixelarAlEntrar(): Modifier = composed {
    val f = remember { Animatable(0f) }
    LaunchedEffect(Unit) { f.animateTo(1f, tween(900)) }
    graphicsLayer { if (Build.VERSION.SDK_INT >= 31 && f.value < 1f) { val r = (1 - f.value) * 24f + .01f; renderEffect = BlurEffect(r, r, TileMode.Clamp) } else renderEffect = null; alpha = .3f + .7f * f.value }
}

/* ───────────────────────── Entradas y movimiento ───────────────────────── */

/** Blur Fade (Magic UI) / In View (Motion Primitives) / Reveal: aparece desde abajo con desenfoque. */
fun Modifier.aparecerDifuso(indice: Int = 0, pasoMs: Int = 40): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay((indice * pasoMs).toLong().coerceAtMost(600)); a.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
    graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 18.dp.toPx(); if (Build.VERSION.SDK_INT >= 31 && a.value < 1f) { val r = (1 - a.value) * 10f + .01f; renderEffect = BlurEffect(r, r) } else renderEffect = null }
}

/** Pulsating Button (Magic UI): pulso suave alrededor. */
fun Modifier.pulso(color: Color = Acc, radio: Dp = 24.dp): Modifier = composed {
    val t by rememberInfiniteTransition(label = "p").animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "p")
    drawBehind { val e = 10.dp.toPx() * t; drawRoundRect(color.copy(alpha = (1 - t) * .45f), Offset(-e, -e), Size(size.width + 2 * e, size.height + 2 * e), CornerRadius(radio.toPx() + e)) }
}

/** Ripple Button (Magic UI): onda que sale del punto tocado. */
fun Modifier.ondaAlTocar(color: Color = Color.White): Modifier = composed {
    var p by remember { mutableStateOf(Offset.Zero) }; val r = remember { Animatable(1f) }; val s = rememberCoroutineScope()
    pointerInput(Unit) { detectTapGestures(onPress = { p = it; s.launch { r.snapTo(0f); r.animateTo(1f, tween(600)) } }) }
        .drawWithContent { drawContent(); if (r.value < 1f) drawCircle(color.copy(alpha = (1 - r.value) * .35f), size.maxDimension * r.value, p) }
}

/** Confetti (Magic UI). Llama a disparar() para lanzarlo. */
class ConfetiEstado { internal var n by mutableIntStateOf(0); fun disparar() { n++ } }
@Composable fun rememberConfeti() = remember { ConfetiEstado() }
@Composable
fun Confeti(estado: ConfetiEstado, modifier: Modifier = Modifier) {
    val t = remember { Animatable(1f) }
    val ps = remember(estado.n) { List(90) { floatArrayOf(Random.nextFloat() * 2 - 1, -Random.nextFloat() * 1.6f - .4f, Random.nextFloat() * 360, Random.nextInt(5).toFloat()) } }
    LaunchedEffect(estado.n) { if (estado.n > 0) { t.snapTo(0f); t.animateTo(1f, tween(1800, easing = LinearEasing)) } }
    val cs = listOf(Color(0xFFFF4D6D), Color(0xFFFFD166), Color(0xFF06D6A0), Color(0xFF118AB2), Color(0xFFB388FF))
    if (t.value < 1f) Canvas(modifier) {
        val k = t.value
        ps.forEach { p -> val x = size.width / 2 + p[0] * size.width * .6f * k; val y = size.height * .6f + (p[1] * k + 1.4f * k * k) * size.height * .6f
            rotate(p[2] + k * 720, Offset(x, y)) { drawRect(cs[p[3].toInt()].copy(alpha = 1 - k), Offset(x, y), Size(10f, 5f)) } }
    }
}

/** Animated Theme Toggler (Magic UI): revelado circular. Envuelve la app y llama a revelar(punto) antes de cambiar el tema. */
class RevelarEstado { internal var origen by mutableStateOf(Offset.Zero); internal val r = Animatable(1f); internal var color by mutableStateOf(Color.Black) }
@Composable fun rememberRevelar() = remember { RevelarEstado() }
suspend fun RevelarEstado.revelar(desde: Offset, colorNuevo: Color) { origen = desde; color = colorNuevo; r.snapTo(0f); r.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
@Composable
fun CapaRevelar(estado: RevelarEstado, modifier: Modifier = Modifier) {
    if (estado.r.value < 1f) Canvas(modifier) { drawCircle(estado.color, size.maxDimension * 1.5f * estado.r.value, estado.origen, alpha = 1f - estado.r.value * .9f) }
}

/* ───────────────────────── Texto animado ───────────────────────── */

/** Text Animate / Text Effect (Magic UI / Motion Primitives): las letras entran una a una. */
@Composable
fun TextoEntrada(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Row(modifier) { texto.forEachIndexed { i, c -> Text(c.toString(), Modifier.aparecerDifuso(i, 25), style = estilo, color = color) } }
}

/** Typing Animation (Magic UI). */
@Composable
fun TextoEscribiendo(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, msPorLetra: Long = 45, color: Color = Color.Unspecified) {
    var n by remember(texto) { mutableIntStateOf(0) }
    LaunchedEffect(texto) { while (n < texto.length) { delay(msPorLetra); n++ } }
    val cur by rememberInfiniteTransition(label = "c").animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "c")
    Text(buildAnnotatedString { append(texto.take(n)); withStyle(SpanStyle(color = (if (color == Color.Unspecified) Acc else color).copy(alpha = cur))) { append("▍") } }, modifier, style = estilo, color = color)
}

/** Hyper Text (Magic UI) / Text Scramble (Motion Primitives): letras que se barajan hasta formar el texto. */
@Composable
fun TextoBarajado(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    var mostrado by remember(texto) { mutableStateOf(texto) }
    LaunchedEffect(texto) { val ab = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&"; for (k in 0..texto.length) { mostrado = texto.mapIndexed { i, c -> if (i < k || c == ' ') c else ab.random() }.joinToString(""); delay(35) } }
    Text(mostrado, modifier, style = estilo, color = color)
}

/** Word Rotate (Magic UI) / Text Loop (Motion Primitives) / Morphing Text: rota entre palabras. */
@Composable
fun TextoRotando(palabras: List<String>, estilo: TextStyle, modifier: Modifier = Modifier, ms: Long = 2200, color: Color = Color.Unspecified, morph: Boolean = false) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(palabras) { while (isActive) { delay(ms); i = (i + 1) % palabras.size } }
    AnimatedContent(palabras[i], modifier, transitionSpec = {
        if (morph) (fadeIn(tween(400)) + scaleIn(initialScale = .92f)) togetherWith (fadeOut(tween(300)) + scaleOut(targetScale = 1.06f))
        else (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
    }, label = "w") { Text(it, style = estilo, color = color) }
}

/** Text Morph / Text Roll (Motion Primitives): cambia letra por letra al cambiar el texto (p. ej. etiqueta de botón). */
@Composable
fun TextoMorph(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Row(modifier) { texto.forEachIndexed { i, c -> AnimatedContent(c, transitionSpec = { (slideInVertically { it } + fadeIn(tween(200, i * 15))) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "m$i") { Text(it.toString(), style = estilo, color = color) } } }
}

/** Animated Shiny Text / Text Shimmer / Text Shimmer Wave / Dia Text Reveal: barrido de brillo sobre el texto. */
@Composable
fun TextoBrillo(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, base: Color = Color(0xFF9AA0AA), brillo: Color = Color.White, ola: Boolean = false) {
    val t by rememberInfiniteTransition(label = "sh").animateFloat(-1f, 2f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sh")
    if (ola) Row(modifier) { texto.forEachIndexed { i, c -> val y = sin((t * 3 - i * .25f)).coerceAtLeast(0f); Text(c.toString(), Modifier.graphicsLayer { translationY = -y * 4f }, style = estilo, color = lerp(base, brillo, y)) } }
    else Text(texto, modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent { drawContent()
        drawRect(Brush.linearGradient(listOf(base, brillo, base), Offset(size.width * t - 120, 0f), Offset(size.width * t + 120, 0f)), blendMode = BlendMode.SrcIn) }, style = estilo)
}

/** Animated Gradient Text / Aurora Text (Magic UI): texto con degradado que se mueve. */
@Composable
fun TextoDegradado(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, colores: List<Color> = listOf(Color(0xFFFF0080), Color(0xFF7928CA), Color(0xFF0070F3), Color(0xFF38BDF8))) {
    val t by rememberInfiniteTransition(label = "gt").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "gt")
    Text(texto, modifier, style = estilo.copy(brush = Brush.linearGradient(colores + colores.first(), Offset(t * 1000f, 0f), Offset(t * 1000f + 600f, 300f), TileMode.Mirror)))
}

/** Text Highlighter (Magic UI): resaltador que se pinta detrás del texto. */
@Composable
fun TextoResaltado(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color(0x88FFD54F)) {
    val f = remember { Animatable(0f) }; LaunchedEffect(texto) { f.animateTo(1f, tween(700, 150)) }
    Text(texto, modifier.drawBehind { drawRoundRect(color, Offset(-4f, size.height * .45f), Size((size.width + 8) * f.value, size.height * .5f), CornerRadius(6f)) }, style = estilo)
}

enum class Anotacion { Subrayado, Ondulado, Circulo, Tachado, Caja, Flecha, Resaltado }

/** Annotated Text (opensourceui): anotaciones a mano alzada (subrayado ondulado, círculo, tachado…). */
@Composable
fun TextoAnotado(texto: String, tipo: Anotacion, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color(0xFFE5484D)) {
    val f = remember { Animatable(0f) }; LaunchedEffect(texto, tipo) { f.snapTo(0f); f.animateTo(1f, tween(800, 100)) }
    val rnd = remember(texto) { Random(texto.hashCode()) }; val j = remember(texto) { List(8) { rnd.nextFloat() * 4 - 2 } }
    Text(texto, modifier.padding(horizontal = 6.dp, vertical = 4.dp).drawWithContent {
        if (tipo == Anotacion.Resaltado) drawRect(color.copy(.3f), Offset(0f, size.height * .2f), Size(size.width * f.value, size.height * .7f))
        drawContent()
        val w = size.width; val h = size.height; val k = f.value; val st = Stroke(2.2f, cap = StrokeCap.Round)
        when (tipo) {
            Anotacion.Subrayado -> drawLine(color, Offset(0f, h - 2 + j[0]), Offset(w * k, h - 2 + j[1]), 2.4f, StrokeCap.Round)
            Anotacion.Ondulado -> { val p = Path(); var x = 0f; p.moveTo(0f, h - 2); while (x < w * k) { p.quadraticTo(x + 6, h - 2 + if ((x / 12).toInt() % 2 == 0) 4f else -4f, x + 12, h - 2); x += 12 }; drawPath(p, color, style = st) }
            Anotacion.Tachado -> drawLine(color, Offset(0f, h / 2 + j[0]), Offset(w * k, h / 2 + j[1]), 2.4f, StrokeCap.Round)
            Anotacion.Circulo -> drawArc(color, -90f + j[2], 370f * k, false, Offset(-6f, -4f), Size(w + 12, h + 8), style = st)
            Anotacion.Caja -> { val p = Path(); p.moveTo(j[0], j[1]); p.lineTo(w + j[2], j[3]); p.lineTo(w + j[4], h + j[5]); p.lineTo(j[6], h + j[7]); p.close(); val m = PathMeasure(); m.setPath(p, false); val d = Path(); m.getSegment(0f, m.length * k, d); drawPath(d, color, style = st) }
            Anotacion.Flecha -> { drawLine(color, Offset(w * .1f, h + 14), Offset(w * .1f + (w * .8f) * k, h + 4), 2.4f, StrokeCap.Round); if (k > .95f) { drawLine(color, Offset(w * .9f, h + 4), Offset(w * .9f - 10, h - 2), 2.4f); drawLine(color, Offset(w * .9f, h + 4), Offset(w * .9f - 10, h + 12), 2.4f) } }
            Anotacion.Resaltado -> Unit
        }
    }, style = estilo)
}

/** Comic Text (Magic UI): letras de cómic con contorno y sombra dura. */
@Composable
fun TextoComic(texto: String, modifier: Modifier = Modifier, tam: TextUnit = 34.sp, color: Color = Color(0xFFFFD60A)) {
    val a = remember { Animatable(.6f) }; LaunchedEffect(texto) { a.animateTo(1f, spring(dampingRatio = .45f)) }
    Box(modifier.graphicsLayer { scaleX = a.value; scaleY = a.value; rotationZ = -3f }) {
        Text(texto, Modifier.offset(3.dp, 3.dp), fontSize = tam, fontWeight = FontWeight.Black, color = Color.Black)
        Text(texto, fontSize = tam, fontWeight = FontWeight.Black, style = TextStyle(drawStyle = Stroke(6f)), color = Color.Black)
        Text(texto, fontSize = tam, fontWeight = FontWeight.Black, color = color)
    }
}

/** Spinning Text (Magic UI / Motion Primitives): texto girando en círculo. */
@Composable
fun TextoCircular(texto: String, modifier: Modifier = Modifier.size(110.dp), color: Color = Color.White) {
    val a by rememberInfiniteTransition(label = "sp").animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "sp")
    val med = rememberTextMeasurer()
    Canvas(modifier) { rotate(a) { val n = texto.length; texto.forEachIndexed { i, c -> rotate(360f * i / n) { val l = med.measure(c.toString(), TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)); drawText(l, topLeft = Offset(center.x - l.size.width / 2, 0f)) } } } }
}

/** Line Shadow Text / Kinetic Text / Text 3D Flip (Magic UI): texto con sombra de líneas y giro 3D por letra. */
@Composable
fun TextoGiro3D(texto: String, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    var k by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { while (isActive) { delay(2600); k++ } }
    Row(modifier) { texto.forEachIndexed { i, c -> val r by animateFloatAsState(k * 360f, tween(600, i * 40), label = "r$i"); Text(c.toString(), Modifier.graphicsLayer { rotationX = r; cameraDistance = 8 * density }, style = estilo, color = color) } }
}

/** Scroll Based Velocity / Marquee (Magic UI) / Infinite Slider (Motion Primitives). */
@Composable
fun Marquesina(modifier: Modifier = Modifier, velocidad: Int = 40, content: @Composable RowScope.() -> Unit) {
    Row(modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = velocidad.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/* ───────────────────────── Números (NumberFlow) ───────────────────────── */

/**
 * NumberFlow (barvian) / Number Ticker (Magic UI) / Sliding Number y Animated Number (Motion Primitives):
 * cada dígito rueda hacia arriba o abajo según suba o baje el número. Respeta "quitar animaciones".
 */
@Composable
fun NumeroFlow(valor: Number, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, prefijo: String = "", sufijo: String = "", decimales: Int = 0) {
    val texto = remember(valor, decimales) { if (decimales > 0) String.format(java.util.Locale.US, "%.${decimales}f", valor.toDouble()) else valor.toLong().toString() }
    var previo by remember { mutableDoubleStateOf(valor.toDouble()) }
    val sube = valor.toDouble() >= previo
    SideEffect { previo = valor.toDouble() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prefijo.isNotEmpty()) Text(prefijo, style = estilo, color = color)
        texto.padStart(texto.length).forEachIndexed { i, c ->
            key(texto.length - i) {
                AnimatedContent(c, transitionSpec = {
                    val d = if (sube) 1 else -1
                    (slideInVertically(spring(dampingRatio = .8f, stiffness = 400f)) { it * d } + fadeIn()) togetherWith (slideOutVertically { -it * d } + fadeOut()) using SizeTransform(clip = true)
                }, label = "d") { Text(it.toString(), style = estilo.copy(fontFeatureSettings = "tnum"), color = color) }
            }
        }
        if (sufijo.isNotEmpty()) Text(sufijo, style = estilo, color = color)
    }
}

/** NumberFlowGroup: "12 / 40" con los dos números moviéndose juntos. */
@Composable
fun NumeroFlowGrupo(a: Int, b: Int, estilo: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, sep: String = " / ") {
    Row(modifier.animateContentSize(), verticalAlignment = Alignment.CenterVertically) { NumeroFlow(a, estilo, color = color); Text(sep, style = estilo, color = color); NumeroFlow(b, estilo, color = color) }
}

/* ───────────────────────── Progreso y carga ───────────────────────── */

/** Scroll Progress (Magic UI / Motion Primitives): barra fina arriba con el avance. */
@Composable
fun BarraProgresoScroll(fraccion: Float, modifier: Modifier = Modifier, color: Color = Acc, colores: List<Color>? = null) {
    val f by animateFloatAsState(fraccion.coerceIn(0f, 1f), spring(stiffness = 500f), label = "sp")
    Box(modifier.fillMaxWidth().height(3.dp)) { Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(colores?.let { Brush.horizontalGradient(it) } ?: SolidColor(color))) }
}

/** Animated Circular Progress Bar (Magic UI) / Progress Ring (opensourceui) / Meter (coss). */
@Composable
fun AnilloProgreso(fraccion: Float, modifier: Modifier = Modifier.size(56.dp), color: Color = Acc, grosor: Dp = 5.dp, conTexto: Boolean = true, estilo: TextStyle = MaterialTheme.typography.labelMedium) {
    val f by animateFloatAsState(fraccion.coerceIn(0f, 1f), tween(600), label = "ar")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { val s = grosor.toPx(); drawArc(color.copy(.18f), 0f, 360f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s)); drawArc(color, -90f, 360f * f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round)) }
        if (conTexto) NumeroFlow((f * 100).roundToInt(), estilo, sufijo = "%")
    }
}

/** Spinner (coss) / Spin Loader (opensourceui). */
@Composable
fun Girador(modifier: Modifier = Modifier.size(28.dp), color: Color = Acc) {
    val a by rememberInfiniteTransition(label = "sp").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Canvas(modifier) { for (i in 0 until 8) rotate(a + i * 45f) { drawLine(color.copy(alpha = (i + 1) / 8f), Offset(center.x, size.height * .1f), Offset(center.x, size.height * .3f), 3f, StrokeCap.Round) } }
}

/** Text Loader (opensourceui): "Cargando" con puntos. */
@Composable
fun TextoCargando(texto: String = "Cargando", estilo: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = Color.Unspecified) {
    var n by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { while (isActive) { delay(350); n = (n + 1) % 4 } }
    Text(texto + ".".repeat(n), style = estilo, color = color)
}

/** Apple Hello Loader (opensourceui): "hola" escrito a mano que se traza. */
@Composable
fun HolaApple(modifier: Modifier = Modifier.size(180.dp, 70.dp), color: Color = Color.White) {
    val f = remember { Animatable(0f) }; LaunchedEffect(Unit) { f.animateTo(1f, tween(2200, easing = LinearEasing)) }
    Canvas(modifier) { val w = size.width; val h = size.height
        val p = Path().apply { moveTo(0f, h * .7f); cubicTo(w * .05f, h * .1f, w * .12f, 0f, w * .12f, h * .3f); lineTo(w * .1f, h * .9f); cubicTo(w * .14f, h * .4f, w * .25f, h * .4f, w * .25f, h * .8f); cubicTo(w * .3f, h * .45f, w * .42f, h * .45f, w * .42f, h * .7f); cubicTo(w * .42f, h, w * .3f, h, w * .32f, h * .7f); cubicTo(w * .5f, h * .2f, w * .55f, 0f, w * .55f, h * .2f); lineTo(w * .53f, h * .9f); cubicTo(w * .6f, h * .4f, w * .72f, h * .5f, w * .7f, h * .8f); cubicTo(w * .78f, h * .45f, w * .9f, h * .45f, w * .88f, h * .8f); lineTo(w, h * .7f) }
        val m = PathMeasure(); m.setPath(p, false); val d = Path(); m.getSegment(0f, m.length * f.value, d); drawPath(d, color, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

/** Skeleton (coss): rectángulo con brillo mientras carga. */
fun Modifier.esqueleto(radio: Dp = 14.dp, base: Color = Color(0x22FFFFFF)): Modifier = composed {
    val t by rememberInfiniteTransition(label = "sk").animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sk")
    drawBehind { drawRoundRect(base, cornerRadius = CornerRadius(radio.toPx())); drawRoundRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(.08f), Color.Transparent), Offset(size.width * t - 200, 0f), Offset(size.width * t + 200, 0f)), cornerRadius = CornerRadius(radio.toPx())) }
}

/* ───────────────────────── Botones e interacción ───────────────────────── */

private val Pildora = RoundedCornerShape(50)

/** Shimmer / Shiny / Rainbow / Interactive Hover Button (Magic UI). estilo: 0 shimmer, 1 shiny, 2 rainbow, 3 interactivo. */
@Composable
fun BotonMagico(texto: String, modifier: Modifier = Modifier, estilo: Int = 0, onClick: () -> Unit) {
    val t by rememberInfiniteTransition(label = "bm").animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "bm")
    val src = remember { MutableInteractionSource() }; val pr by src.collectIsPressedAsState()
    val fondo: Brush = when (estilo) { 2 -> Brush.linearGradient(listOf(Color(0xFFFF0040), Color(0xFFFFB800), Color(0xFF00FF88), Color(0xFF00B7FF), Color(0xFFB000FF), Color(0xFFFF0040)), Offset(t * 800, 0f), Offset(t * 800 + 400, 0f), TileMode.Repeated); 3 -> SolidColor(if (pr) Acc else Color(0xFF1C1C22)); else -> SolidColor(Color(0xFF111114)) }
    Box(modifier.clip(Pildora).background(fondo).then(if (estilo != 2) Modifier.bordeBrillante(50.dp) else Modifier)
        .clickable(src, null, onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        if (estilo == 1) TextoBrillo(texto, MaterialTheme.typography.labelLarge) else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(texto, color = Color.White, style = MaterialTheme.typography.labelLarge)
            if (estilo == 3) AnimatedVisibility(pr) { Text("  →", color = Color.White) }
        }
        if (estilo == 0) Box(Modifier.matchParentSize().drawBehind { val o = size.width * (t * 2 - .5f); drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(.25f), Color.Transparent), Offset(o - 40, 0f), Offset(o + 40, size.height))) })
    }
}

/** Like Toggle / Bookmark Save / Follow Toggle (opensourceui): botón que rebota y cambia de estado. */
@Composable
fun BotonEstado(activo: Boolean, textoOn: String, textoOff: String, simbolo: String, modifier: Modifier = Modifier, color: Color = Color(0xFFFF4D6D), onCambio: (Boolean) -> Unit) {
    val s = remember { Animatable(1f) }; val sc = rememberCoroutineScope()
    Row(modifier.graphicsLayer { scaleX = s.value; scaleY = s.value }.clip(Pildora).background(if (activo) color.copy(.18f) else Color(0x1FFFFFFF))
        .clickable { onCambio(!activo); sc.launch { s.snapTo(.8f); s.animateTo(1f, spring(dampingRatio = .35f, stiffness = 500f)) } }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(simbolo, color = if (activo) color else Color.Gray); Spacer(Modifier.width(8.dp))
        TextoMorph(if (activo) textoOn else textoOff, MaterialTheme.typography.labelLarge, color = if (activo) color else Color.Unspecified)
    }
}

/** Download with States (opensourceui): Descargar → progreso → Listo. */
@Composable
fun BotonDescarga(progreso: Float?, listo: Boolean, modifier: Modifier = Modifier, color: Color = Acc, onClick: () -> Unit) {
    Box(modifier.animateContentSize().clip(Pildora).background(color.copy(.16f)).clickable(enabled = progreso == null && !listo, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(when { listo -> 2; progreso != null -> 1; else -> 0 }, label = "dl") { e ->
            when (e) { 2 -> Text("✓ Listo", color = color); 1 -> Row(verticalAlignment = Alignment.CenterVertically) { AnilloProgreso(progreso ?: 0f, Modifier.size(22.dp), color, 3.dp, conTexto = false); Spacer(Modifier.width(8.dp)); NumeroFlow(((progreso ?: 0f) * 100).roundToInt(), MaterialTheme.typography.labelLarge, color = color, sufijo = "%") }; else -> Text("↓ Descargar", color = color) }
        }
    }
}

/** Hold to Delete (opensourceui): mantener pulsado para confirmar. */
@Composable
fun BotonMantener(texto: String, modifier: Modifier = Modifier, color: Color = Color(0xFFE5484D), ms: Int = 1200, onConfirmar: () -> Unit) {
    val f = remember { Animatable(0f) }; val sc = rememberCoroutineScope()
    Box(modifier.clip(Pildora).background(color.copy(.15f)).drawBehind { drawRect(color.copy(.45f), size = Size(size.width * f.value, size.height)) }
        .pointerInput(Unit) { detectTapGestures(onPress = { val j = sc.launch { f.animateTo(1f, tween(ms, easing = LinearEasing)); onConfirmar(); f.snapTo(0f) }; tryAwaitRelease(); j.cancel(); sc.launch { f.animateTo(0f, tween(250)) } }) }
        .padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text(texto, color = color, style = MaterialTheme.typography.labelLarge) }
}

/** Slide to Confirm (opensourceui). */
@Composable
fun DeslizarConfirmar(texto: String, modifier: Modifier = Modifier, color: Color = Acc, onConfirmar: () -> Unit) {
    var ancho by remember { mutableFloatStateOf(1f) }; val x = remember { Animatable(0f) }; val sc = rememberCoroutineScope(); val d = LocalDensity.current
    val pomo = with(d) { 48.dp.toPx() }
    Box(modifier.fillMaxWidth().height(56.dp).clip(Pildora).background(color.copy(.14f)).onSizeChanged { ancho = it.width.toFloat() }, contentAlignment = Alignment.Center) {
        TextoBrillo(texto, MaterialTheme.typography.labelLarge, Modifier.alpha(1f - x.value / (ancho - pomo).coerceAtLeast(1f)))
        Box(Modifier.align(Alignment.CenterStart).offset { IntOffset(x.value.roundToInt(), 0) }.padding(4.dp).size(48.dp).clip(CircleShape).background(color)
            .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = { sc.launch { if (x.value > (ancho - pomo) * .85f) { x.animateTo(ancho - pomo - 8); onConfirmar(); delay(400) }; x.animateTo(0f, spring()) } }) { _, dx -> sc.launch { x.snapTo((x.value + dx).coerceIn(0f, ancho - pomo - 8)) } } },
            contentAlignment = Alignment.Center) { Text("›", color = Color.White, fontSize = 24.sp) }
    }
}

/** Quantity Stepper (opensourceui) / Number Field (coss): − valor + con decimales. */
@Composable
fun CampoNumero(valor: Float, onCambio: (Float) -> Unit, modifier: Modifier = Modifier, paso: Float = .1f, min: Float = 0f, max: Float = 100f, decimales: Int = 1, sufijo: String = "") {
    Row(modifier.clip(Pildora).background(Color(0x1FFFFFFF)), verticalAlignment = Alignment.CenterVertically) {
        TextButton({ onCambio((valor - paso).coerceIn(min, max)) }) { Text("−", fontSize = 20.sp) }
        NumeroFlow(valor, MaterialTheme.typography.titleMedium, decimales = decimales, sufijo = sufijo)
        TextButton({ onCambio((valor + paso).coerceIn(min, max)) }) { Text("+", fontSize = 20.sp) }
    }
}

/** Kbd (coss): tecla. */
@Composable fun Tecla(t: String) = Text(t, Modifier.border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp)).background(Color(0x14FFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)

/** Keyboard Shortcuts (opensourceui). */
@Composable
fun ListaAtajos(atajos: List<Pair<String, List<String>>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { atajos.forEach { (t, ks) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(t, Modifier.weight(1f)); ks.forEach { Tecla(it); Spacer(Modifier.width(4.dp)) } } } }
}

/** Animated Background (Motion Primitives) / Segmented Control (coss / opensourceui): indicador que se desliza. */
@Composable
fun SegmentadoDeslizante(opciones: List<String>, sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier, color: Color = Acc) {
    var anchos by remember { mutableStateOf(List(opciones.size) { 0f }) }
    val x by animateFloatAsState(anchos.take(sel).sum(), spring(dampingRatio = .75f, stiffness = 500f), label = "x")
    val w by animateFloatAsState(anchos.getOrElse(sel) { 0f }, spring(dampingRatio = .75f, stiffness = 500f), label = "w")
    val d = LocalDensity.current
    Box(modifier.clip(Pildora).background(Color(0x1FFFFFFF)).padding(3.dp)) {
        Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(with(d) { w.toDp() }).matchParentSize().let { it }.clip(Pildora))
        Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.size(with(d) { w.toDp() }, 36.dp).clip(Pildora).background(color))
        Row { opciones.forEachIndexed { i, o -> Box(Modifier.height(36.dp).onSizeChanged { s -> anchos = anchos.toMutableList().also { it[i] = s.width.toFloat() } }.clip(Pildora).clickable { onSel(i) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(o, color = if (i == sel) Color.White else Color.Unspecified, style = MaterialTheme.typography.labelLarge) } } }
    }
}

/* ───────────────────────── Barras, docks y paneles ───────────────────────── */

/** Toolbar Dynamic / Toolbar Expandable (Motion Primitives): barra compacta que se ensancha para mostrar más. */
@Composable
fun BarraDinamica(expandida: Boolean, onExpandir: (Boolean) -> Unit, modifier: Modifier = Modifier, fondo: Color = Color(0xEE1C1C22), compacta: @Composable RowScope.() -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.animateContentSize(spring(dampingRatio = .78f, stiffness = 420f)).clip(RoundedCornerShape(26.dp)).background(fondo).padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, content = compacta)
        AnimatedVisibility(expandida, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) { Column(Modifier.padding(8.dp), content = extra) }
    }
}

/** Dock (Magic UI) / Mac Dock (opensourceui): íconos que se agrandan bajo el dedo. */
@Composable
fun DockMagnificado(items: List<String>, modifier: Modifier = Modifier, onClick: (Int) -> Unit = {}) {
    var px by remember { mutableStateOf<Float?>(null) }; var pos by remember { mutableStateOf(List(items.size) { 0f }) }
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xCC1C1C22)).padding(8.dp)
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val c = e.changes.first(); px = if (c.pressed) c.position.x else null } } },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        items.forEachIndexed { i, s -> val dist = px?.let { abs(it - pos[i]) } ?: 999f; val k by animateFloatAsState(1f + (1f - (dist / 160f)).coerceIn(0f, 1f) * .6f, spring(stiffness = 600f), label = "k$i")
            Box(Modifier.onSizeChanged { }.layoutPos { x -> pos = pos.toMutableList().also { it[i] = x } }.size((40 * k).dp).clip(RoundedCornerShape((12 * k).dp)).background(Color(0x33FFFFFF)).clickable { onClick(i) }, contentAlignment = Alignment.Center) { Text(s, fontSize = (16 * k).sp) } }
    }
}
private fun Modifier.layoutPos(on: (Float) -> Unit): Modifier = this.onGloballyPositioned { c -> on(c.positionInParent().x + c.size.width / 2f) }

/** Transition Panel / Disclosure / Accordion / Collapsible (Motion Primitives + coss). */
@Composable
fun Desplegable(titulo: String, modifier: Modifier = Modifier, inicial: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var abierto by remember { mutableStateOf(inicial) }; val r by animateFloatAsState(if (abierto) 180f else 0f, label = "r")
    Column(modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Text("⌄", Modifier.rotate(r)) }
        if (abierto) Column(content = content)
    }
}

/** Animated List (Magic UI): los avisos entran apilándose. */
@Composable
fun ListaAnimada(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { items.forEachIndexed { i, s -> key(s) { Box(Modifier.aparecerDifuso(i, 120).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF)).padding(14.dp)) { Text(s) } } } }
}

/** Stacked Cards Effect (opensourceui): tarjetas apiladas que se abren en abanico al tocar. */
@Composable
fun TarjetasApiladas(n: Int, modifier: Modifier = Modifier, alto: Dp = 120.dp, tarjeta: @Composable (Int) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier.height(if (abierto) alto * n * .7f else alto + 24.dp).animateContentSize().clickable { abierto = !abierto }) {
        for (i in (n - 1) downTo 0) { val y by animateDpAsState(if (abierto) alto * .7f * i else 8.dp * i, spring(dampingRatio = .7f), label = "y$i"); val s by animateFloatAsState(if (abierto) 1f else 1f - i * .05f, label = "s$i")
            Box(Modifier.offset(y = y).scale(s).fillMaxWidth().height(alto).clip(RoundedCornerShape(22.dp))) { tarjeta(i) } }
    }
}

/** Carousel (Motion Primitives) / Film Strip / Gallery (opensourceui): carrusel con escala. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarruselEscala(n: Int, modifier: Modifier = Modifier, item: @Composable (Int) -> Unit) {
    val st = rememberPagerState { n }
    HorizontalPager(st, modifier, contentPadding = PaddingValues(horizontal = 56.dp), pageSpacing = 12.dp) { p ->
        val off = ((st.currentPage - p) + st.currentPageOffsetFraction).absoluteValue
        Box(Modifier.graphicsLayer { val k = 1f - off.coerceIn(0f, 1f) * .15f; scaleX = k; scaleY = k; alpha = 1f - off.coerceIn(0f, 1f) * .4f }) { item(p) }
    }
}

/* ───────────────────────── Calendario, actividad y widgets ───────────────────────── */

/** Github Contribution / Daily Activity Calendar (opensourceui): mapa de calor por días. */
@Composable
fun MapaActividad(valores: List<Int>, modifier: Modifier = Modifier, color: Color = Color(0xFF39D353), semanas: Int = 20) {
    val mx = (valores.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(100.dp)) { val c = size.height / 7f; for (w in 0 until semanas) for (d in 0 until 7) { val i = valores.size - (semanas - w) * 7 + d; val v = valores.getOrNull(i) ?: 0
        drawRoundRect(if (v == 0) Color(0x22FFFFFF) else color.copy(alpha = .25f + .75f * v / mx), Offset(w * c * 1.15f, d * c), Size(c * .85f, c * .85f), CornerRadius(3f)) } }
}

/** Week Strip Calendar (opensourceui): tira de la semana con el día elegido. */
@Composable
fun TiraSemana(sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier, conteo: (Int) -> Int = { 0 }, color: Color = Acc) {
    val hoy = java.util.Calendar.getInstance(); val dias = listOf("L", "M", "X", "J", "V", "S", "D")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) { val c = (hoy.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, i - 6) }; val a = i == sel
            val bg by animateColorAsState(if (a) color else Color.Transparent, label = "bg$i")
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onSel(i) }.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dias[(c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7], style = MaterialTheme.typography.labelSmall); Text("${c.get(java.util.Calendar.DAY_OF_MONTH)}", style = MaterialTheme.typography.titleMedium)
                val n = conteo(i); Box(Modifier.size(5.dp).clip(CircleShape).background(if (n > 0) (if (a) Color.White else color) else Color.Transparent)) } }
    }
}

/** Battery Face + iOS Digital Clock (opensourceui): hora y batería, como en el lector de Mihon. */
@Composable
fun RelojBateria(modifier: Modifier = Modifier, estilo: TextStyle = MaterialTheme.typography.labelSmall, color: Color = Color.White) {
    val ctx = LocalContext.current
    var hora by remember { mutableStateOf("") }; var bat by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) { val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()); while (isActive) { hora = f.format(java.util.Date()); bat = (ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); delay(30_000) } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(hora, style = estilo, color = color); Spacer(Modifier.width(8.dp))
        Box(Modifier.size(20.dp, 10.dp).border(1.dp, color.copy(.7f), RoundedCornerShape(3.dp)).padding(1.5.dp)) { Box(Modifier.fillMaxHeight().fillMaxWidth(bat / 100f).clip(RoundedCornerShape(2.dp)).background(if (bat <= 15) Color(0xFFE5484D) else color)) }
        Spacer(Modifier.width(4.dp)); Text("$bat%", style = estilo, color = color)
    }
}

/** Pomodoro / Stopwatch / Focus Breath (opensourceui). modo: 0 pomodoro 25 min, 1 cronómetro, 2 respiración. */
@Composable
fun Temporizador(modo: Int, modifier: Modifier = Modifier, color: Color = Acc) {
    var corriendo by remember { mutableStateOf(false) }; var seg by remember(modo) { mutableIntStateOf(if (modo == 0) 25 * 60 else 0) }
    LaunchedEffect(corriendo, modo) { while (corriendo && isActive) { delay(1000); seg = if (modo == 0) (seg - 1).coerceAtLeast(0) else seg + 1; if (modo == 0 && seg == 0) corriendo = false } }
    if (modo == 2) { val k by rememberInfiniteTransition(label = "br").animateFloat(.6f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "k")
        Box(modifier.size(120.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(120.dp).scale(k).clip(CircleShape).background(color.copy(.25f))); Text(if (k > .8f) "Inhala" else "Exhala") }; return }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) { if (modo == 0) AnilloProgreso(seg / 1500f, Modifier.size(110.dp), color, conTexto = false)
            Row { NumeroFlow(seg / 60, MaterialTheme.typography.headlineSmall); Text(":", style = MaterialTheme.typography.headlineSmall); NumeroFlow(seg % 60 / 10, MaterialTheme.typography.headlineSmall); NumeroFlow(seg % 10, MaterialTheme.typography.headlineSmall) } }
        Row { TextButton({ corriendo = !corriendo }) { TextoMorph(if (corriendo) "Pausa" else "Empezar", MaterialTheme.typography.labelLarge) }; TextButton({ corriendo = false; seg = if (modo == 0) 1500 else 0 }) { Text("Reiniciar") } }
    }
}

/** Audio Recorder / Recorder Face / Voice Assistant (opensourceui): onda de audio animada. */
@Composable
fun OndaAudio(activo: Boolean, modifier: Modifier = Modifier.height(40.dp).width(160.dp), color: Color = Color(0xFFE5484D), nivel: Float = .6f) {
    val t by rememberInfiniteTransition(label = "au").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "au")
    Canvas(modifier) { val n = 28; val w = size.width / n; for (i in 0 until n) { val h = if (activo) (abs(sin((i * .55f + t * 6.28f))) * nivel + .1f) * size.height else size.height * .08f; drawRoundRect(color, Offset(i * w + w * .2f, (size.height - h) / 2), Size(w * .6f, h), CornerRadius(w)) } }
}

/* ───────────────────────── Bloques (blocks.so) y componentes (coss / opensourceui) ───────────────────────── */

/** Empty (coss): estado vacío. */
@Composable
fun EstadoVacio(icono: String, titulo: String, detalle: String, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x1FFFFFFF)).aparecerDifuso(), contentAlignment = Alignment.Center) { Text(icono, fontSize = 32.sp) }
        Spacer(Modifier.height(14.dp)); Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.aparecerDifuso(1)); Text(detalle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.aparecerDifuso(2).alpha(.7f))
        accion?.let { Spacer(Modifier.height(14.dp)); it() }
    }
}

/** Stats 01–15 (blocks.so): tarjeta de estadística. variante 0..14 cambia la composición. */
@Composable
fun TarjetaEstadistica(titulo: String, valor: Int, cambio: Float, variante: Int, modifier: Modifier = Modifier, serie: List<Float> = listOf(3f, 5f, 4f, 7f, 6f, 9f, 8f), color: Color = Acc) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x14FFFFFF)).then(if (variante % 5 == 4) Modifier.bordeBrillante() else Modifier).padding(16.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, modifier = Modifier.alpha(.7f))
        Row(verticalAlignment = Alignment.Bottom) { NumeroFlow(valor, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)); Spacer(Modifier.width(8.dp))
            if (variante % 3 != 2) Text((if (cambio >= 0) "▲ " else "▼ ") + String.format(java.util.Locale.US, "%.1f%%", abs(cambio)), color = if (cambio >= 0) Color(0xFF3DD68C) else Color(0xFFE5484D), style = MaterialTheme.typography.labelSmall) }
        when (variante % 4) {
            0 -> Canvas(Modifier.fillMaxWidth().height(36.dp).padding(top = 8.dp)) { val mx = serie.max(); val p = Path(); serie.forEachIndexed { i, v -> val x = size.width * i / (serie.size - 1); val y = size.height * (1 - v / mx); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }; drawPath(p, color, style = Stroke(2.5f, cap = StrokeCap.Round)) }
            1 -> Row(Modifier.fillMaxWidth().height(36.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) { serie.forEach { v -> Box(Modifier.weight(1f).fillMaxHeight(v / serie.max()).clip(RoundedCornerShape(3.dp)).background(color.copy(.7f))) } }
            2 -> LinearProgressIndicator({ (valor % 100) / 100f }, Modifier.fillMaxWidth().padding(top = 10.dp), color = color)
            else -> Unit
        }
    }
}

/** Dialogs 01–12 (blocks.so) + Alert Dialog (coss): diálogo con cabecera de ícono y estilos. */
@Composable
fun DialogoFx(visible: Boolean, titulo: String, texto: String, variante: Int, onCerrar: () -> Unit, confirmar: String = "Aceptar", peligro: Boolean = false, onConfirmar: () -> Unit = onCerrar) {
    if (!visible) return
    androidx.compose.ui.window.Dialog(onCerrar) {
        val col = if (peligro) Color(0xFFE5484D) else Acc
        Column(Modifier.aparecerDifuso().clip(RoundedCornerShape(if (variante % 2 == 0) 28.dp else 18.dp)).background(Color(0xF21C1C22)).then(if (variante % 4 == 3) Modifier.hazBorde(28.dp, col) else Modifier).then(if (variante >= 6) Modifier.noise(.05f) else Modifier).padding(24.dp),
            horizontalAlignment = if (variante % 3 == 0) Alignment.CenterHorizontally else Alignment.Start) {
            if (variante % 3 != 2) Box(Modifier.size(48.dp).clip(CircleShape).background(col.copy(.18f)), contentAlignment = Alignment.Center) { Text(if (peligro) "!" else "i", color = col, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Spacer(Modifier.height(12.dp)); Text(titulo, style = MaterialTheme.typography.titleLarge, color = Color.White); Spacer(Modifier.height(6.dp)); Text(texto, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB5B5BD))
            Spacer(Modifier.height(20.dp))
            Row(if (variante % 3 == 0) Modifier.fillMaxWidth() else Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onCerrar, if (variante % 3 == 0) Modifier.weight(1f) else Modifier) { Text("Cancelar") }
                Button(onConfirmar, if (variante % 3 == 0) Modifier.weight(1f) else Modifier, colors = ButtonDefaults.buttonColors(containerColor = col)) { Text(confirmar) }
            }
        }
    }
}

/** Onboarding 01–07 (blocks.so) / Transition Panel: pasos con indicador y animación entre paneles. */
@Composable
fun Bienvenida(pasos: List<Triple<String, String, String>>, onTerminar: () -> Unit, modifier: Modifier = Modifier, variante: Int = 0, color: Color = Acc) {
    var i by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize().then(if (variante % 2 == 1) Modifier.noise(.04f) else Modifier).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AnimatedContent(i, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) }, label = "ob") { k ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(pasos[k].first, fontSize = 56.sp); Spacer(Modifier.height(18.dp)); TextoEntrada(pasos[k].second, MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text(pasos[k].third, textAlign = TextAlign.Center, modifier = Modifier.alpha(.75f)) }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pasos.indices.forEach { k -> val w by animateDpAsState(if (k == i) 22.dp else 7.dp, label = "w$k"); Box(Modifier.size(w, 7.dp).clip(Pildora).background(if (k == i) color else Color(0x44FFFFFF))) } }
        Spacer(Modifier.height(24.dp))
        BotonMagico(if (i == pasos.lastIndex) "Empezar" else "Siguiente", estilo = variante % 4) { if (i == pasos.lastIndex) onTerminar() else i++ }
    }
}

/** Command Menu 01–04 (blocks.so) / Command (coss) / Spotlight Bar (opensourceui): buscador de comandos. */
@Composable
fun MenuComandos(visible: Boolean, comandos: List<Pair<String, () -> Unit>>, onCerrar: () -> Unit, placeholder: String = "Buscar un comando…") {
    if (!visible) return
    var q by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onCerrar) {
        Column(Modifier.aparecerDifuso().fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xF51C1C22)).padding(12.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text(placeholder) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
            comandos.filter { it.first.contains(q, true) }.take(8).forEachIndexed { i, (t, a) -> Row(Modifier.aparecerDifuso(i, 25).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { a(); onCerrar() }.padding(12.dp)) { Text(t, Modifier.weight(1f), color = Color.White); Text("↵", color = Color.Gray) } }
        }
    }
}

/** Sidebar 01–06 (blocks.so): panel lateral deslizable. */
@Composable
fun PanelLateral(abierto: Boolean, onCerrar: () -> Unit, modifier: Modifier = Modifier, ancho: Dp = 300.dp, content: @Composable ColumnScope.() -> Unit) {
    val a by animateFloatAsState(if (abierto) 1f else 0f, spring(dampingRatio = .85f, stiffness = 380f), label = "pl")
    if (a > 0.01f) Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.45f * a)).clickable(remember { MutableInteractionSource() }, null, onClick = onCerrar))
        Column(Modifier.fillMaxHeight().width(ancho).graphicsLayer { translationX = -(1 - a) * size.width }.clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(Color(0xF51C1C22)).noise(.04f).padding(20.dp), content = content)
    }
}

/** Grid List 01–03 (blocks.so) + Gallery Grid (opensourceui): rejilla con entrada escalonada. */
@Composable
fun RejillaEscalonada(n: Int, columnas: Int, modifier: Modifier = Modifier, item: @Composable (Int) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { (0 until n).chunked(columnas).forEach { fila -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { fila.forEach { i -> Box(Modifier.weight(1f).aparecerDifuso(i)) { item(i) } }; repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) } } } }
}

/** Bento Grid (Magic UI): rejilla de tarjetas de distintos tamaños. */
@Composable
fun Bento(modifier: Modifier = Modifier, celdas: List<@Composable () -> Unit>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { celdas.getOrNull(0)?.let { Box(Modifier.weight(2f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0x14FFFFFF)).foco()) { it() } }; celdas.getOrNull(1)?.let { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0x14FFFFFF)).foco()) { it() } } }
        Row(Modifier.height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { celdas.drop(2).forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0x14FFFFFF)).foco()) { c() } } }
    }
}

/** Avatar Circles (Magic UI) / Presence Dock (opensourceui): avatares superpuestos. */
@Composable
fun AvataresSuperpuestos(iniciales: List<String>, modifier: Modifier = Modifier, extra: Int = 0) {
    val cs = listOf(Color(0xFF7C9CFF), Color(0xFFFF8FAB), Color(0xFF3DD68C), Color(0xFFFFD166), Color(0xFFB388FF))
    Row(modifier) { iniciales.forEachIndexed { i, s -> Box(Modifier.offset(x = (-10 * i).dp).size(38.dp).border(2.dp, Color(0xFF111114), CircleShape).clip(CircleShape).background(cs[i % cs.size]), contentAlignment = Alignment.Center) { Text(s, color = Color.Black, fontWeight = FontWeight.Bold) } }
        if (extra > 0) Box(Modifier.offset(x = (-10 * iniciales.size).dp).size(38.dp).clip(CircleShape).background(Color(0xFF2A2A30)), contentAlignment = Alignment.Center) { Text("+$extra", style = MaterialTheme.typography.labelSmall) } }
}

/** File Tree (Magic UI): árbol de carpetas plegable. */
data class Nodo(val nombre: String, val hijos: List<Nodo> = emptyList())
@Composable
fun ArbolArchivos(raiz: List<Nodo>, modifier: Modifier = Modifier, onArchivo: (Nodo) -> Unit = {}) {
    Column(modifier) { raiz.forEach { NodoFila(it, 0, onArchivo) } }
}
@Composable private fun NodoFila(n: Nodo, nivel: Int, onArchivo: (Nodo) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { if (n.hijos.isEmpty()) onArchivo(n) else abierto = !abierto }.padding(start = (14 * nivel).dp, top = 6.dp, bottom = 6.dp)) { Text(if (n.hijos.isEmpty()) "📄 " else if (abierto) "📂 " else "📁 "); Text(n.nombre) }
    AnimatedVisibility(abierto) { Column { n.hijos.forEach { NodoFila(it, nivel + 1, onArchivo) } } }
}

/** Stacked Folder Card / Folder Tab Card (opensourceui): carpeta con pestaña. */
@Composable
fun TarjetaCarpeta(titulo: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(120.dp)) {
        Box(Modifier.padding(top = 4.dp).fillMaxWidth(.45f).height(24.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(color.copy(.7f)))
        Box(Modifier.offset(y = 6.dp).padding(horizontal = 10.dp).fillMaxWidth().height(90.dp).graphicsLayer { rotationZ = -2f }.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.85f)))
        Column(Modifier.padding(top = 20.dp).fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(color).padding(14.dp), verticalArrangement = Arrangement.Bottom) { Text(titulo, color = Color.White, fontWeight = FontWeight.SemiBold); Text(sub, color = Color.White.copy(.8f), style = MaterialTheme.typography.labelSmall) }
    }
}

/** Sticky Note Polaroid / Hard Shadow Polaroid / Polaroid Image (opensourceui). */
@Composable
fun MarcoPolaroid(pie: String, modifier: Modifier = Modifier, giro: Float = -3f, nota: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Column(modifier.graphicsLayer { rotationZ = giro }.shadow(if (nota) 2.dp else 8.dp, RoundedCornerShape(4.dp)).background(if (nota) Color(0xFFFFF59D) else Color.White).padding(10.dp).padding(bottom = 22.dp)) {
        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(Color(0xFFDDDDDD)), content = content); Spacer(Modifier.height(8.dp)); Text(pie, color = Color.DarkGray, style = MaterialTheme.typography.labelMedium)
    }
}

/** Transform Box Frame / Transform Plus Frame (opensourceui): caja de selección con tiradores. */
fun Modifier.marcoTransformar(color: Color = Acc, mas: Boolean = false): Modifier = drawWithContent {
    drawContent(); val h = 5.dp.toPx(); drawRect(color, style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
    listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), Offset(0f, size.height / 2), Offset(size.width, size.height / 2)).forEach {
        if (mas) { drawLine(color, it - Offset(h, 0f), it + Offset(h, 0f), 2f); drawLine(color, it - Offset(0f, h), it + Offset(0f, h), 2f) } else { drawRect(Color.White, it - Offset(h, h), Size(2 * h, 2 * h)); drawRect(color, it - Offset(h, h), Size(2 * h, 2 * h), style = Stroke(1.5f)) } }
    drawCircle(color, h, Offset(size.width / 2, -6 * h)); drawLine(color, Offset(size.width / 2, -5 * h), Offset(size.width / 2, 0f), 1.2f)
}

/** Notificaciones (opensourceui) / Toast (coss): aviso que baja desde arriba y se va solo. */
class AvisoFxEstado { var texto by mutableStateOf<String?>(null); var icono by mutableStateOf("🔔"); fun mostrar(t: String, i: String = "🔔") { icono = i; texto = t } }
@Composable fun rememberAvisoFx() = remember { AvisoFxEstado() }
@Composable
fun AvisoFx(estado: AvisoFxEstado, modifier: Modifier = Modifier) {
    LaunchedEffect(estado.texto) { if (estado.texto != null) { delay(2800); estado.texto = null } }
    AnimatedVisibility(estado.texto != null, modifier, enter = slideInVertically(spring(dampingRatio = .7f)) { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Row(Modifier.padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF22A2A30)).noise(.04f).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(estado.icono, fontSize = 22.sp); Spacer(Modifier.width(10.dp)); Text(estado.texto ?: "", color = Color.White) }
    }
}

/** Code / Login / Form Layout / OTP (blocks.so + coss + opensourceui): campo OTP de cajas o subrayado. */
@Composable
fun CampoOTP(valor: String, onCambio: (String) -> Unit, modifier: Modifier = Modifier, n: Int = 6, subrayado: Boolean = false, color: Color = Acc) {
    androidx.compose.foundation.text.BasicTextField(valor, { if (it.length <= n && it.all(Char::isDigit)) onCambio(it) }, modifier, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(n) { i -> val c = valor.getOrNull(i); val foco = i == valor.length
            Box(Modifier.size(44.dp, 52.dp).then(if (subrayado) Modifier.drawBehind { drawLine(if (foco) color else Color.Gray, Offset(0f, size.height), Offset(size.width, size.height), 3f) } else Modifier.border(if (foco) 2.dp else 1.dp, if (foco) color else Color(0x55FFFFFF), RoundedCornerShape(12.dp))), contentAlignment = Alignment.Center) {
                AnimatedContent(c, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "o$i") { Text(it?.toString() ?: "", style = MaterialTheme.typography.titleLarge) } } } }
    }
}

/** Device Mocks (Magic UI Safari/iPhone/Android, opensourceui Apple mockups): marco de dispositivo. tipo: 0 teléfono, 1 navegador, 2 tableta. */
@Composable
fun MarcoDispositivo(tipo: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    when (tipo) {
        1 -> Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2A30))) { Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach { Box(Modifier.size(9.dp).clip(CircleShape).background(it)) }; Box(Modifier.padding(start = 10.dp).weight(1f).height(18.dp).clip(Pildora).background(Color(0xFF1C1C22))) }; Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(Color.Black), content = content) }
        else -> Box(modifier.aspectRatio(if (tipo == 0) .5f else .75f).clip(RoundedCornerShape(if (tipo == 0) 34.dp else 22.dp)).background(Color(0xFF111114)).padding(6.dp)) { Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (tipo == 0) 28.dp else 16.dp)).background(Color.Black), content = content); if (tipo == 0) Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(60.dp, 16.dp).clip(Pildora).background(Color.Black)) }
    }
}

/** Animated Beam (Magic UI): haz animado entre dos puntos (p. ej. teléfono ↔ PC al sincronizar). */
@Composable
fun HazEntre(modifier: Modifier = Modifier, color: Color = Acc) {
    val t by rememberInfiniteTransition(label = "hb").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "hb")
    Canvas(modifier) { val a = Offset(20f, size.height / 2); val b = Offset(size.width - 20f, size.height / 2); val c = Offset(size.width / 2, 0f)
        val p = Path().apply { moveTo(a.x, a.y); quadraticTo(c.x, c.y, b.x, b.y) }; drawPath(p, color.copy(.2f), style = Stroke(3f))
        val m = PathMeasure(); m.setPath(p, false); val d = Path(); m.getSegment(m.length * (t - .2f).coerceAtLeast(0f), m.length * t, d); drawPath(d, Brush.linearGradient(listOf(Color.Transparent, color, Color.White)), style = Stroke(3f, cap = StrokeCap.Round))
        drawCircle(color, 10f, a); drawCircle(color, 10f, b) }
}

/** Morphing Dialog (Motion Primitives) / Hero Video Dialog: una tarjeta que crece hasta ocupar la pantalla. */
@Composable
fun TarjetaMorph(modifier: Modifier = Modifier, cerrada: @Composable BoxScope.() -> Unit, abierta: @Composable BoxScope.(cerrar: () -> Unit) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(Modifier.clip(RoundedCornerShape(20.dp)).clickable { abierto = true }, content = cerrada)
        if (abierto) androidx.compose.ui.window.Dialog({ abierto = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            val a = remember { Animatable(0f) }; LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = .82f, stiffness = 320f)) }
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = .6f + .4f * a.value; scaleY = .6f + .4f * a.value; alpha = a.value; shape = RoundedCornerShape((40 * (1 - a.value)).dp); clip = true }.background(Color(0xFF111114))) { abierta { abierto = false } }
        }
    }
}

/** Morphing Popover (Motion Primitives) / Preview Card (coss): botón que se transforma en un panel. */
@Composable
fun PopoverMorph(etiqueta: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.(cerrar: () -> Unit) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier.animateContentSize(spring(dampingRatio = .75f, stiffness = 400f)).clip(RoundedCornerShape(if (abierto) 22.dp else 50.dp)).background(Color(0xFF26262C))) {
        if (!abierto) Text(etiqueta, Modifier.clickable { abierto = true }.padding(horizontal = 18.dp, vertical = 10.dp))
        else Column(Modifier.padding(14.dp).widthIn(max = 280.dp)) { content { abierto = false } }
    }
}

/** Image Comparison (Motion Primitives): deslizador antes/después. */
@Composable
fun Comparador(modifier: Modifier = Modifier, antes: @Composable () -> Unit, despues: @Composable () -> Unit) {
    var f by remember { mutableFloatStateOf(.5f) }
    Box(modifier.pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> f = (c.position.x / size.width).coerceIn(0f, 1f) } }) {
        despues(); Box(Modifier.matchParentSize().drawWithContent { clipRect(right = size.width * f) { this@drawWithContent.drawContent() } }) { antes() }
        Box(Modifier.matchParentSize().drawBehind { drawLine(Color.White, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 3f); drawCircle(Color.White, 14f, Offset(size.width * f, size.height / 2)) })
    }
}

/** Orbiting / Cool Mode (Magic UI): partículas que salen del botón al tocar. */
fun Modifier.modoGenial(emoji: String = "✨"): Modifier = composed {
    val ps = remember { mutableStateListOf<Triple<Offset, Offset, Long>>() }; var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ps.size) { while (ps.isNotEmpty()) { withFrameMillis { now = it }; ps.removeAll { now - it.third > 900 } } }
    val med = rememberTextMeasurer()
    pointerInput(Unit) { detectTapGestures { p -> val t = System.nanoTime() / 1_000_000; repeat(8) { ps.add(Triple(p, Offset(Random.nextFloat() * 2 - 1, -Random.nextFloat() - .3f), t)) } } }
        .drawWithContent { drawContent(); ps.forEach { (o, v, t0) -> val k = ((now - t0) / 900f).coerceIn(0f, 1f); drawText(med.measure(emoji), topLeft = Offset(o.x + v.x * 120 * k, o.y + v.y * 160 * k + 200 * k * k), alpha = 1 - k) } }
}

/** Smooth Cursor / Pointer (Magic UI): punto suave que sigue al lápiz o al dedo. */
fun Modifier.cursorSuave(color: Color = Acc): Modifier = composed {
    var p by remember { mutableStateOf<Offset?>(null) }; val ap by animateOffsetAsState(p ?: Offset.Zero, spring(stiffness = 900f, dampingRatio = .7f), label = "cs")
    pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); p = e.changes.firstOrNull()?.position } } }
        .drawWithContent { drawContent(); if (p != null) { drawCircle(color.copy(.25f), 18f, ap); drawCircle(color, 5f, ap) } }
}

/** Glass Overlay Image (opensourceui): imagen con un panel de vidrio encima. */
@Composable
fun VidrioSobreImagen(modifier: Modifier = Modifier, imagen: @Composable BoxScope.() -> Unit, panel: @Composable ColumnScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(24.dp))) { imagen(); Column(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.14f)).border(1.dp, Color.White.copy(.25f), RoundedCornerShape(18.dp)).noise(.05f).padding(12.dp), content = panel) }
}

/** Tablas 01–05 (blocks.so) / Table (coss) / Tasks, Customers, Users Select Table (opensourceui). */
@Composable
fun TablaFx(cabecera: List<String>, filas: List<List<String>>, modifier: Modifier = Modifier, seleccionable: Boolean = false, tarjeta: Boolean = true) {
    val sel = remember { mutableStateListOf<Int>() }
    Column(modifier.then(if (tarjeta) Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x10FFFFFF)) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) { if (seleccionable) Spacer(Modifier.width(36.dp)); cabecera.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.Gray) } }
        filas.forEachIndexed { i, f -> HorizontalDivider(color = Color(0x14FFFFFF)); Row(Modifier.fillMaxWidth().background(if (i in sel) Acc.copy(.12f) else Color.Transparent).clickable { if (seleccionable) { if (i in sel) sel.remove(i) else sel.add(i) } }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (seleccionable) Checkbox(i in sel, { if (it) sel.add(i) else sel.remove(i) }, Modifier.size(36.dp)); f.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) } } }
    }
}

/** Fila con insignia (coss Badge) para "Nuevo" con brillo. */
@Composable fun InsigniaBrillo(texto: String, color: Color = Acc) = Box(Modifier.clip(Pildora).background(color.copy(.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) { TextoBrillo(texto, MaterialTheme.typography.labelSmall, base = color, brillo = Color.White) }
