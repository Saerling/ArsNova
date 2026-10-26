package com.arsnova.ink

import android.graphics.*
import com.arsnova.data.Page
import com.arsnova.data.Stroke
import com.arsnova.data.Template
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.atan2

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
    private val bgCache = android.util.LruCache<String, Bitmap>(8)
    private val dst = RectF()
    fun bg(name: String): Bitmap? = bgCache.get(name)
        ?: BitmapFactory.decodeFile(java.io.File(bgDir, name).path)?.also { bgCache.put(name, it) }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCD6CB.toInt(); strokeWidth = 1.5f }

    fun radius(width: Float, p: Float) = width * 0.5f * (0.28f + 0.72f * p.coerceIn(0f, 1f).pow(0.75f))

    private fun radius(style: Int, width: Float, p0: Float, dx: Float, dy: Float): Float {
        val p = p0.coerceIn(0f, 1f); val h = width * 0.5f
        return when (style) {
            1 -> h * (0.16f + 0.84f * abs(sin(atan2(dy, dx) - 0.785f))) * (0.55f + 0.45f * p)
            7 -> h * (0.07f + 0.93f * abs(sin(atan2(dy, dx) - 0.52f)))
            2 -> h * (0.45f + 0.55f * p)
            3 -> h * (0.06f + 0.94f * p.pow(1.5f))
            4 -> h
            else -> h * (0.28f + 0.72f * p.pow(0.75f))
        }
    }

    private val grain: BitmapShader by lazy {
        val n = 96; val b = Bitmap.createBitmap(n, n, Bitmap.Config.ALPHA_8); val r = java.util.Random(7)
        val px = ByteArray(n * n) { (150 + r.nextInt(106)).toByte() }; b.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px))
        BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private var hidOf = false
    private val tapeP = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Cinta para ocultar (estudio): opaca cuando oculta, translúcida al tocarla. Patrón: 0 liso, 1 puntos, 2 cuadrícula, 3 líneas. */
    private fun drawTape(c: Canvas, pts: FloatArray, n: Int, width: Float, color: Int, revealed: Boolean) {
        val x0 = pts[0]; val y0 = pts[1]; val x1 = pts[n - 3]; val y1 = pts[n - 2]; val len = hypot(x1 - x0, y1 - y0).coerceAtLeast(width)
        c.save(); c.translate(x0, y0); c.rotate(Math.toDegrees(atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat())
        tapeP.color = if (revealed) (color and 0xFFFFFF) or 0x30000000 else color or 0xFF000000.toInt(); tapeP.style = Paint.Style.FILL
        c.drawRoundRect(0f, -width / 2, len, width / 2, 4f, 4f, tapePattern(c, len, width, revealed))
        c.restore()
    }
    var tapePatternOf = 0
    private fun tapePattern(c: Canvas, len: Float, w: Float, revealed: Boolean): Paint {
        if (!revealed && tapePatternOf > 0) {
            val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; strokeWidth = 2f }
            c.save(); c.clipRect(0f, -w / 2, len, w / 2); c.drawRoundRect(0f, -w / 2, len, w / 2, 4f, 4f, tapeP)
            when (tapePatternOf) { 1 -> { var x = 8f; while (x < len) { var y = -w / 2 + 8f; while (y < w / 2) { c.drawCircle(x, y, 2.5f, p2); y += 14f }; x += 14f } }
                2 -> { var x = 0f; while (x < len) { c.drawLine(x, -w / 2, x, w / 2, p2); x += 14f }; var y = -w / 2; while (y < w / 2) { c.drawLine(0f, y, len, y, p2); y += 14f } }
                else -> { var x = -w; while (x < len) { c.drawLine(x, w / 2, x + w, -w / 2, p2); x += 12f } } }
            c.restore(); return Paint().apply { color = 0 }
        }
        return tapeP
    }
    private val flat = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    fun styleOf(s: Stroke) = if (s.hl) 6 else s.style
    /** El patrón de la cinta se guarda en los bits altos del id (sin cambiar el formato de archivo). */
    private fun tapeStyleOf(s: Stroke) = ((s.id ushr 60) and 3).toInt()

    fun drawStroke(c: Canvas, s: Stroke, faded: Boolean = false) {
        val st = styleOf(s)
        val path = s.path ?: buildPath(s.pts, s.pts.size, s.width, st).also { s.path = it }
        hidOf = s.hid; tapePatternOf = if (st == 11) tapeStyleOf(s) else 0
        val col = if (faded) (s.color and 0x00FFFFFF) or (((s.color ushr 24) * 0.2f).toInt() shl 24) else s.color
        draw(c, path, s.pts, s.pts.size, s.width, st, col)
    }

    fun drawLive(c: Canvas, pts: FloatArray, n: Int, width: Float, style: Int, color: Int, tapePattern: Int = 0) =
        hidOf.let { hidOf = false; tapePatternOf = tapePattern }.let { draw(c, buildPath(pts, n, width, style), pts, n, width, style, color) }

    private fun draw(c: Canvas, path: Path, pts: FloatArray, n: Int, width: Float, style: Int, color: Int) {
        if (n < 3) return
        when (style) {
            6 -> { hlPaint.color = color; hlPaint.strokeWidth = width; c.drawPath(path, hlPaint); return }
            5 -> { flat.color = color; flat.strokeWidth = width; c.drawPath(path, flat); return }
            8 -> { glow.color = color; glow.strokeWidth = width * 1.6f; glow.maskFilter = BlurMaskFilter(width * 0.9f, BlurMaskFilter.Blur.NORMAL); c.drawPath(path, glow)
                glow.maskFilter = null; glow.strokeWidth = width * 0.7f; c.drawPath(path, glow); glow.color = 0xEEFFFFFF.toInt(); glow.strokeWidth = width * 0.28f; c.drawPath(path, glow); return }
            9 -> { val a = (color ushr 24) / 255f; glow.color = (color and 0xFFFFFF) or ((a * 90).toInt() shl 24); glow.strokeWidth = width * 1.3f
                glow.maskFilter = BlurMaskFilter(width * 0.35f, BlurMaskFilter.Blur.NORMAL); c.drawPath(path, glow); glow.strokeWidth = width * 0.8f; c.drawPath(path, glow); glow.maskFilter = null; return }
            10 -> { fill.color = color; fill.shader = null; val rnd = java.util.Random(pts[0].toLong() * 31 + n); var dist = 0f
                for (k in 1 until n / 3) { val x0 = pts[3 * k - 3]; val y0 = pts[3 * k - 2]; val x1 = pts[3 * k]; val y1 = pts[3 * k + 1]; dist += hypot(x1 - x0, y1 - y0)
                    while (dist > width * 0.8f) { dist -= width * 0.8f; c.drawCircle(x1 + (rnd.nextFloat() - .5f) * width * 0.5f, y1 + (rnd.nextFloat() - .5f) * width * 0.5f, width * (0.25f + 0.25f * pts[3 * k + 2]), fill) } }; return }
            11 -> { drawTape(c, pts, n, width, color, hidOf); return }
        }
        fill.color = color; fill.shader = if (style == 2) grain else null
        c.drawPath(path, fill)
        if (style == 1) { c.drawCircle(pts[0], pts[1], radius(1, width, pts[2], 1f, 0f), fill); c.drawCircle(pts[n - 3], pts[n - 2], radius(1, width, pts[n - 1], 1f, 0f), fill) }
        fill.shader = null
    }

    fun buildPath(pts: FloatArray, n: Int, width: Float, style: Int): Path {
        val path = Path(); val cnt = n / 3
        if (cnt == 0) return path
        if (style == 5 || style == 6 || style in 8..11) {
            var px = pts[0]; var py = pts[1]; path.moveTo(px, py)
            if (cnt == 1) path.lineTo(px + 0.1f, py)
            for (i in 1 until cnt) {
                val x = pts[3 * i]; val y = pts[3 * i + 1]
                path.quadTo(px, py, (px + x) / 2, (py + y) / 2); px = x; py = y
            }
            path.lineTo(px, py); return path
        }
        if (style != 1 && style != 7) return Freehand.path(pts, n, when (style) {
            2 -> Freehand.Opt(width, 0.35f, 0.5f, 0.45f)
            3 -> Freehand.Opt(width * 1.2f, 0.82f, 0.62f, 0.5f, taperStart = width * 3f, taperEnd = width * 5f)
            4 -> Freehand.Opt(width, 0f, 0.5f, 0.45f)
            else -> Freehand.Opt(width, 0.5f, 0.5f, 0.45f)
        })
        val xs = FloatArray(cnt) { pts[3 * it] }; val ys = FloatArray(cnt) { pts[3 * it + 1] }
        smooth(xs); smooth(ys)
        val rs = FloatArray(cnt) {
            val a = max(it - 1, 0); val b = min(it + 1, cnt - 1)
            radius(style, width, pts[3 * it + 2], xs[b] - xs[a], ys[b] - ys[a])
        }
        smooth(rs); smooth(rs)
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

    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = 0x66E0533D }
    fun drawPaper(c: Canvas, p: Page) = drawPaper(c, p.template, p.bg, p.paper, p.spacing)
    fun drawPaper(c: Canvas, t: Template, bgName: String? = null, paper: Int = -1, spacing: Float = 0f) {
        white.color = paper; c.drawRect(0f, 0f, PAGE_W, PAGE_H, white)
        val darkP = Color.red(paper) * 0.3f + Color.green(paper) * 0.59f + Color.blue(paper) * 0.11f < 110
        rule.color = if (darkP) 0x40FFFFFF else 0xFFDCD6CB.toInt(); dash.color = rule.color
        bgName?.let { bg(it) }?.let { b ->
            val s = minOf(PAGE_W / b.width, PAGE_H / b.height); val w = b.width * s; val h = b.height * s
            dst.set((PAGE_W - w) / 2, (PAGE_H - h) / 2, (PAGE_W + w) / 2, (PAGE_H + h) / 2)
            c.drawBitmap(b, null, dst, bmpPaint)
        }
        fun sp(d: Float) = if (spacing > 0) spacing else d
        fun lines(st: Float, x0: Float = 70f, y0: Float = 160f, x1: Float = PAGE_W - 70f, y1: Float = PAGE_H - 60f) { var y = y0; while (y < y1) { c.drawLine(x0, y, x1, y, rule); y += st } }
        when (t) {
            Template.BLANK -> {}
            Template.LINED, Template.LINED_N, Template.LINED_W -> lines(sp(when (t) { Template.LINED_N -> 42f; Template.LINED_W -> 74f; else -> 56f }))
            Template.GRID, Template.GRID_S, Template.GRID_L -> {
                val st = sp(when (t) { Template.GRID_S -> 36f; Template.GRID_L -> 78f; else -> 52f })
                var x = 0f; while (x <= PAGE_W) { c.drawLine(x, 0f, x, PAGE_H, rule); x += st }
                var y = 0f; while (y <= PAGE_H) { c.drawLine(0f, y, PAGE_W, y, rule); y += st }
            }
            Template.DOTS -> { val st = sp(52f); var y = st; while (y < PAGE_H) { var x = st; while (x < PAGE_W) { c.drawCircle(x, y, 2.4f, rule); x += st }; y += st } }
            Template.MARGIN -> { lines(sp(56f), 0f, 160f, PAGE_W); c.drawLine(170f, 0f, 170f, PAGE_H, red) }
            Template.CORNELL -> { c.drawLine(70f, 150f, PAGE_W - 70f, 150f, rule); lines(sp(56f), 430f, 210f, PAGE_W - 70f, PAGE_H - 420f)
                c.drawLine(400f, 150f, 400f, PAGE_H - 400f, rule); c.drawLine(70f, PAGE_H - 400f, PAGE_W - 70f, PAGE_H - 400f, rule) }
            Template.TODO -> { val st = sp(70f); var y = 180f; while (y < PAGE_H - 80) { c.drawRoundRect(80f, y - 40f, 112f, y - 8f, 6f, 6f, rule.apply { style = Paint.Style.STROKE }); rule.style = Paint.Style.FILL
                c.drawLine(140f, y, PAGE_W - 70f, y, rule); y += st } }
            Template.WEEKLY -> { val names = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo", "Notas")
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = rule.color or 0xFF000000.toInt(); alpha = 160 }
                val bw = (PAGE_W - 140f) / 2; val bh = (PAGE_H - 200f) / 4
                for (i in 0 until 8) { val x = 70f + (i % 2) * bw; val y = 120f + (i / 2) * bh
                    rule.style = Paint.Style.STROKE; c.drawRoundRect(x + 8, y + 8, x + bw - 8, y + bh - 8, 18f, 18f, rule); rule.style = Paint.Style.FILL
                    c.drawText(names[i], x + 30f, y + 52f, tp) } }
            Template.MUSIC -> { var y = 180f; while (y < PAGE_H - 120) { for (k in 0 until 5) c.drawLine(70f, y + k * 16f, PAGE_W - 70f, y + k * 16f, rule); y += 150f } }
            Template.HANDWRITING -> { val st = sp(36f); var y = 180f; while (y < PAGE_H - 120) { c.drawLine(70f, y, PAGE_W - 70f, y, rule); c.drawLine(70f, y + st, PAGE_W - 70f, y + st, dash)
                c.drawLine(70f, y + 2 * st, PAGE_W - 70f, y + 2 * st, rule); y += 3 * st + 40f } }
        }
    }

    fun drawPage(c: Canvas, p: Page) { drawPaper(c, p); p.items.forEach { drawItem(c, it) }; p.strokes.forEach { drawStroke(c, it) } }

    private val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    fun drawItem(c: Canvas, it: com.arsnova.data.Item) {
        if (it.kind == 1) { it.file?.let { f -> bg(f) }?.let { b -> dst.set(it.x, it.y, it.x + it.w, it.y + it.h); c.drawBitmap(b, null, dst, bmpPaint) }; return }
        tp.textSize = it.size; tp.color = it.color; tp.typeface = if (it.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val al = when (it.align) { 1 -> android.text.Layout.Alignment.ALIGN_CENTER; 2 -> android.text.Layout.Alignment.ALIGN_OPPOSITE; else -> android.text.Layout.Alignment.ALIGN_NORMAL }
        val l = android.text.StaticLayout.Builder.obtain(it.text, 0, it.text.length, tp, it.w.toInt().coerceAtLeast(10)).setAlignment(al).build()
        it.h = l.height.toFloat(); c.save(); c.translate(it.x, it.y); l.draw(c); c.restore()
    }

    fun render(p: Page, scale: Float): Bitmap {
        val b = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.scale(scale, scale); drawPage(c, p); return b
    }
}
