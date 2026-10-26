package com.arsnova.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import com.arsnova.data.Page
import com.arsnova.data.Stroke
import com.arsnova.data.Tool
import kotlin.math.hypot

/** Lienzo de tinta: caché en bitmap, entrada con puntos históricos, rechazo de palma, zoom/paneo. */
@SuppressLint("ViewConstructor")
class InkView(context: Context) : View(context) {
    var page: Page? = null
        set(v) { if (field === v) return; field = v; sel.clear(); curN = 0; mode = Mode.NONE; fitted = false; cacheDirty = true; invalidate(); notifyState() }
    var tool = Tool.PEN
        set(v) { if (field == v) return; if (v != Tool.LASSO) clearSelection(); field = v; invalidate() }
    var color = 0xFF1D1B18.toInt()
    var penWidth = 5f
    var hlWidth = 28f
    var eraserRadius = 16f
    var stylusOnly = false
    var onChange: () -> Unit = {}
    var onState: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> }
    /** Doble toque / botón del lápiz: abre la rueda en (x, y) de la vista. */
    var onWheel: (Float, Float) -> Unit = { _, _ -> }
    /** Modo aprender: recibe el código de botón del lápiz. Devuelve true si lo consumió. */
    var onStylusButton: (Int) -> Boolean = { false }
    var hoverX = -1f; private set
    var hoverY = -1f; private set
    private val predictor = MotionEventPredictor.newInstance(this)
    private var predN = 0

    private sealed class Op {
        class Add(val s: Stroke) : Op()
        class Remove(val items: List<Pair<Int, Stroke>>) : Op()
        class Move(val s: List<Stroke>, val dx: Float, val dy: Float) : Op()
        class Recolor(val s: List<Stroke>, val old: IntArray, val new: IntArray) : Op()
    }
    private val undoMap = HashMap<Long, ArrayDeque<Op>>()
    private val redoMap = HashMap<Long, ArrayDeque<Op>>()
    private fun undos() = undoMap.getOrPut(page!!.id) { ArrayDeque() }
    private fun redos() = redoMap.getOrPut(page!!.id) { ArrayDeque() }

    private val matrix = Matrix(); private val inv = Matrix(); private val cacheMatrix = Matrix(); private val tmp = Matrix()
    private var scale = 1f; private var tx = 0f; private var ty = 0f; private var fitted = false
    private var cache: Bitmap? = null; private var cacheDirty = true
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint().apply { color = 0x22000000; maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL) }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFD9481F.toInt(); strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x88000000.toInt(); strokeWidth = 2f }

    private enum class Mode { NONE, DRAW, ERASE, LASSO, MOVE_SEL, NAV }
    private var mode = Mode.NONE
    private var pid = -1; private var isStylus = false

    private var cur = FloatArray(1536); private var curN = 0; private var curHl = false
    private var snapped = false; private var sx0 = 0f; private var sy0 = 0f; private var sigX = 0f; private var sigY = 0f
    private val erased = ArrayList<Pair<Int, Stroke>>()
    private var ex = -1f; private var ey = -1f
    private val lasso = Path()
    private val sel = ArrayList<Stroke>(); private val selBounds = RectF()
    private var selDx = 0f; private var selDy = 0f; private var lastX = 0f; private var lastY = 0f
    private var lfx = 0f; private var lfy = 0f; private var lspan = 0f
    private val pt = FloatArray(2)

    private val snap = Runnable {
        if (mode == Mode.DRAW && curN >= 18) {
            snapped = true; sx0 = cur[0]; sy0 = cur[1]
            line(cur[curN - 3], cur[curN - 2]); performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); invalidate()
        }
    }

    // ---------- API ----------
    fun canUndo() = page != null && undos().isNotEmpty()
    fun undo() { val op = undos().removeLastOrNull() ?: return; revert(op); redos().addLast(op); after() }
    fun redo() { val op = redos().removeLastOrNull() ?: return; apply(op); undos().addLast(op); after() }
    fun hasSelection() = sel.isNotEmpty()

    fun deleteSelection() {
        val p = page ?: return; if (sel.isEmpty()) return
        val items = ArrayList<Pair<Int, Stroke>>()
        for (s in sel) { val i = p.strokes.indexOf(s); if (i >= 0) { p.strokes.removeAt(i); items += i to s } }
        push(Op.Remove(items)); sel.clear(); after()
    }

    fun recolorSelection(c: Int) {
        if (sel.isEmpty()) return
        val old = IntArray(sel.size) { sel[it].color }
        val new = IntArray(sel.size) { if (sel[it].hl) hlColor(c) else c }
        val op = Op.Recolor(ArrayList(sel), old, new); apply(op); push(op); invalidate(); onChange()
    }

    fun fit() { fitted = false; cacheDirty = true; invalidate() }

    private fun hlColor(c: Int) = (c and 0x00FFFFFF) or 0x66000000

    // ---------- ops ----------
    private fun apply(op: Op) {
        val p = page!!
        when (op) {
            is Op.Add -> p.strokes.add(op.s)
            is Op.Remove -> op.items.forEach { p.strokes.remove(it.second) }
            is Op.Move -> op.s.forEach { it.offset(op.dx, op.dy) }
            is Op.Recolor -> op.s.forEachIndexed { i, s -> s.color = op.new[i] }
        }
    }
    private fun revert(op: Op) {
        val p = page!!
        when (op) {
            is Op.Add -> p.strokes.remove(op.s)
            is Op.Remove -> op.items.asReversed().forEach { (i, s) -> p.strokes.add(i.coerceAtMost(p.strokes.size), s) }
            is Op.Move -> op.s.forEach { it.offset(-op.dx, -op.dy) }
            is Op.Recolor -> op.s.forEachIndexed { i, s -> s.color = op.old[i] }
        }
    }
    private fun push(op: Op) { val u = undos(); u.addLast(op); if (u.size > 300) u.removeFirst(); redos().clear(); notifyState() }
    private fun after() { sel.clear(); cacheDirty = true; invalidate(); notifyState(); onChange() }
    private fun notifyState() { if (page != null) onState(undos().isNotEmpty(), redos().isNotEmpty(), sel.isNotEmpty()) }
    private fun clearSelection() { if (sel.isNotEmpty()) { sel.clear(); cacheDirty = true; invalidate(); notifyState() } }

    // ---------- transform ----------
    private fun updateMatrix() { matrix.setScale(scale, scale); matrix.postTranslate(tx, ty); matrix.invert(inv) }
    private fun doFit() {
        val m = 16f * resources.displayMetrics.density
        scale = ((width - 2 * m) / Ink.PAGE_W).coerceAtMost((height * 1.6f) / Ink.PAGE_H).coerceAtLeast(0.1f)
        tx = (width - Ink.PAGE_W * scale) / 2; ty = m; updateMatrix(); fitted = true
    }
    private fun toPage(x: Float, y: Float): FloatArray { pt[0] = x; pt[1] = y; inv.mapPoints(pt); return pt }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { cache?.recycle(); cache = null; if (!fitted || ow == 0) fitted = false; cacheDirty = true }

    // ---------- draw ----------
    override fun onDraw(c: Canvas) {
        val p = page ?: return
        if (width == 0 || height == 0) return
        if (!fitted) doFit()
        val bmp = cache ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { cache = it; cacheDirty = true }
        if (cacheDirty && mode != Mode.NAV) {
            val cc = Canvas(bmp); cc.drawColor(DESK); cc.save(); cc.concat(matrix)
            cc.drawRect(0f, 6f, Ink.PAGE_W, Ink.PAGE_H + 6f, shadow)
            Ink.drawPaper(cc, p.template)
            val selSet = sel.toHashSet()
            for (s in p.strokes) if (s !in selSet) Ink.drawStroke(cc, s)
            cc.restore(); cacheMatrix.set(matrix); cacheDirty = false
        }
        cacheMatrix.invert(tmp); tmp.postConcat(matrix)
        c.drawColor(DESK); c.drawBitmap(bmp, tmp, bmpPaint)
        c.save(); c.concat(matrix)
        if (sel.isNotEmpty()) {
            c.save(); c.translate(selDx, selDy); sel.forEach { Ink.drawStroke(c, it) }
            dash.strokeWidth = 2f / scale; c.drawRect(selBounds, dash); c.restore()
        }
        if (mode == Mode.DRAW && curN > 0) Ink.drawLive(c, cur, curN + predN, if (curHl) hlWidth else penWidth, curHl, if (curHl) hlColor(color) else color)
        if (mode == Mode.LASSO) { dash.strokeWidth = 2f / scale; c.drawPath(lasso, dash) }
        c.restore()
        if (mode == Mode.ERASE && ex >= 0) c.drawCircle(ex, ey, eraserRadius, cursor)
        if (mode == Mode.NONE && hoverX >= 0) when (tool) {
            Tool.ERASER -> c.drawCircle(hoverX, hoverY, eraserRadius, cursor)
            Tool.PEN, Tool.HIGHLIGHTER -> {
                hoverPaint.color = if (tool == Tool.HIGHLIGHTER) hlColor(color) else (color and 0x00FFFFFF) or 0x99000000.toInt()
                c.drawCircle(hoverX, hoverY, ((if (tool == Tool.HIGHLIGHTER) hlWidth else penWidth) * scale / 2).coerceAtLeast(2.5f), hoverPaint)
            }
            else -> {}
        }
    }

    // ---------- input ----------
    private fun stylusAt(e: MotionEvent, i: Int) = e.getToolType(i).let { it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        page ?: return false
        predictor.record(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e); hoverX = -1f
                parent?.requestDisallowInterceptTouchEvent(true)
                isStylus = stylusAt(e, 0)
                if (isStylus || !stylusOnly) begin(e, 0) else { mode = Mode.NAV; navReset(e, -1) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                when {
                    mode == Mode.NAV && stylusOnly && stylusAt(e, i) -> { endNav(); isStylus = true; begin(e, i) }
                    mode == Mode.NAV -> navReset(e, -1)
                    !isStylus -> { cancelDraw(); mode = Mode.NAV; navReset(e, -1) }
                }
            }
            MotionEvent.ACTION_MOVE -> if (mode == Mode.NAV) navMove(e) else moveDraw(e)
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.NAV) navReset(e, e.actionIndex)
                else if (e.getPointerId(e.actionIndex) == pid) { endDraw(); mode = Mode.NONE }
            }
            MotionEvent.ACTION_UP -> { if (mode == Mode.NAV) endNav() else if (mode != Mode.NONE) endDraw(); mode = Mode.NONE; invalidate() }
            MotionEvent.ACTION_CANCEL -> { if (mode == Mode.NAV) endNav() else cancelDraw(); mode = Mode.NONE }
        }
        return true
    }

    private fun begin(e: MotionEvent, i: Int) {
        pid = e.getPointerId(i)
        val eraserBtn = e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER || (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val t = if (eraserBtn) Tool.ERASER else tool
        val sx = e.getX(i); val sy = e.getY(i); val q = toPage(sx, sy); val x = q[0]; val y = q[1]
        when (t) {
            Tool.PEN, Tool.HIGHLIGHTER -> {
                clearSelection(); mode = Mode.DRAW; curHl = t == Tool.HIGHLIGHTER; curN = 0; snapped = false
                add(x, y, pressure(e.getPressure(i))); sigX = sx; sigY = sy; postSnap()
            }
            Tool.ERASER -> { mode = Mode.ERASE; erased.clear(); ex = sx; ey = sy; eraseAt(x, y) }
            Tool.LASSO -> if (sel.isNotEmpty() && selBounds.contains(x, y)) { mode = Mode.MOVE_SEL; lastX = x; lastY = y }
                else { clearSelection(); mode = Mode.LASSO; lasso.reset(); lasso.moveTo(x, y) }
        }
        invalidate()
    }

    private fun pressure(p: Float) = if (isStylus) p else 0.55f

    private fun moveDraw(e: MotionEvent) {
        val i = e.findPointerIndex(pid); if (i < 0) return
        for (h in 0 until e.historySize) point(e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalPressure(i, h))
        point(e.getX(i), e.getY(i), e.getPressure(i))
        predN = 0
        if (mode == Mode.DRAW && !snapped && curN >= 3) predictor.predict()?.let { pe ->
            val pi = pe.findPointerIndex(pid).coerceAtLeast(0)
            val q = toPage(pe.getX(pi), pe.getY(pi))
            if (curN + 3 > cur.size) cur = cur.copyOf(cur.size * 2)
            cur[curN] = q[0]; cur[curN + 1] = q[1]; cur[curN + 2] = cur[curN - 1]; predN = 3
            pe.recycle()
        }
        invalidate()
    }

    private fun point(sx: Float, sy: Float, pr: Float) {
        val q = toPage(sx, sy); val x = q[0]; val y = q[1]
        when (mode) {
            Mode.DRAW -> {
                if (snapped) { line(x, y); return }
                if (hypot(sx - sigX, sy - sigY) > 6f) { sigX = sx; sigY = sy; postSnap() }
                if (hypot(x - cur[curN - 3], y - cur[curN - 2]) * scale < 0.8f) return
                add(x, y, pressure(pr))
            }
            Mode.ERASE -> { ex = sx; ey = sy; eraseAt(x, y) }
            Mode.LASSO -> lasso.lineTo(x, y)
            Mode.MOVE_SEL -> { selDx += x - lastX; selDy += y - lastY; lastX = x; lastY = y }
            else -> {}
        }
    }

    private fun postSnap() { removeCallbacks(snap); postDelayed(snap, 550) }

    private fun add(x: Float, y: Float, p: Float) {
        if (curN + 3 > cur.size) cur = cur.copyOf(cur.size * 2)
        cur[curN++] = x; cur[curN++] = y; cur[curN++] = p
    }

    private fun line(x1: Float, y1: Float) {
        var avg = 0f; var k = 0; var i = 2; while (i < curN) { avg += cur[i]; k++; i += 3 }
        val p = if (k > 0) (avg / k) else 0.5f
        val steps = (hypot(x1 - sx0, y1 - sy0) / 4f).toInt().coerceIn(1, 500)
        curN = 0
        for (s in 0..steps) { val t = s.toFloat() / steps; add(sx0 + (x1 - sx0) * t, sy0 + (y1 - sy0) * t, p) }
    }

    private fun eraseAt(x: Float, y: Float) {
        val p = page!!; val r = eraserRadius / scale; var hit = false
        for (idx in p.strokes.indices.reversed()) {
            val s = p.strokes[idx]; val b = s.bounds
            if (x < b.left - r || x > b.right + r || y < b.top - r || y > b.bottom + r) continue
            val lim = (r + s.width / 2).let { it * it }; val a = s.pts; var j = 0
            while (j < a.size) {
                val dx = a[j] - x; val dy = a[j + 1] - y
                if (dx * dx + dy * dy <= lim) { p.strokes.removeAt(idx); erased += idx to s; hit = true; break }
                j += 3
            }
        }
        if (hit) cacheDirty = true
        invalidate()
    }

    private fun endDraw() {
        removeCallbacks(snap); predN = 0
        val p = page ?: return
        when (mode) {
            Mode.DRAW -> if (curN >= 3) {
                val s = Stroke(System.nanoTime(), if (curHl) hlColor(color) else color, if (curHl) hlWidth else penWidth, curHl, cur.copyOf(curN))
                p.strokes.add(s); push(Op.Add(s))
                if (!cacheDirty) cache?.let { val cc = Canvas(it); cc.concat(cacheMatrix); Ink.drawStroke(cc, s) }
                curN = 0; onChange()
            }
            Mode.ERASE -> { ex = -1f; if (erased.isNotEmpty()) { push(Op.Remove(ArrayList(erased))); erased.clear(); onChange() } }
            Mode.LASSO -> {
                lasso.close()
                val rg = Region().apply { setPath(lasso, Region(-20000, -20000, 40000, 40000)) }
                selBounds.setEmpty()
                for (s in p.strokes) {
                    var inside = 0; var tot = 0; var j = 0
                    while (j < s.pts.size) { tot++; if (rg.contains(s.pts[j].toInt(), s.pts[j + 1].toInt())) inside++; j += 3 }
                    if (tot > 0 && inside * 2 >= tot) { sel += s; selBounds.union(s.bounds) }
                }
                lasso.reset(); cacheDirty = true; notifyState()
            }
            Mode.MOVE_SEL -> if (selDx != 0f || selDy != 0f) {
                val op = Op.Move(ArrayList(sel), selDx, selDy); apply(op); undos().addLast(op); redos().clear()
                selBounds.offset(selDx, selDy); selDx = 0f; selDy = 0f; notifyState(); onChange()
            }
            else -> {}
        }
        invalidate()
    }

    private fun cancelDraw() {
        removeCallbacks(snap)
        if (mode == Mode.ERASE && erased.isNotEmpty()) { push(Op.Remove(ArrayList(erased))); erased.clear(); onChange() }
        curN = 0; lasso.reset(); selDx = 0f; selDy = 0f; ex = -1f; invalidate()
    }

    // ---------- hover / botones del lápiz ----------
    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (!stylusAt(e, 0)) return super.onHoverEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hoverX = e.x; hoverY = e.y }
            MotionEvent.ACTION_HOVER_EXIT -> hoverX = -1f
        }
        invalidate(); return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && stylusAt(e, 0) && mode == Mode.NONE) {
            val code = 1000 + e.actionButton
            if (!onStylusButton(code)) onWheel(e.x, e.y)
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    // ---------- nav ----------
    private fun navReset(e: MotionEvent, skip: Int) {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { sx += e.getX(i); sy += e.getY(i); n++ }
        if (n == 0) return
        lfx = sx / n; lfy = sy / n
        lspan = if (n >= 2) { var d = 0f; for (i in 0 until e.pointerCount) if (i != skip) d += hypot(e.getX(i) - lfx, e.getY(i) - lfy); d / n } else 0f
    }
    private fun navMove(e: MotionEvent) {
        val n = e.pointerCount; var sx = 0f; var sy = 0f
        for (i in 0 until n) { sx += e.getX(i); sy += e.getY(i) }
        val fx = sx / n; val fy = sy / n
        tx += fx - lfx; ty += fy - lfy
        if (n >= 2) {
            var d = 0f; for (i in 0 until n) d += hypot(e.getX(i) - fx, e.getY(i) - fy); val span = d / n
            if (lspan > 8f) {
                val ns = (scale * span / lspan).coerceIn(0.2f, 8f); val f = ns / scale
                tx = fx - (fx - tx) * f; ty = fy - (fy - ty) * f; scale = ns
            }
            lspan = span
        }
        lfx = fx; lfy = fy; updateMatrix(); invalidate()
    }
    private fun endNav() { cacheDirty = true; invalidate() }

    companion object { const val DESK = 0xFFE9E5DC.toInt() }
}
