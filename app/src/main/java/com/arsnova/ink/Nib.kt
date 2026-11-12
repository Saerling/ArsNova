package com.arsnova.ink

import android.graphics.Path
import kotlin.math.*

/**
 * Geometría del trazo (código propio de ArsNova).
 *
 * El trazo se arma como la unión de piezas convexas con la misma orientación: un trapecio por tramo,
 * una cuña en el lado exterior de cada giro y tapas redondas. Al rellenar con la regla "winding" la
 * unión es exacta, así que no salen huecos ni picos aunque el trazo se cruce o gire en seco, y lo que
 * se ve mientras escribes es exactamente lo que queda al levantar el lápiz.
 *
 * Tubería: puntos crudos (x, y, presión) → centro suavizado → radio por presión (con inercia y
 * pendiente limitada) → afinado de puntas → curva Catmull-Rom densificada → piezas.
 */
object Nib {
    /**
     * Carácter de cada lápiz. Diámetro = ancho · scale · (base + gain · presión^gamma).
     * taperIn / taperOut: largo del afinado de entrada y salida, en anchos. tip: fracción del radio en la punta.
     * follow: cuánto sigue el radio a la presión en cada muestra (menos = más tinta "con cuerpo").
     */
    class Pen(val base: Float, val gain: Float, val gamma: Float, val scale: Float = 1f,
        val taperIn: Float = 0f, val taperOut: Float = 0f, val tip: Float = 0.3f, val follow: Float = 0.5f)

    private val ballpoint = Pen(0.70f, 0.50f, 0.9f, taperIn = 0.9f, tip = 0.55f)
    private val fountain = Pen(0.28f, 1.05f, 1.25f, taperIn = 1.6f, taperOut = 2.2f, tip = 0.12f, follow = 0.45f)
    private val pencil = Pen(0.62f, 0.55f, 1f, taperIn = 0.6f, tip = 0.6f)
    private val brush = Pen(0.06f, 0.94f, 1.5f, scale = 1.3f, taperIn = 2.5f, taperOut = 4f, tip = 0.04f, follow = 0.35f)
    private val mono = Pen(1f, 0f, 1f)
    private val nibPen = Pen(0.8f, 0.25f, 1f)

    fun pen(style: Int) = when (style) { 1 -> fountain; 2 -> pencil; 3 -> brush; 4 -> mono; 7 -> nibPen; else -> ballpoint }

    /** Ángulo de la plumilla de caligrafía (−35°: trazos finos al subir, gruesos al bajar). */
    private val NIB_X = cos(Math.toRadians(-35.0)).toFloat()
    private val NIB_Y = sin(Math.toRadians(-35.0)).toFloat()

    private fun ease(t: Float) = t * (2f - t)

