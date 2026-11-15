package com.arsnova.ink

import kotlin.math.*

/**
 * Reconocimiento de figuras al mantener el lápiz quieto al final del trazo (código propio).
 *
 * Trazo cerrado: triángulo, rectángulo/cuadrado, polígono de 5 a 8 lados o círculo/elipse; se decide comparando
 * cuánto se separa el trazo de la elipse inscrita y del polígono de sus esquinas (no solo contando vértices).
 * Trazo abierto: línea, flecha (asta + punta dibujada de un tirón), línea quebrada o curva suave (Bézier cuadrática).
 * Técnicas públicas: simplificación de Ramer-Douglas-Peucker y ajuste por mínimos cuadrados.
 */
object Shapes {
    const val NONE = 0; const val LINE = 1; const val ARROW = 2; const val RECT = 3; const val ELLIPSE = 4
    const val TRIANGLE = 5; const val POLYGON = 6; const val POLYLINE = 7; const val CURVE = 8

    /** [pts]: x, y alternados, ya remuestreados. [closed]: la figura encierra un área (se puede rellenar). */
    class Fit(val kind: Int, val pts: FloatArray, val closed: Boolean)

    /** Compatibilidad: solo figuras cerradas. */
    fun recognize(p: FloatArray, n: Int): FloatArray? = fit(p, n)?.takeIf { it.closed }?.pts

