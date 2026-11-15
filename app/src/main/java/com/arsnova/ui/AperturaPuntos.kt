package com.arsnova.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Duración total de la apertura de puntos, en ms. */
const val AperturaMs = 2100

private fun tramo(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun suave(x: Float) = x * x * (3f - 2f * x)
private fun sale(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun entra(x: Float) = x * x * x

/** Las letras de [marca] convertidas en una rejilla de puntos (x, y relativos al centro del texto, en px). */
private class MarcaPuntos(val texto: String, val xs: FloatArray, val ys: FloatArray, val radio: Float, val pincel: Paint, val base: Float, val ancho: Float)

private fun puntosDeMarca(marca: String, tipo: Typeface?, anchoMax: Float, tamPx: Float, pasoPx: Float): MarcaPuntos {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tipo ?: Typeface.DEFAULT_BOLD; textSize = tamPx; letterSpacing = -0.03f; color = android.graphics.Color.WHITE }
    var w = p.measureText(marca)
    if (w > anchoMax) { p.textSize = tamPx * anchoMax / w; w = p.measureText(marca) }
    val fm = p.fontMetrics
    val bw = (w + 4).toInt().coerceAtLeast(8); val bh = (fm.descent - fm.ascent + 4).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).drawText(marca, 2f, 2f - fm.ascent, p)
    val px = IntArray(bw * bh); bmp.getPixels(px, 0, bw, 0, 0, bw, bh); bmp.recycle()
    val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
    val paso = pasoPx.coerceAtLeast(2f)
    var x = paso / 2
    while (x < bw) { // por columnas: los puntos quedan ordenados de izquierda a derecha
        var y = paso / 2
        while (y < bh) { if ((px[y.toInt() * bw + x.toInt()] ushr 24) > 110) { xs += x - bw / 2f; ys += y - bh / 2f }; y += paso }
        x += paso
    }
    return MarcaPuntos(marca, xs.toFloatArray(), ys.toFloatArray(), paso * 0.40f, p, 2f - fm.ascent - bh / 2f, bw.toFloat())
}

/**
 * Apertura de puntos: el icono (hecho de puntos, [puntos] = x, y, radio, símbolo en el lienzo de 108) aparece dentro de
 * una lente, la lente se cierra en un disco con dispersión de color, y al abrirse los puntos viajan hasta escribir
 * [marca]; el nombre se vuelve sólido y la app aparece por debajo. El primer fotograma coincide con la pantalla de
 * inicio del sistema (mismo icono, 288 dp, centrado). Tocar la salta. Solo se repinta el lienzo: no recompone por
 * fotograma. [alRevelar] recibe 0→1 mientras la app de debajo queda a la vista.
 */