    /** Nodos finales del trazo: x, y, radio. */
    fun nodes(pts: FloatArray, n: Int, width: Float, style: Int): FloatArray {
        val pen = pen(style)
        val raw = n / 3
        if (raw == 0) return FloatArray(0)
        // 1) centro: quita duplicados y suaviza con un núcleo 1-2-1 (los extremos no se mueven)
        var xs = FloatArray(raw); var ys = FloatArray(raw); var ps = FloatArray(raw); var m = 0
        for (i in 0 until raw) {
            val x = pts[3 * i]; val y = pts[3 * i + 1]
            if (m > 0 && abs(x - xs[m - 1]) + abs(y - ys[m - 1]) < 0.08f) { ps[m - 1] = max(ps[m - 1], pts[3 * i + 2]); continue }
            xs[m] = x; ys[m] = y; ps[m] = pts[3 * i + 2].coerceIn(0f, 1f); m++
        }
        val half = width * pen.scale * 0.5f
        fun target(p: Float) = half * (pen.base + pen.gain * p.pow(pen.gamma))
        if (m == 1) return floatArrayOf(xs[0], ys[0], max(target(ps[0]), 0.35f))
        if (m > 2) {
            val ox = xs.copyOf(m); val oy = ys.copyOf(m)
            for (i in 1 until m - 1) { xs[i] = (ox[i - 1] + 2 * ox[i] + ox[i + 1]) * 0.25f; ys[i] = (oy[i - 1] + 2 * oy[i] + oy[i + 1]) * 0.25f }
        }
        // 2) radio: sigue a la presión con inercia y no cambia más rápido de lo que avanza el trazo
        val rs = FloatArray(m); val run = FloatArray(m)
        for (i in 1 until m) run[i] = run[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        // el lápiz aterriza con poca presión: el arranque toma el cuerpo de las primeras muestras
        var p0 = ps[0]; var q = 1; while (q < m && run[q] < width * 0.75f) { p0 = max(p0, ps[q]); q++ }
        rs[0] = target(p0)
        for (i in 1 until m) {
            val d = run[i] - run[i - 1]
            val want = rs[i - 1] + (target(ps[i]) - rs[i - 1]) * pen.follow
            rs[i] = want.coerceIn(rs[i - 1] - 0.45f * d, rs[i - 1] + 0.45f * d)
        }
        val total = run[m - 1]
        // 3) puntas: afinado de entrada y salida (nunca más del 40 % del trazo cada una)
        val lin = min(pen.taperIn * width, total * 0.4f); val lout = min(pen.taperOut * width, total * 0.4f)
        for (i in 0 until m) {
            var f = 1f
            if (lin > 0f && run[i] < lin) f = pen.tip + (1f - pen.tip) * ease(run[i] / lin)
            if (lout > 0f && total - run[i] < lout) f = min(f, pen.tip + (1f - pen.tip) * ease((total - run[i]) / lout))
            rs[i] = max(rs[i] * f, 0.18f)
        }
        // 4) curva: Catmull-Rom entre muestras, más fina donde el tramo es largo o el giro es cerrado
        var out = FloatArray(m * 6); var k = 0
        fun put(x: Float, y: Float, r: Float) { if (k + 3 > out.size) out = out.copyOf(out.size * 2); out[k++] = x; out[k++] = y; out[k++] = r }
        put(xs[0], ys[0], rs[0])
        for (i in 0 until m - 1) {
            val a = max(i - 1, 0); val b = min(i + 2, m - 1)
            val x1 = xs[i]; val y1 = ys[i]; val x2 = xs[i + 1]; val y2 = ys[i + 1]
            val d = hypot(x2 - x1, y2 - y1)
            var steps = 1
            if (d > 0.7f) {
                val t1x = x2 - xs[a]; val t1y = y2 - ys[a]; val t2x = xs[b] - x1; val t2y = ys[b] - y1
                val l1 = hypot(t1x, t1y); val l2 = hypot(t2x, t2y)
                val bend = if (l1 < 1e-3f || l2 < 1e-3f) 0f else acos(((t1x * t2x + t1y * t2y) / (l1 * l2)).coerceIn(-1f, 1f))
                steps = ceil(max(d / 2.5f, bend / 0.22f)).toInt().coerceIn(1, 8)
            }
            for (j in 1..steps) {
                val t = j.toFloat() / steps
                if (steps == 1) { put(x2, y2, rs[i + 1]); break }
                val t2 = t * t; val t3 = t2 * t
                val x = 0.5f * (2 * x1 + (x2 - xs[a]) * t + (2 * xs[a] - 5 * x1 + 4 * x2 - xs[b]) * t2 + (3 * x1 - xs[a] - 3 * x2 + xs[b]) * t3)
                val y = 0.5f * (2 * y1 + (y2 - ys[a]) * t + (2 * ys[a] - 5 * y1 + 4 * y2 - ys[b]) * t2 + (3 * y1 - ys[a] - 3 * y2 + ys[b]) * t3)
                put(x, y, rs[i] + (rs[i + 1] - rs[i]) * t)
            }
        }
        return if (k == out.size) out else out.copyOf(k)
    }

    /** Añade un polígono convexo (en [buf], [c] vértices) siempre con la misma orientación. */
    private fun emit(path: Path, buf: FloatArray, c: Int) {
        var area = 0f; var j = c - 1
        for (i in 0 until c) { area += buf[2 * j] * buf[2 * i + 1] - buf[2 * i] * buf[2 * j + 1]; j = i }
        if (abs(area) < 1e-4f) return
        if (area > 0) { path.moveTo(buf[0], buf[1]); for (i in 1 until c) path.lineTo(buf[2 * i], buf[2 * i + 1]) }
        else { path.moveTo(buf[2 * c - 2], buf[2 * c - 1]); for (i in c - 2 downTo 0) path.lineTo(buf[2 * i], buf[2 * i + 1]) }
        path.close()
    }

    private fun disc(path: Path, buf: FloatArray, x: Float, y: Float, r: Float) {
        val c = ceil(8f + r * 1.4f).toInt().coerceIn(8, 60)
        for (i in 0 until c) { val a = 2.0 * PI * i / c; buf[2 * i] = x + r * cos(a).toFloat(); buf[2 * i + 1] = y + r * sin(a).toFloat() }
        emit(path, buf, c)
    }

    /** Contorno relleno del trazo para los lápices de ancho variable (0-4) y la caligrafía (7). */
    fun path(pts: FloatArray, n: Int, width: Float, style: Int): Path {
        val nd = nodes(pts, n, width, style); val cnt = nd.size / 3
        val path = Path(); if (cnt == 0) return path
        val buf = FloatArray(128)
        if (style == 7) return nibPath(path, buf, nd, cnt, width)
        if (cnt == 1) { disc(path, buf, nd[0], nd[1], nd[2]); return path }
        var pux = 0f; var puy = 0f; var have = false
        for (i in 0 until cnt - 1) {
            val ax = nd[3 * i]; val ay = nd[3 * i + 1]; val ar = nd[3 * i + 2]
            val bx = nd[3 * i + 3]; val by = nd[3 * i + 4]; val br = nd[3 * i + 5]
            val d = hypot(bx - ax, by - ay); if (d < 1e-3f) continue
            val ux = (bx - ax) / d; val uy = (by - ay) / d; val nx = -uy; val ny = ux
            if (have) {
                // unión con el tramo anterior: cuña en el lado exterior; si el giro es muy seco, disco completo
                val cross = pux * uy - puy * ux; val dot = pux * ux + puy * uy
                if (dot < 0.3f) disc(path, buf, ax, ay, ar)
                else if (abs(cross) > 1e-3f) {
                    val s = if (cross > 0) -ar else ar
                    val n1x = -puy; val n1y = pux
                    var mx = n1x + nx; var my = n1y + ny; val ml = hypot(mx, my); mx /= ml; my /= ml
                    buf[0] = ax; buf[1] = ay; buf[2] = ax + n1x * s; buf[3] = ay + n1y * s
                    buf[4] = ax + mx * s; buf[5] = ay + my * s; buf[6] = ax + nx * s; buf[7] = ay + ny * s
                    emit(path, buf, 4)
                }
            } else disc(path, buf, ax, ay, ar)
            buf[0] = ax + nx * ar; buf[1] = ay + ny * ar; buf[2] = bx + nx * br; buf[3] = by + ny * br
            buf[4] = bx - nx * br; buf[5] = by - ny * br; buf[6] = ax - nx * ar; buf[7] = ay - ny * ar
            emit(path, buf, 4)
            pux = ux; puy = uy; have = true
        }
        disc(path, buf, nd[3 * cnt - 3], nd[3 * cnt - 2], nd[3 * cnt - 1])
        return path
    }

    /** Plumilla plana de verdad: se barre el filo (más un grosor mínimo perpendicular para los perfiles). */
    private fun nibPath(path: Path, buf: FloatArray, nd: FloatArray, cnt: Int, width: Float): Path {
        val e = max(width * 0.07f, 0.4f)
        if (cnt == 1) { val x = nd[0]; val y = nd[1]; val h = nd[2]
            buf[0] = x - NIB_X * h + NIB_Y * e; buf[1] = y - NIB_Y * h - NIB_X * e; buf[2] = x + NIB_X * h + NIB_Y * e; buf[3] = y + NIB_Y * h - NIB_X * e
            buf[4] = x + NIB_X * h - NIB_Y * e; buf[5] = y + NIB_Y * h + NIB_X * e; buf[6] = x - NIB_X * h - NIB_Y * e; buf[7] = y - NIB_Y * h + NIB_X * e
            emit(path, buf, 4); return path }
        for (i in 0 until cnt - 1) {
            val ax = nd[3 * i]; val ay = nd[3 * i + 1]; val ah = nd[3 * i + 2]
            val bx = nd[3 * i + 3]; val by = nd[3 * i + 4]; val bh = nd[3 * i + 5]
            buf[0] = ax - NIB_X * ah; buf[1] = ay - NIB_Y * ah; buf[2] = ax + NIB_X * ah; buf[3] = ay + NIB_Y * ah
            buf[4] = bx + NIB_X * bh; buf[5] = by + NIB_Y * bh; buf[6] = bx - NIB_X * bh; buf[7] = by - NIB_Y * bh
            emit(path, buf, 4)
            buf[0] = ax + NIB_Y * e; buf[1] = ay - NIB_X * e; buf[2] = ax - NIB_Y * e; buf[3] = ay + NIB_X * e
            buf[4] = bx - NIB_Y * e; buf[5] = by + NIB_X * e; buf[6] = bx + NIB_Y * e; buf[7] = by - NIB_X * e
            emit(path, buf, 4)
        }
        return path
    }
}
