package com.arsnova

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.arsnova.data.Stroke
import com.arsnova.ink.Ink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.*

/** Dibuja trazos sintéticos con cada lápiz y los guarda como PNG en build/ink para revisarlos a ojo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkRenderTest {
    private fun gen(n: Int, f: (Float) -> Triple<Float, Float, Float>): FloatArray {
        val a = FloatArray(n * 3)
        for (i in 0 until n) { val (x, y, p) = f(i / (n - 1f)); a[3 * i] = x; a[3 * i + 1] = y
            val land = min(1f, (i + 1) / 4f) * min(1f, (n - i) / 5f); a[3 * i + 2] = (p * land).coerceIn(0.02f, 1f) }
        return a
    }
    private fun samples(ox: Float, oy: Float): List<FloatArray> = listOf(
        // bucles tipo "eeee" con presión que sube al bajar
        gen(220) { t -> val a = t * 8 * PI.toFloat(); Triple(ox + t * 330 + 22 * sin(a), oy + 26 * cos(a), 0.35f + 0.4f * max(0f, -sin(a))) },
        // zigzag en seco
        gen(60) { t -> val k = t * 6; val f = k - floor(k); Triple(ox + 380 + t * 220, oy - 26 + 52 * (if (k.toInt() % 2 == 0) f else 1 - f), 0.5f) },
        // línea recta con presión creciente
        gen(40) { t -> Triple(ox + 640 + t * 200, oy + 20 - t * 40, 0.1f + 0.9f * t) },
        // punto
        floatArrayOf(ox + 880, oy, 0.6f),
        // giro cerrado sobre sí mismo (prueba de huecos)
        gen(90) { t -> val a = t * 5 * PI.toFloat(); Triple(ox + 940 + 14 * cos(a) * (1 - t * 0.7f), oy + 14 * sin(a) * (1 - t * 0.7f), 0.8f) },
        // trazo rápido con pocas muestras (prueba de curva)
        gen(9) { t -> Triple(ox + 1000 + t * 260, oy + 30 * sin(t * 2 * PI.toFloat()), 0.55f) },
    )

    @Test fun pens() {
        val names = listOf("boligrafo", "pluma", "lapiz", "pincel", "monolinea", "rotulador", "resaltador", "caligrafia", "neon", "acuarela")
        val sc = 2.2f; val rowH = 110f
        val b = Bitmap.createBitmap((1300 * sc).toInt(), (rowH * names.size * sc).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.WHITE); c.scale(sc, sc); Ink.darkPaper = false
        names.forEachIndexed { st, _ ->
            val w = if (st == 6) 26f else if (st == 3) 14f else 7f
            samples(20f, rowH * st + rowH / 2).forEachIndexed { i, pts ->
                Ink.drawStroke(c, Stroke(i.toLong(), if (st == 6) 0x66F2B705 else if (i % 2 == 0) 0xFF1D1B18.toInt() else 0xB02D5BD9.toInt(), w, st == 6, pts, style = st)) }
        }
        val out = File("build/ink").apply { mkdirs() }
        File(out, "pens.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun icons() {
        val names = com.arsnova.ui.ArsIcons.defs.keys.toList(); val cell = 96; val cols = 9
        val b = Bitmap.createBitmap(cols * cell, ((names.size + cols - 1) / cols) * cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.WHITE)
        val st = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND; color = Color.BLACK }
        val fl = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        names.forEachIndexed { i, n ->
            c.save(); c.translate((i % cols) * cell + 12f, (i / cols) * cell + 12f); c.scale(3f, 3f)
            for (d in com.arsnova.ui.ArsIcons.defs.getValue(n)) if (d.startsWith("F:")) c.drawPath(androidx.core.graphics.PathParser.createPathFromPathData(d.substring(2)), fl)
                else c.drawPath(androidx.core.graphics.PathParser.createPathFromPathData(d), st)
            c.restore()
        }
        File("build/ink").apply { mkdirs() }.let { File(it, "icons.png").outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 100, o) } }
    }
}
