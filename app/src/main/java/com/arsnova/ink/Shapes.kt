package com.arsnova.ink

import kotlin.math.*

/** Reconocimiento de figuras al mantener el trazo: triángulo, rectángulo, círculo/elipse. */
object Shapes {
    fun recognize(p: FloatArray, n: Int): FloatArray? {
        val cnt = n / 3; if (cnt < 8) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until cnt) { val x = p[3 * i]; val y = p[3 * i + 1]; minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
        val diag = hypot(maxX - minX, maxY - minY); if (diag < 40f) return null
        if (hypot(p[0] - p[n - 3], p[1] - p[n - 2]) > 0.25f * diag) return null
        val xs = FloatArray(cnt + 1) { p[3 * (it % cnt)] }; val ys = FloatArray(cnt + 1) { p[3 * (it % cnt) + 1] }
        val keep = BooleanArray(cnt + 1); keep[0] = true; keep[cnt] = true
        rdp(xs, ys, 0, cnt, 0.08f * diag, keep)
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>()
        for (i in 0..cnt) if (keep[i]) { vx += xs[i]; vy += ys[i] }
        val v = vx.size - 1
        return when (v) {
            3 -> poly(vx, vy)
            4 -> {
                var axis = true
                for (i in 0 until 4) { val a = abs(Math.toDegrees(atan2((vy[i + 1] - vy[i]).toDouble(), (vx[i + 1] - vx[i]).toDouble()))) % 90; if (a > 14 && a < 76) axis = false }
                if (axis) poly(arrayListOf(minX, maxX, maxX, minX, minX), arrayListOf(minY, minY, maxY, maxY, minY)) else poly(vx, vy)
            }
            else -> {
                val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; var rx = (maxX - minX) / 2; var ry = (maxY - minY) / 2
                if (abs(rx - ry) / max(rx, ry) < 0.15f) { rx = (rx + ry) / 2; ry = rx }
                val k = 96; FloatArray((k + 1) * 2) { val i = it / 2; val a = 2 * PI * i / k; if (it % 2 == 0) cx + rx * cos(a).toFloat() else cy + ry * sin(a).toFloat() }
            }
        }
    }

    private fun poly(vx: List<Float>, vy: List<Float>): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0 until vx.size - 1) {
            val steps = (hypot(vx[i + 1] - vx[i], vy[i + 1] - vy[i]) / 4f).toInt().coerceAtLeast(1)
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
