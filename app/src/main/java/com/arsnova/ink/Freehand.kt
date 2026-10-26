package com.arsnova.ink

import android.graphics.Path
import kotlin.math.*

/**
 * Adaptación a Kotlin de perfect-freehand (Steve Ruiz, licencia MIT, github.com/steveruizok/perfect-freehand),
 * el algoritmo de contorno de tinta que usan tldraw y Saber.
 */
object Freehand {
    class Opt(val size: Float, val thinning: Float, val smoothing: Float, val streamline: Float, val taperStart: Float = 0f, val taperEnd: Float = 0f)
    private class SP(val x: Float, val y: Float, val p: Float, val vx: Float, val vy: Float, val run: Float)

    fun path(pts: FloatArray, n: Int, o: Opt): Path {
        val outline = outline(points(pts, n, o), o)
        val path = Path(); if (outline.size < 4) return path
        path.moveTo(outline[0], outline[1])
        var i = 2
        while (i < outline.size) {
            val x0 = outline[i - 2]; val y0 = outline[i - 1]; val x1 = outline[i]; val y1 = outline[i + 1]
            path.quadTo(x0, y0, (x0 + x1) / 2, (y0 + y1) / 2); i += 2
        }
        path.close(); return path
    }

    private fun points(pts: FloatArray, n: Int, o: Opt): List<SP> {
        val cnt = n / 3; if (cnt == 0) return emptyList()
        val t = 0.15f + (1 - o.streamline) * 0.85f
        val out = ArrayList<SP>(cnt)
        var px = pts[0]; var py = pts[1]; var run = 0f
        out += SP(px, py, pts[2], 1f, 1f, 0f)
        var reached = false
        for (i in 1 until cnt) {
            val last = i == cnt - 1
            val x = if (last) pts[3 * i] else px + (pts[3 * i] - px) * t
            val y = if (last) pts[3 * i + 1] else py + (pts[3 * i + 1] - py) * t
            val d = hypot(x - px, y - py); if (d < 1e-3f) continue
            run += d
            if (!last && !reached) { if (run < o.size) continue; reached = true }
            out += SP(x, y, pts[3 * i + 2], (px - x) / d, (py - y) / d, run); px = x; py = y
        }
        if (out.size > 1) out[0] = SP(out[0].x, out[0].y, out[0].p, out[1].vx, out[1].vy, 0f)
        return out
    }

    private fun radius(o: Opt, p: Float) = o.size * (0.5f - o.thinning * (0.5f - p.coerceIn(0f, 1f)))

    private fun outline(sp: List<SP>, o: Opt): FloatArray {
        if (sp.isEmpty()) return FloatArray(0)
        val total = sp.last().run; val minD = (o.size * o.smoothing).let { it * it }
        val l = ArrayList<Float>(); val r = ArrayList<Float>()
        if (sp.size == 1) { val c = sp[0]; val rad = max(radius(o, c.p), 0.5f); return circle(c.x, c.y, rad) }
        var plx = sp[0].x; var ply = sp[0].y; var prx = plx; var pry = ply
        var pvx = sp[0].vx; var pvy = sp[0].vy; var prevSharp = false
        var firstR = -1f; var lastR = 0f
        for (i in sp.indices) {
            val c = sp[i]
            if (i < sp.size - 1 && total - c.run < 3) continue
            var rad = if (o.thinning != 0f) radius(o, c.p) else o.size / 2
            val ts = if (o.taperStart > 0 && c.run < o.taperStart) ease(c.run / o.taperStart) else 1f
            val te = if (o.taperEnd > 0 && total - c.run < o.taperEnd) ease((total - c.run) / o.taperEnd) else 1f
            rad = max(0.01f, rad * min(ts, te)); if (firstR < 0) firstR = rad; lastR = rad
            val nx = if (i < sp.size - 1) sp[i + 1].vx else c.vx; val ny = if (i < sp.size - 1) sp[i + 1].vy else c.vy
            val nDpr = if (i < sp.size - 1) c.vx * nx + c.vy * ny else 1f
            val pDpr = c.vx * pvx + c.vy * pvy
            val sharp = pDpr < 0 && !prevSharp; val nextSharp = nDpr < 0
            if (sharp || nextSharp) {
                val ox = -pvy * rad; val oy = pvx * rad
                for (k in 0..13) { val a = PI.toFloat() * k / 13
                    rot(c.x - ox, c.y - oy, c.x, c.y, a).let { l += it.first; l += it.second; plx = it.first; ply = it.second }
                    rot(c.x + ox, c.y + oy, c.x, c.y, -a).let { r += it.first; r += it.second; prx = it.first; pry = it.second } }
                if (nextSharp) prevSharp = true; continue
            }
            prevSharp = false
            if (i == sp.size - 1) { val ox = -c.vy * rad; val oy = c.vx * rad; l += c.x - ox; l += c.y - oy; r += c.x + ox; r += c.y + oy; continue }
            var bx = nx + (c.vx - nx) * nDpr; var by = ny + (c.vy - ny) * nDpr
            val bl = hypot(bx, by).let { if (it < 1e-6f) 1f else it }; bx /= bl; by /= bl
            val ox = -by * rad; val oy = bx * rad
            val tlx = c.x - ox; val tly = c.y - oy
            if (i <= 1 || (tlx - plx).pow(2) + (tly - ply).pow(2) > minD) { l += tlx; l += tly; plx = tlx; ply = tly }
            val trx = c.x + ox; val try_ = c.y + oy
            if (i <= 1 || (trx - prx).pow(2) + (try_ - pry).pow(2) > minD) { r += trx; r += try_; prx = trx; pry = try_ }
            pvx = c.vx; pvy = c.vy
        }
        val out = ArrayList<Float>(l.size + r.size + 64)
        out.addAll(l)
        val e = sp.last(); if (o.taperEnd == 0f && r.size >= 2 && l.size >= 2) arc(out, l[l.size - 2], l[l.size - 1], e.x, e.y)
        for (i in r.size - 2 downTo 0 step 2) { out += r[i]; out += r[i + 1] }
        val s = sp.first(); if (o.taperStart == 0f && r.size >= 2) arc(out, r[0], r[1], s.x, s.y)
        return out.toFloatArray()
    }

    private fun ease(t: Float) = t * (2 - t)
    private fun rot(x: Float, y: Float, cx: Float, cy: Float, a: Float): Pair<Float, Float> {
        val s = sin(a); val c = cos(a); val dx = x - cx; val dy = y - cy
        return Pair(cx + dx * c - dy * s, cy + dx * s + dy * c)
    }
    private fun arc(out: ArrayList<Float>, sx: Float, sy: Float, cx: Float, cy: Float) {
        for (k in 1..12) rot(sx, sy, cx, cy, -PI.toFloat() * k / 13).let { out += it.first; out += it.second }
    }
    private fun circle(x: Float, y: Float, r: Float) = FloatArray(48) { val k = it / 2; val a = 2 * PI * k / 24; if (it % 2 == 0) x + r * cos(a).toFloat() else y + r * sin(a).toFloat() }
}
