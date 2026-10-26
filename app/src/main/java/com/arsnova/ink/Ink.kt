package com.arsnova.ink

import android.graphics.*
import com.arsnova.data.Page
import com.arsnova.data.Stroke
import com.arsnova.data.Template
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Renderizado de tinta. Igual que ScripNote (pointsToContourSVG), el bolígrafo se dibuja como un
 * contorno relleno de ancho variable: suavizado + curva de presión + normales por punto.
 */
object Ink {
    const val PAGE_W = 1404f
    const val PAGE_H = 1872f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }
    private val white = Paint().apply { color = Color.WHITE }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    var bgDir: java.io.File? = null
    private val bgCache = android.util.LruCache<String, Bitmap>(3)
    private val dst = RectF()
    private fun bg(name: String): Bitmap? = bgCache.get(name)
        ?: BitmapFactory.decodeFile(java.io.File(bgDir, name).path)?.also { bgCache.put(name, it) }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCD6CB.toInt(); strokeWidth = 1.5f }

    fun radius(width: Float, p: Float) = width * 0.5f * (0.28f + 0.72f * p.coerceIn(0f, 1f).pow(0.75f))

    fun drawStroke(c: Canvas, s: Stroke, faded: Boolean = false) {
        val path = s.path ?: buildPath(s.pts, s.pts.size, s.width, s.hl).also { s.path = it }
        val col = if (faded) (s.color and 0x00FFFFFF) or (((s.color ushr 24) * 0.2f).toInt() shl 24) else s.color
        draw(c, path, s.pts, s.pts.size, s.width, s.hl, col)
    }

    fun drawLive(c: Canvas, pts: FloatArray, n: Int, width: Float, hl: Boolean, color: Int) =
        draw(c, buildPath(pts, n, width, hl), pts, n, width, hl, color)

    private fun draw(c: Canvas, path: Path, pts: FloatArray, n: Int, width: Float, hl: Boolean, color: Int) {
        if (n < 3) return
        if (hl) { hlPaint.color = color; hlPaint.strokeWidth = width; c.drawPath(path, hlPaint); return }
        fill.color = color
        c.drawPath(path, fill)
        c.drawCircle(pts[0], pts[1], radius(width, pts[2]), fill)
        c.drawCircle(pts[n - 3], pts[n - 2], radius(width, pts[n - 1]), fill)
    }

    fun buildPath(pts: FloatArray, n: Int, width: Float, hl: Boolean): Path {
        val path = Path(); val cnt = n / 3
        if (cnt == 0) return path
        if (hl) {
            var px = pts[0]; var py = pts[1]; path.moveTo(px, py)
            if (cnt == 1) path.lineTo(px + 0.1f, py)
            for (i in 1 until cnt) {
                val x = pts[3 * i]; val y = pts[3 * i + 1]
                path.quadTo(px, py, (px + x) / 2, (py + y) / 2); px = x; py = y
            }
            path.lineTo(px, py); return path
        }
        val xs = FloatArray(cnt) { pts[3 * it] }; val ys = FloatArray(cnt) { pts[3 * it + 1] }
        val rs = FloatArray(cnt) { radius(width, pts[3 * it + 2]) }
        smooth(xs); smooth(ys); smooth(rs); smooth(rs)
        if (cnt == 1) { path.addCircle(xs[0], ys[0], rs[0], Path.Direction.CW); return path }
        val lx = FloatArray(cnt); val ly = FloatArray(cnt); val rx = FloatArray(cnt); val ry = FloatArray(cnt)
        for (i in 0 until cnt) {
            val a = max(i - 1, 0); val b = min(i + 1, cnt - 1)
            var dx = xs[b] - xs[a]; var dy = ys[b] - ys[a]
            val len = hypot(dx, dy)
            if (len < 1e-4f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
            lx[i] = xs[i] - dy * rs[i]; ly[i] = ys[i] + dx * rs[i]
            rx[i] = xs[i] + dy * rs[i]; ry[i] = ys[i] - dx * rs[i]
        }
        path.moveTo(lx[0], ly[0])
        for (i in 1 until cnt) path.lineTo(lx[i], ly[i])
        for (i in cnt - 1 downTo 0) path.lineTo(rx[i], ry[i])
        path.close()
        return path
    }

    private fun smooth(a: FloatArray) {
        if (a.size < 3) return
        var prev = a[0]
        for (i in 1 until a.size - 1) { val cur = a[i]; a[i] = (prev + 2 * cur + a[i + 1]) * 0.25f; prev = cur }
    }

    fun drawPaper(c: Canvas, t: Template, bgName: String? = null) {
        c.drawRect(0f, 0f, PAGE_W, PAGE_H, white)
        bgName?.let { bg(it) }?.let { b ->
            val s = minOf(PAGE_W / b.width, PAGE_H / b.height); val w = b.width * s; val h = b.height * s
            dst.set((PAGE_W - w) / 2, (PAGE_H - h) / 2, (PAGE_W + w) / 2, (PAGE_H + h) / 2)
            c.drawBitmap(b, null, dst, bmpPaint)
        }
        when (t) {
            Template.BLANK -> {}
            Template.LINED -> { var y = 160f; while (y < PAGE_H - 60) { c.drawLine(70f, y, PAGE_W - 70, y, rule); y += 56f } }
            Template.GRID -> {
                var x = 0f; while (x <= PAGE_W) { c.drawLine(x, 0f, x, PAGE_H, rule); x += 52f }
                var y = 0f; while (y <= PAGE_H) { c.drawLine(0f, y, PAGE_W, y, rule); y += 52f }
            }
            Template.DOTS -> {
                var y = 52f; while (y < PAGE_H) { var x = 52f; while (x < PAGE_W) { c.drawCircle(x, y, 2.4f, rule); x += 52f }; y += 52f }
            }
        }
    }

    fun drawPage(c: Canvas, p: Page) { drawPaper(c, p.template, p.bg); p.strokes.forEach { drawStroke(c, it) } }

    fun render(p: Page, scale: Float): Bitmap {
        val b = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.scale(scale, scale); drawPage(c, p); return b
    }
}
