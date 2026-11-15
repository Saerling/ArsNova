package com.arsnova

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.arsnova.data.Page
import com.arsnova.data.Repo
import com.arsnova.data.Stroke
import com.arsnova.data.Template
import com.arsnova.ink.Ink
import com.arsnova.ink.Shapes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.*

/** Guardado sin pérdidas, notas antiguas, reconocimiento de formas y capturas de lápices y formas (build/ink). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EstabilidadTest {
    private fun repo() = Repo(RuntimeEnvironment.getApplication()).also { r -> r.dir.listFiles()?.forEach { it.delete() } }

    /** Un cuaderno guardado por versiones anteriores (sin los campos nuevos fill/tilt) se abre igual. */
    @Test fun abreNotasAntiguas() {
        val r = repo(); val id = "antiguo"
        // puntos (10,20,.5) (30,40,.6) en float32 little-endian, base64
        val bb = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN); floatArrayOf(10f, 20f, .5f, 30f, 40f, .6f).forEach { bb.putFloat(it) }
        val pts = android.util.Base64.encodeToString(bb.array(), android.util.Base64.NO_WRAP)
        File(r.dir, "$id.json").writeText("""{"id":"$id","title":"Viejo","cover":0,"template":"LINED","updated":1,"pages":[{"id":5,"template":"LINED","strokes":[{"id":1,"color":-16777216,"width":4.0,"pts":"$pts","style":8},{"id":2,"color":-16777216,"width":4.0,"pts":"$pts","style":10}]}]}""")
        val nb = r.load(id)
        assertEquals(2, nb.pages[0].strokes.size); assertEquals(0, nb.pages[0].strokes[0].fill); assertEquals(0f, nb.pages[0].strokes[0].tilt)
        // y los lápices retirados (neón, puntillismo) se siguen dibujando sin fallar
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888); nb.pages[0].strokes.forEach { Ink.drawStroke(Canvas(b), it) }
    }

    /** El guardado deja respaldo, sobrevive a un principal dañado y un índice perdido se reconstruye. */
    @Test fun guardadoAtomicoYRespaldo() {
        val r = repo(); val id = r.create("Cálculo", 0, Template.GRID)
        val nb = r.load(id); nb.pages[0].strokes += Stroke(9, Color.BLACK, 4f, pts = floatArrayOf(1f, 2f, .5f, 3f, 4f, .5f), fill = 0x40FF0000, tilt = .5f)
        r.saveAsync(nb, null); r.flush()
        assertTrue(File(r.dir, "$id.bak").exists()); assertFalse(File(r.dir, "$id.tmp").exists())
        val back = r.load(id).pages[0].strokes[0]; assertEquals(0x40FF0000, back.fill); assertEquals(.5f, back.tilt, 1e-6f)
        // un apagón a mitad de escritura deja el principal cortado: se abre el respaldo
        File(r.dir, "$id.json").writeText("{\"id\":\"$id\",\"title\":\"Cál")
        assertEquals("Cálculo", r.load(id).title)
        // y el siguiente guardado no pisa el respaldo bueno con el archivo roto
        r.saveAsync(r.load(id), null); r.flush(); File(r.dir, "$id.json").delete(); assertEquals("Cálculo", r.load(id).title)
        // índice borrado: la biblioteca no se queda vacía
        File(r.dir, "index.json").delete(); assertEquals(listOf(id), r.list().map { it.id })
    }

    private fun ring(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 80) = FloatArray(3 * (n + 1)) { k -> val i = k / 3; val a = 0.5 + 2 * PI * i / n
        when (k % 3) { 0 -> cx + rx * cos(a).toFloat(); 1 -> cy + ry * sin(a).toFloat(); else -> .5f } }
    private fun poly(vararg v: Float): FloatArray { val o = ArrayList<Float>()
        for (i in 0 until v.size / 2 - 1) { val ax = v[2 * i]; val ay = v[2 * i + 1]; val bx = v[2 * i + 2]; val by = v[2 * i + 3]; val n = max(1, (hypot(bx - ax, by - ay) / 5).toInt())
            for (s in 0 until n) { val t = s.toFloat() / n; o += ax + (bx - ax) * t; o += ay + (by - ay) * t; o += .5f } }
        o += v[v.size - 2]; o += v[v.size - 1]; o += .5f; return o.toFloatArray() }

    @Test fun reconoceFormas() {
        fun k(p: FloatArray) = Shapes.fit(p, p.size)?.kind
        assertEquals(Shapes.ELLIPSE, k(ring(300f, 300f, 120f, 120f)))
        assertEquals(Shapes.ELLIPSE, k(ring(300f, 300f, 180f, 70f)))
        assertEquals(Shapes.RECT, k(poly(100f, 100f, 400f, 100f, 400f, 260f, 100f, 260f, 102f, 104f)))
        assertEquals(Shapes.TRIANGLE, k(poly(250f, 80f, 420f, 360f, 80f, 360f, 248f, 84f)))
        assertEquals(Shapes.POLYGON, k(Shapes.regular(5, 100f, 100f, 400f, 400f).let { a -> FloatArray(a.size / 2 * 3) { if (it % 3 == 2) .5f else a[it / 3 * 2 + it % 3] } }))
        assertNull("recta: la sigue el lienzo", k(poly(100f, 100f, 420f, 180f)))
        assertEquals(Shapes.ARROW, k(poly(100f, 300f, 400f, 300f, 360f, 270f, 400f, 300f, 360f, 330f)))
        assertEquals(Shapes.POLYLINE, k(poly(100f, 100f, 100f, 300f, 300f, 300f)))
        val e = Shapes.snapAngle(0f, 0f, 100f, 3f); assertEquals("regla a 0°", 0f, e[1], 0.01f)
    }

    /** Capturas: lápices actuales (con lápiz inclinado) y formas con y sin relleno. */
    @Test fun capturas() {
        val out = File("build/ink").apply { mkdirs() }
        val b = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(Color.WHITE); Ink.darkPaper = false
        listOf(0, 1, 2, 12, 5, 3, 7, 4, 13, 9).forEachIndexed { row, st ->
            val pts = FloatArray(3 * 120) { k -> val i = k / 3; val t = i / 119f; when (k % 3) { 0 -> 40 + t * 600; 1 -> 50f + row * 80 + 24 * sin(t * 6 * PI.toFloat()); else -> (0.3f + 0.6f * sin(t * PI.toFloat())) } }
            Ink.drawStroke(c, Stroke(row.toLong(), 0xFF1D1B18.toInt(), if (st == 3) 12f else 6f, pts = pts, style = st))
            if (st == 2 || st == 12) Ink.drawStroke(c, Stroke(100L + row, 0xFF1D1B18.toInt(), 6f, pts = pts.copyOf().also { a -> var i = 0; while (i < a.size) { a[i] += 680f; i += 3 } }, style = st, tilt = 0.8f))
        }
        val shapes = listOf(ring(1000f, 120f, 80f, 60f), poly(1100f, 220f, 1300f, 220f, 1300f, 340f, 1100f, 340f, 1100f, 220f), Shapes.regular(6, 900f, 380f, 1060f, 540f).let { a -> FloatArray(a.size / 2 * 3) { if (it % 3 == 2) .5f else a[it / 3 * 2 + it % 3] } },
            Shapes.star(1120f, 380f, 1300f, 560f).let { a -> FloatArray(a.size / 2 * 3) { if (it % 3 == 2) .5f else a[it / 3 * 2 + it % 3] } })
        shapes.forEachIndexed { i, p -> Ink.drawStroke(c, Stroke(200L + i, 0xFF2D5BD9.toInt(), 4f, pts = p, style = 0, fill = if (i % 2 == 1) 0x402D5BD9 else 0)) }
        File(out, "pens-shapes.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