@Composable
fun AperturaPuntos(
    puntos: FloatArray,
    marca: String,
    modifier: Modifier = Modifier,
    tipo: Typeface? = null,
    fondo: Color = Color.Black,
    tinta: Color = Color.White,
    alRevelar: (Float) -> Unit = {},
    fijo: Float? = null,   // para capturas de prueba: fotograma quieto (0..1)
    alTerminar: () -> Unit,
) {
    val t = remember { Animatable(fijo ?: 0f) }
    val fin by rememberUpdatedState(alTerminar)
    val revelar by rememberUpdatedState(alRevelar)
    val d = LocalDensity.current
    var salto by remember { mutableStateOf(false) }
    LaunchedEffect(salto) {
        if (fijo != null) return@LaunchedEffect
        if (salto && t.value < 0.82f) t.snapTo(0.82f)   // tocar: directo al final (el nombre ya escrito y la app apareciendo)
        t.animateTo(1f, tween(((1f - t.value) * AperturaMs).toInt(), easing = LinearEasing)) { revelar(suave(tramo(value * AperturaMs, 1720f, 2100f))) }
        fin()
    }
    // Símbolo primero (de izquierda a derecha, como las letras), luego el fondo del orbe.
    val orden = remember(puntos) { (0 until puntos.size / 4).filter { puntos[it * 4 + 3] > 0.5f }.sortedBy { puntos[it * 4] }.toIntArray() }
    val cache = remember(marca, tipo) { arrayOfNulls<MarcaPuntos>(1) }
    Canvas(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { salto = true } }) {
        val ms = t.value * AperturaMs
        val cx = size.width / 2; val cy = size.height / 2
        val lado = with(d) { 288f * density }
        val k = lado / 108f
        val mp = cache[0] ?: puntosDeMarca(marca, tipo, size.width * 0.78f, 50f * d.density * d.fontScale.coerceAtMost(1.2f), 2.6f * d.density).also { cache[0] = it }

        val revelado = suave(tramo(ms, 1720f, 2100f))
        drawRect(fondo.copy(alpha = fondo.alpha * (1f - revelado)))

        val cierra = entra(tramo(ms, 620f, 930f))          // la lente se cierra en un disco
        val abre = sale(tramo(ms, 930f, 1480f))            // el disco se abre y escribe el nombre
        val escala = 1f - 0.78f * cierra
        val croma = (cierra * (1f - tramo(ms, 1050f, 1500f)))  // dispersión de color: sube al cerrarse, baja al asentarse
        val solido = suave(tramo(ms, 1430f, 1700f))
        val aPuntos = (1f - tramo(ms, 1600f, 1720f)) * (1f - revelado)   // los puntos aguantan hasta que el nombre ya es sólido

        // Lente: de círculo a superelipse, con un brillo fino; se apaga al cerrarse.
        val aLente = suave(tramo(ms, 60f, 380f)) * (1f - tramo(ms, 620f, 820f))
        if (aLente > 0.01f) {
            val r = lado * 0.37f * (0.86f + 0.14f * sale(tramo(ms, 0f, 420f)))
            val esquina = r * (1f - 0.42f * suave(tramo(ms, 220f, 560f)))
            val grosor = 1.1f * d.density
            drawRoundRect(tinta.copy(alpha = 0.34f * aLente), Offset(cx - r, cy - r), Size(r * 2, r * 2), CornerRadius(esquina), style = Stroke(grosor))
            drawRoundRect(tinta.copy(alpha = 0.10f * aLente), Offset(cx - r - grosor * 3, cy - r - grosor * 3), Size((r + grosor * 3) * 2, (r + grosor * 3) * 2), CornerRadius(esquina + grosor * 3), style = Stroke(grosor))
        }

        // Fondo del orbe (puntos pequeños): acompaña a la lente y se apaga al cerrarse.
        val aFondo = (1f - tramo(ms, 620f, 900f))
        if (aFondo > 0.01f) {
            var i = 0
            while (i < puntos.size) {
                if (puntos[i + 3] < 0.5f) drawCircle(tinta.copy(alpha = 0.38f * aFondo), puntos[i + 2] * k * escala, Offset(cx + (puntos[i] - 54f) * k * escala, cy + (puntos[i + 1] - 54f) * k * escala))
                i += 4
            }
        }

        // Partículas: salen del símbolo y acaban en las letras. Con dispersión se pintan tres veces (R, G, B) sumadas.
        if (aPuntos > 0.01f) {
            val n = mp.xs.size; val nb = orden.size.coerceAtLeast(1)
            val giro = (1f - abre) * cierra * (PI.toFloat() * 0.6f)
            val cg = cos(giro); val sg = sin(giro)
            fun pasada(color: Color, disp: Float, modo: BlendMode) {
                for (j in 0 until n) {
                    val o = orden[(j.toLong() * nb / n).toInt().coerceAtMost(nb - 1)] * 4
                    // Varias letras comparten un punto del símbolo: se abren un poco en abanico para no salir apiladas.
                    val ab = ((j % 7) - 3) * 0.35f * k * (1f - abre)
                    val sx = (puntos[o] - 54f) * k * escala + ab; val sy = (puntos[o + 1] - 54f) * k * escala + ab * 0.6f
                    val rx = sx * cg - sy * sg; val ry = sx * sg + sy * cg
                    val x = (rx + (mp.xs[j] - rx) * abre) * disp; val y = (ry + (mp.ys[j] - ry) * abre) * disp
                    val r = puntos[o + 2] * k * escala * (1f - abre) + mp.radio * abre
                    drawCircle(color, r, Offset(cx + x, cy + y), blendMode = modo)
                }
            }
            if (croma > 0.03f) {
                val s = 0.085f * croma
                pasada(Color(1f, 0.15f, 0.2f, aPuntos), 1f + s, BlendMode.Plus)
                pasada(Color(0.1f, 1f, 0.35f, aPuntos), 1f, BlendMode.Plus)
                pasada(Color(0.2f, 0.35f, 1f, aPuntos), 1f - s, BlendMode.Plus)
            } else pasada(tinta.copy(alpha = aPuntos), 1f, BlendMode.SrcOver)
        }

        // El nombre, sólido, exactamente sobre sus puntos; al final se encoge un poco y se apaga con el fondo.
        if (solido > 0.01f) drawNombre(mp, cx, cy, tinta, solido * (1f - revelado), 1f - 0.06f * revelado)
    }
}

private fun DrawScope.drawNombre(mp: MarcaPuntos, cx: Float, cy: Float, tinta: Color, alfa: Float, escala: Float) = drawIntoCanvas { c ->
    mp.pincel.color = tinta.copy(alpha = alfa.coerceIn(0f, 1f)).toArgb()
    val n = c.nativeCanvas; n.save(); n.scale(escala, escala, cx, cy)
    n.drawText(mp.texto, cx - mp.ancho / 2f + 2f, cy + mp.base, mp.pincel)
    n.restore()
}