    /** Ajusta el ángulo de una recta a múltiplos de 15° cuando ya está casi ahí (regla). Devuelve el extremo corregido. */
    fun snapAngle(x0: Float, y0: Float, x1: Float, y1: Float, tolDeg: Float = 4f): FloatArray {
        val len = hypot(x1 - x0, y1 - y0); if (len < 1e-3f) return floatArrayOf(x1, y1)
        val a = Math.toDegrees(atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())); val s = Math.round(a / 15.0) * 15.0
        if (abs(a - s) > tolDeg) return floatArrayOf(x1, y1)
        val r = Math.toRadians(s); return floatArrayOf(x0 + len * cos(r).toFloat(), y0 + len * sin(r).toFloat())
    }

    /** null = trazo demasiado corto o recto (el lienzo lo trata como línea); kind NONE = no se parece a nada: se deja como está. */
    fun fit(raw: FloatArray, n: Int): Fit? {
        val cnt = n / 3; if (cnt < 8) return null
        // dos pasadas 1-2-1: el temblor de la mano no debe contar como esquinas
        var p = raw.copyOf(n)
        repeat(2) { val q = p.copyOf(); for (i in 1 until cnt - 1) { q[3 * i] = (p[3 * i - 3] + 2 * p[3 * i] + p[3 * i + 3]) / 4; q[3 * i + 1] = (p[3 * i - 2] + 2 * p[3 * i + 1] + p[3 * i + 4]) / 4 }; p = q }
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until cnt) { val x = p[3 * i]; val y = p[3 * i + 1]; minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
        val diag = hypot(maxX - minX, maxY - minY); if (diag < 40f) return null
        val gap = hypot(p[0] - p[n - 3], p[1] - p[n - 2])
        return if (gap <= 0.25f * diag) closed(p, cnt, minX, minY, maxX, maxY, diag) else open(p, cnt, diag)
    }

    // ---------- cerradas ----------
    private fun closed(p: FloatArray, cnt: Int, minX: Float, minY: Float, maxX: Float, maxY: Float, diag: Float): Fit {
        val xs = FloatArray(cnt + 1) { p[3 * (it % cnt)] }; val ys = FloatArray(cnt + 1) { p[3 * (it % cnt) + 1] }
        val keep = BooleanArray(cnt + 1); keep[0] = true; keep[cnt] = true
        rdp(xs, ys, 0, cnt, 0.07f * diag, keep)
        var vx = ArrayList<Float>(); var vy = ArrayList<Float>()
        for (i in 0 until cnt) if (keep[i]) { vx += xs[i]; vy += ys[i] }
        // el punto donde empezó el trazo siempre queda marcado aunque caiga en mitad de un lado: se quitan los vértices casi llanos
        var changed = true
        while (changed && vx.size > 3) { changed = false
            for (i in vx.indices) { val a = (i + vx.size - 1) % vx.size; val b = (i + 1) % vx.size
                if (angle(vx[a], vy[a], vx[i], vy[i], vx[b], vy[b]) > 152f || hypot(vx[i] - vx[b], vy[i] - vy[b]) < 0.06f * diag) { vx.removeAt(i); vy.removeAt(i); changed = true; break } } }
        val v = vx.size
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; var rx = (maxX - minX) / 2; var ry = (maxY - minY) / 2
        // error medio (en fracción de la diagonal) respecto a la elipse inscrita y respecto al polígono de las esquinas
        var eE = 0f; var eP = 0f
        for (i in 0 until cnt) {
            val dx = xs[i] - cx; val dy = ys[i] - cy
            val rho = sqrt((dx * dx) / max(rx * rx, 1e-3f) + (dy * dy) / max(ry * ry, 1e-3f))
            eE += abs(rho - 1f) * hypot(dx, dy) / max(rho, 1e-3f)
            var best = Float.MAX_VALUE
            for (k in 0 until v) { val k2 = (k + 1) % v; best = min(best, segDist(xs[i], ys[i], vx[k], vy[k], vx[k2], vy[k2])) }
            eP += best
        }
        eE /= cnt * diag; eP /= cnt * diag
        val polygon = v in 3..8 && eP < 0.03f && eP < eE * 0.75f
        if (!polygon) {
            if (abs(rx - ry) / max(rx, ry) < 0.15f) { rx = (rx + ry) / 2; ry = rx }
            val k = 96
            return Fit(ELLIPSE, FloatArray((k + 1) * 2) { val i = it / 2; val a = 2 * PI * i / k; if (it % 2 == 0) cx + rx * cos(a).toFloat() else cy + ry * sin(a).toFloat() }, true)
        }
        if (v == 4) {
            var axis = true
            for (i in 0 until 4) { val j = (i + 1) % 4; val a = abs(Math.toDegrees(atan2((vy[j] - vy[i]).toDouble(), (vx[j] - vx[i]).toDouble()))) % 90; if (a > 14 && a < 76) axis = false }
            if (axis) {
                var l = minX; var t = minY; var r = maxX; var b = maxY
                // casi cuadrado: cuadrado
                val w = r - l; val h = b - t
                if (abs(w - h) / max(w, h) < 0.12f) { val s = (w + h) / 2; l = cx - s / 2; r = cx + s / 2; t = cy - s / 2; b = cy + s / 2 }
                return Fit(RECT, poly(arrayListOf(l, r, r, l, l), arrayListOf(t, t, b, b, t)), true)
            }
        }
        vx = ArrayList(vx); vy = ArrayList(vy); vx += vx[0]; vy += vy[0]
        return Fit(if (v == 3) TRIANGLE else if (v == 4) RECT else POLYGON, poly(vx, vy), true)
    }

    // ---------- abiertas ----------
    private fun open(p: FloatArray, cnt: Int, diag: Float): Fit? {
        val xs = FloatArray(cnt) { p[3 * it] }; val ys = FloatArray(cnt) { p[3 * it + 1] }
        val x0 = xs[0]; val y0 = ys[0]; val x1 = xs[cnt - 1]; val y1 = ys[cnt - 1]; val chord = hypot(x1 - x0, y1 - y0)
        var dev = 0f; var len = 0f
        for (i in 0 until cnt) { dev = max(dev, segDist(xs[i], ys[i], x0, y0, x1, y1)); if (i > 0) len += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) }
        if (dev < 0.08f * chord) return null   // recta: la sigue arrastrando el lienzo
        val keep = BooleanArray(cnt); keep[0] = true; keep[cnt - 1] = true
        rdp(xs, ys, 0, cnt - 1, 0.06f * diag, keep)
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>()
        for (i in 0 until cnt) if (keep[i]) { vx += xs[i]; vy += ys[i] }
        // esquinas de verdad: giro de más de 40° entre tramos
        var i = 1
        while (i < vx.size - 1) { if (angle(vx[i - 1], vy[i - 1], vx[i], vy[i], vx[i + 1], vy[i + 1]) > 140f) { vx.removeAt(i); vy.removeAt(i) } else i++ }
        val corners = vx.size - 2
        if (corners >= 2) {
            // flecha de un tirón: un asta larga y luego la punta (tramos cortos que no se alejan del extremo)
            val shaft = hypot(vx[1] - vx[0], vy[1] - vy[0]); var head = shaft > 0.5f * diag
            for (k in 2 until vx.size) if (hypot(vx[k] - vx[1], vy[k] - vy[1]) > 0.45f * shaft) head = false
            if (head) { val e = snapAngle(vx[0], vy[0], vx[1], vy[1]); return Fit(ARROW, arrow(vx[0], vy[0], e[0], e[1]), false) }
        }
        if (corners <= 2) {
            // curva suave: Bézier cuadrática por mínimos cuadrados (parámetro = longitud recorrida)
            val t = FloatArray(cnt); for (k in 1 until cnt) t[k] = t[k - 1] + hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1])
            var sw = 0f; var sx = 0f; var sy = 0f
            for (k in 0 until cnt) { val u = t[k] / max(len, 1e-3f); val w = 2 * u * (1 - u); val a = (1 - u) * (1 - u); val b = u * u
                sw += w * w; sx += w * (xs[k] - a * x0 - b * x1); sy += w * (ys[k] - a * y0 - b * y1) }
            val qx = sx / max(sw, 1e-4f); val qy = sy / max(sw, 1e-4f); val m = 64; val out = FloatArray((m + 1) * 2)
            for (k in 0..m) { val u = k.toFloat() / m; val a = (1 - u) * (1 - u); val w = 2 * u * (1 - u); val b = u * u; out[2 * k] = a * x0 + w * qx + b * x1; out[2 * k + 1] = a * y0 + w * qy + b * y1 }
            var err = 0f
            for (k in 0 until cnt) { var best = Float.MAX_VALUE; for (j in 0 until m) best = min(best, segDist(xs[k], ys[k], out[2 * j], out[2 * j + 1], out[2 * j + 2], out[2 * j + 3])); err += best }
            if (err / cnt < (if (corners == 0) 0.03f else 0.02f) * diag) return Fit(CURVE, out, false)
        }
        if (corners in 1..7) {
            var err = 0f
            for (k in 0 until cnt) { var best = Float.MAX_VALUE; for (j in 0 until vx.size - 1) best = min(best, segDist(xs[k], ys[k], vx[j], vy[j], vx[j + 1], vy[j + 1])); err += best }
            if (err / cnt < 0.015f * diag) return Fit(POLYLINE, poly(vx, vy), false)
        }
        return Fit(NONE, FloatArray(0), false)
    }

    /** Flecha de (x0, y0) a (x1, y1): asta y dos barbas, en un solo recorrido. */
    fun arrow(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val a = atan2(y1 - y0, x1 - x0); val h = (hypot(x1 - x0, y1 - y0) * 0.18f).coerceIn(10f, 46f)
        val ax = x1 - h * cos(a - 0.45f); val ay = y1 - h * sin(a - 0.45f); val bx = x1 - h * cos(a + 0.45f); val by = y1 - h * sin(a + 0.45f)
        return poly(arrayListOf(x0, x1, ax, x1, bx), arrayListOf(y0, y1, ay, y1, by))
    }

    /** Polígono regular de [sides] lados inscrito en la caja (l, t, r, b), con un vértice arriba. */
    fun regular(sides: Int, l: Float, t: Float, r: Float, b: Float): FloatArray {
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>(); val cx = (l + r) / 2; val cy = (t + b) / 2
        for (i in 0..sides) { val a = -PI / 2 + 2 * PI * (i % sides) / sides; vx += cx + (r - l) / 2 * cos(a).toFloat(); vy += cy + (b - t) / 2 * sin(a).toFloat() }
        return poly(vx, vy)
    }

    /** Estrella de cinco puntas en la caja (l, t, r, b). */
    fun star(l: Float, t: Float, r: Float, b: Float): FloatArray {
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>(); val cx = (l + r) / 2; val cy = (t + b) / 2
        for (i in 0..10) { val a = -PI / 2 + PI * (i % 10) / 5; val k = if (i % 2 == 0) 1f else 0.42f; vx += cx + (r - l) / 2 * k * cos(a).toFloat(); vy += cy + (b - t) / 2 * k * sin(a).toFloat() }
        return poly(vx, vy)
    }

    /** Ángulo interior en B (grados) del codo A-B-C. */
    private fun angle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
        val ux = ax - bx; val uy = ay - by; val wx = cx - bx; val wy = cy - by; val l = hypot(ux, uy) * hypot(wx, wy)
        return if (l < 1e-4f) 180f else Math.toDegrees(acos(((ux * wx + uy * wy) / l).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay; val l = vx * vx + vy * vy
        val t = if (l <= 0f) 0f else (((x - ax) * vx + (y - ay) * vy) / l).coerceIn(0f, 1f)
        return hypot(ax + t * vx - x, ay + t * vy - y)
    }

    /** Recorre los vértices con un punto cada ~4 unidades (los lápices de ancho variable necesitan muestras seguidas). */
    fun poly(vx: List<Float>, vy: List<Float>): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0 until vx.size - 1) {
            val steps = (hypot(vx[i + 1] - vx[i], vy[i + 1] - vy[i]) / 4f).toInt().coerceIn(1, 600)
            for (s in 0 until steps) { val t = s.toFloat() / steps; out += vx[i] + (vx[i + 1] - vx[i]) * t; out += vy[i] + (vy[i + 1] - vy[i]) * t }
        }
        out += vx.last(); out += vy.last(); return out.toFloatArray()
    }

    private fun rdp(xs: FloatArray, ys: FloatArray, a: Int, b: Int, eps: Float, keep: BooleanArray) {
        if (b <= a + 1) return
        var md = 0f; var mi = -1
        val dx = xs[b] - xs[a]; val dy = ys[b] - ys[a]; val len = hypot(dx, dy)
        for (i in a + 1 until b) {
            val d = if (len < 1e-3f) hypot(xs[i] - xs[a], ys[i] - ys[a]) else abs(dy * xs[i] - dx * ys[i] + xs[b] * ys[a] - ys[b] * xs[a]) / len
            if (d > md) { md = d; mi = i }
        }
        if (md > eps && mi > 0) { keep[mi] = true; rdp(xs, ys, a, mi, eps, keep); rdp(xs, ys, mi, b, eps, keep) }
    }
}
