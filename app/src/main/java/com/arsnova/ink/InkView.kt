package com.arsnova.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import com.arsnova.data.Page
import com.arsnova.data.Item
import com.arsnova.data.Stroke
import com.arsnova.data.Tool
import kotlin.math.hypot

/** Lienzo de tinta: caché en bitmap, entrada con puntos históricos, rechazo de palma, zoom/paneo. */
@SuppressLint("ViewConstructor")
class InkView(context: Context) : View(context) {
    /** Todas las páginas en un rollo vertical continuo. */
    var pages: List<Page> = emptyList()
        set(v) { if (field === v) return; field = v; page = v.firstOrNull(); pageOy = 0f; sel.clear(); selIt.clear(); curN = 0; mode = Mode.NONE
            fitted = false; hadFit = false; undoStack.clear(); redoStack.clear(); dropCache(); invalidate(); notifyState() }
    var page: Page? = null; private set
    private var pageOy = 0f
    var onPage: (Int) -> Unit = {}
    var onNeedPage: () -> Unit = {}
    private var reported = -1; private var hadFit = false
    var tool = Tool.PEN
        set(v) { if (field == v) return; if (v != Tool.LASSO) clearSelection(); field = v; invalidate() }
    var color = 0xFF1D1B18.toInt()
    var penWidth = 5f
    var hlWidth = 28f
    var eraserRadius = 16f
    var stylusOnly = false
    var penStyle = 0
    var stabilize = 0f
    var shapesOn = true
    var eraserPixel = false
    var tapeWidth = 40f
    var tapePattern = 0
    private val laser = ArrayList<FloatArray>()
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pieces = ArrayList<Stroke>()
    var replayTime = -1L
        set(v) { if (field != v) { field = v; cacheDirty = true; invalidate() } }
    var tapMode = false
    var onStrokeTap: (Stroke) -> Unit = {}
    private var shapeLocked = false; private var tdx = 0f; private var tdy = 0f
    var desk = DESK
        set(v) { if (field != v) { field = v; cacheDirty = true; invalidate() } }
    var onChange: () -> Unit = {}
    var onState: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> }
    var onInking: (Boolean) -> Unit = {}
    /** Doble toque / botón del lápiz: abre la rueda en (x, y) de la vista. */
    var onWheel: (Float, Float) -> Unit = { _, _ -> }
    /** Modo aprender: recibe el código de botón del lápiz. Devuelve true si lo consumió. */
    var onStylusButton: (Int) -> Boolean = { false }
    var lastPenX = -1f; private set
    var lastPenY = -1f; private set
    var hoverX = -1f; private set
    var hoverY = -1f; private set
    private val predictor = MotionEventPredictor.newInstance(this)
    private var predN = 0

    private sealed class Op {
        var pg: Page? = null
        class Add(val s: Stroke) : Op()
        class Remove(val items: List<Pair<Int, Stroke>>, val its: List<Pair<Int, Item>> = emptyList()) : Op()
        class Move(val s: List<Stroke>, val its: List<Item>, val dx: Float, val dy: Float) : Op()
        class AddItem(val it: Item) : Op()
        class EditText(val it: Item, val old: String, val new: String) : Op()
        class Resize(val it: Item, val ow: Float, val oh: Float, val nw: Float, val nh: Float) : Op()
        class Replace(val removed: List<Pair<Int, Stroke>>, val added: List<Stroke>) : Op()
        class Recolor(val s: List<Stroke>, val old: IntArray, val new: IntArray) : Op()
    }
    private val undoStack = ArrayDeque<Op>(); private val redoStack = ArrayDeque<Op>()
    private fun undos() = undoStack
    private fun redos() = redoStack

    private val matrix = Matrix(); private val inv = Matrix(); private val cacheMatrix = Matrix(); private val tmp = Matrix()
    private var scale = 1f; private var tx = 0f; private var ty = 0f; private var fitted = false
    private class PC(val bmp: Bitmap, val res: Float) { var valid = false }
    private val pcache = HashMap<Long, PC>()
    /** Escribir true invalida las páginas cacheadas (se re-renderizan solo las visibles). */
    private var cacheDirty: Boolean
        get() = false
        set(v) { if (v) for (pc in pcache.values) pc.valid = false }
    private fun dropCache() { pcache.values.forEach { it.bmp.recycle() }; pcache.clear() }
    private val scroller = android.widget.OverScroller(context)
    private var vt: android.view.VelocityTracker? = null
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14000000 }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFD9481F.toInt(); strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x88000000.toInt(); strokeWidth = 2f }

    private enum class Mode { NONE, DRAW, ERASE, LASSO, MOVE_SEL, NAV, TAP, LASER }
    private var mode = Mode.NONE
    private var pid = -1; private var isStylus = false

    private var cur = FloatArray(1536); private var curN = 0; private var curHl = false; private var curStyle = 0
    private var snapped = false; private var sx0 = 0f; private var sy0 = 0f; private var sigX = 0f; private var sigY = 0f
    private val erased = ArrayList<Pair<Int, Stroke>>()
    private var ex = -1f; private var ey = -1f
    private val lasso = Path()
    private val sel = ArrayList<Stroke>(); private val selIt = ArrayList<Item>(); private val selBounds = RectF()
    private var resizing = false
    var onTextEdit: (Item, Boolean) -> Unit = { _, _ -> }
    private var selDx = 0f; private var selDy = 0f; private var lastX = 0f; private var lastY = 0f
    private var lfx = 0f; private var lfy = 0f; private var lspan = 0f
    private val pt = FloatArray(2)

    private val snap = Runnable {
        if (shapesOn && mode == Mode.DRAW && curN >= 18) {
            snapped = true; sx0 = cur[0]; sy0 = cur[1]
            val sh = Shapes.recognize(cur, curN)
            if (sh != null) {
                var avg = 0f; var i = 2; while (i < curN) { avg += cur[i]; i += 3 }; avg /= (curN / 3)
                curN = 0; var j = 0; while (j < sh.size) { add(sh[j], sh[j + 1], avg); j += 2 }; shapeLocked = true
            } else line(cur[curN - 3], cur[curN - 2])
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); invalidate()
        }
    }

    // ---------- API ----------
    fun canUndo() = page != null && undos().isNotEmpty()
    fun undo() { val op = undos().removeLastOrNull() ?: return; revert(op); redos().addLast(op); after() }
    fun redo() { val op = redos().removeLastOrNull() ?: return; apply(op); undos().addLast(op); after() }
    fun hasSelection() = sel.isNotEmpty() || selIt.isNotEmpty()

    fun deleteSelection() {
        val p = page ?: return; if (!hasSelection()) return
        val items = ArrayList<Pair<Int, Stroke>>(); val its = ArrayList<Pair<Int, Item>>()
        for (s in sel) { val i = p.strokes.indexOf(s); if (i >= 0) { p.strokes.removeAt(i); items += i to s } }
        for (s in selIt) { val i = p.items.indexOf(s); if (i >= 0) { p.items.removeAt(i); its += i to s } }
        push(Op.Remove(items, its)); sel.clear(); selIt.clear(); after()
    }

    fun recolorSelection(c: Int) {
        if (sel.isEmpty()) return
        val old = IntArray(sel.size) { sel[it].color }
        val new = IntArray(sel.size) { if (sel[it].hl) hlColor(c) else c }
        val op = Op.Recolor(ArrayList(sel), old, new); apply(op); push(op); invalidate(); onChange()
    }

    fun selectedStrokes(): List<Stroke> = ArrayList(sel)

    /** Sustituye los trazos seleccionados por un cuadro de texto (deshacible en dos pasos). */
    fun replaceSelectionWithText(text: String) {
        val p = page ?: return; if (sel.isEmpty() || text.isBlank()) return
        val b = RectF(selBounds); deleteSelection()
        val it = Item(System.nanoTime(), 0, b.left, b.top, b.width().coerceAtLeast(240f), text = text, size = (b.height() / text.lines().size * 0.7f).coerceIn(24f, 90f), color = color)
        p.items.add(it); push(Op.AddItem(it)); cacheDirty = true; invalidate(); onChange()
    }

    fun commitText(it: Item, text: String, isNew: Boolean) {
        val p = page ?: return
        when {
            isNew && text.isNotBlank() -> { it.text = text; p.items.add(it); push(Op.AddItem(it)) }
            !isNew && text.isBlank() -> { val i = p.items.indexOf(it); if (i >= 0) { p.items.removeAt(i); push(Op.Remove(emptyList(), listOf(i to it))) } }
            !isNew && text != it.text -> { val op = Op.EditText(it, it.text, text); apply(op); push(op) }
            else -> return
        }
        cacheDirty = true; invalidate(); onChange()
    }

    var topInset = 0f
    fun addSticker(e: String) {
        activateAt(height / 2f); val p = page ?: return; val c = toPage(width / 2f, height / 2f)
        val it = Item(System.nanoTime(), 0, c[0] - 90f, c[1] - 90f, 220f, text = e, size = 150f)
        p.items.add(it); push(Op.AddItem(it)); cacheDirty = true; invalidate(); onChange()
    }

    fun addImage(file: String, bw: Int, bh: Int) {
        activateAt(height / 2f); val p = page ?: return
        val c = toPage(width / 2f, height / 2f); val w = minOf(700f, Ink.PAGE_W * 0.6f); val h = w * bh / bw
        val it = Item(System.nanoTime(), 1, c[0] - w / 2, c[1] - h / 2, w, h, file = file)
        p.items.add(it); push(Op.AddItem(it)); cacheDirty = true; invalidate(); onChange()
    }

    fun fit() { fitted = false; cacheDirty = true; invalidate() }
    fun refresh() { cacheDirty = true; invalidate() }

    private fun hlColor(c: Int) = (c and 0x00FFFFFF) or 0x66000000

    // ---------- ops ----------
    private fun apply(op: Op) {
        val p = op.pg ?: page!!
        when (op) {
            is Op.Add -> p.strokes.add(op.s)
            is Op.Remove -> { op.items.forEach { p.strokes.remove(it.second) }; op.its.forEach { p.items.remove(it.second) } }
            is Op.Move -> { op.s.forEach { it.offset(op.dx, op.dy) }; op.its.forEach { it.offset(op.dx, op.dy) } }
            is Op.AddItem -> p.items.add(op.it)
            is Op.Replace -> { op.removed.forEach { p.strokes.remove(it.second) }; p.strokes.addAll(op.added) }
            is Op.EditText -> op.it.text = op.new
            is Op.Resize -> { op.it.w = op.nw; op.it.h = op.nh }
            is Op.Recolor -> op.s.forEachIndexed { i, s -> s.color = op.new[i] }
        }
    }
    private fun revert(op: Op) {
        val p = op.pg ?: page!!
        when (op) {
            is Op.Add -> p.strokes.remove(op.s)
            is Op.Remove -> { op.items.asReversed().forEach { (i, s) -> p.strokes.add(i.coerceAtMost(p.strokes.size), s) }
                op.its.asReversed().forEach { (i, s) -> p.items.add(i.coerceAtMost(p.items.size), s) } }
            is Op.Move -> { op.s.forEach { it.offset(-op.dx, -op.dy) }; op.its.forEach { it.offset(-op.dx, -op.dy) } }
            is Op.AddItem -> p.items.remove(op.it)
            is Op.Replace -> { p.strokes.removeAll(op.added.toSet()); op.removed.asReversed().forEach { (i, s) -> p.strokes.add(i.coerceAtMost(p.strokes.size), s) } }
            is Op.EditText -> op.it.text = op.old
            is Op.Resize -> { op.it.w = op.ow; op.it.h = op.oh }
            is Op.Recolor -> op.s.forEachIndexed { i, s -> s.color = op.old[i] }
        }
    }
    private fun push(op: Op) { if (op.pg == null) op.pg = page; val u = undos(); u.addLast(op); if (u.size > 300) u.removeFirst(); redos().clear(); notifyState() }
    private fun after() { sel.clear(); selIt.clear(); cacheDirty = true; invalidate(); notifyState(); onChange() }
    private fun notifyState() { if (page != null) onState(undos().isNotEmpty(), redos().isNotEmpty(), hasSelection()) }
    private fun clearSelection() { if (hasSelection()) { sel.clear(); selIt.clear(); cacheDirty = true; invalidate(); notifyState() } }

    // ---------- transform ----------
    private fun updateMatrix() { matrix.setScale(scale, scale); matrix.postTranslate(tx, ty); matrix.invert(inv) }
    private val gap = 44f
    private fun top(i: Int) = i * (Ink.PAGE_H + gap)
    private fun contentH() = (pages.size * (Ink.PAGE_H + gap) - gap).coerceAtLeast(Ink.PAGE_H)
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun doFit() {
        val m = dp(16f); val cy = if (hadFit) (height / 2f - ty) / scale else -1f
        scale = ((width - 2 * m) / Ink.PAGE_W).coerceAtMost((height * 1.6f) / Ink.PAGE_H).coerceAtLeast(0.1f)
        tx = (width - Ink.PAGE_W * scale) / 2; ty = if (cy >= 0) height / 2f - cy * scale else m + topInset
        clamp(); updateMatrix(); fitted = true; hadFit = true
    }
    private fun clamp() {
        val m = dp(16f); val w = Ink.PAGE_W * scale
        tx = if (w <= width) (width - w) / 2 else tx.coerceIn(width - w - m, m)
        val maxTy = topInset + m; val minTy = (height - contentH() * scale - dp(130f)).coerceAtMost(maxTy)
        ty = ty.coerceIn(minTy, maxTy)
    }
    private fun toPage(x: Float, y: Float): FloatArray { pt[0] = x; pt[1] = y; inv.mapPoints(pt); pt[1] -= pageOy; return pt }
    /** Activa la página bajo un punto de pantalla. */
    private fun activateAt(sy: Float) {
        if (pages.isEmpty()) return
        pt[0] = 0f; pt[1] = sy; inv.mapPoints(pt)
        val i = (pt[1] / (Ink.PAGE_H + gap)).toInt().coerceIn(0, pages.size - 1)
        if (pages[i] !== page) { clearSelection(); page = pages[i]; pageOy = top(i); notifyState() }
    }
    fun pagesChanged() {
        if (page !in pages) { clearSelection(); page = pages.firstOrNull() }
        pageOy = top(pages.indexOf(page).coerceAtLeast(0)); clamp(); updateMatrix(); invalidate()
    }
    fun scrollToPage(i: Int) {
        val target = topInset + dp(16f) - top(i) * scale; val from = ty
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply { duration = 380; interpolator = android.view.animation.DecelerateInterpolator(2f)
            addUpdateListener { ty = from + (target - from) * (it.animatedValue as Float); clamp(); updateMatrix(); invalidate() }; start() }
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) { tx = scroller.currX.toFloat(); ty = scroller.currY.toFloat(); clamp(); updateMatrix(); postInvalidateOnAnimation() }
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { fitted = false }
    private fun targetRes() = (scale.coerceIn(0.4f, 2400f / Ink.PAGE_W) * 8).toInt() / 8f
    private fun pageBitmap(p: Page, res: Float): Bitmap {
        var pc = pcache[p.id]
        if (pc == null || (res > 0 && pc.res != res)) {
            val r = if (res > 0) res else (pc?.res ?: targetRes())
            pc?.bmp?.recycle()
            pc = PC(Bitmap.createBitmap((Ink.PAGE_W * r).toInt(), (Ink.PAGE_H * r).toInt(), Bitmap.Config.ARGB_8888), r); pcache[p.id] = pc
        }
        if (!pc.valid) {
            val cc = Canvas(pc.bmp); cc.scale(pc.res, pc.res); val on = p === page
            Ink.drawPaper(cc, p)
            for (it in p.items) if (!on || it !in selIt) Ink.drawItem(cc, it)
            val selSet = if (on) sel.toHashSet() else emptySet()
            for (s in p.strokes) if (s !in selSet) Ink.drawStroke(cc, s, replayTime >= 0 && s.t > replayTime)
            pc.valid = true
        }
        return pc.bmp
    }

    // ---------- draw ----------
    override fun onDraw(c: Canvas) {
        if (pages.isEmpty() || width == 0 || height == 0) return
        if (!fitted) doFit()
        c.drawColor(desk)
        val res = if (mode == Mode.NAV || !scroller.isFinished) -1f else targetRes()
        val step = (Ink.PAGE_H + gap) * scale
        val first = (-ty / step).toInt().coerceIn(0, pages.size - 1); val last = ((height - ty) / step).toInt().coerceIn(first, pages.size - 1)
        for (i in first..last) {
            val b = pageBitmap(pages[i], res)
            c.save(); c.concat(matrix); c.translate(0f, top(i))
            c.drawRoundRect(-3f, 3f, Ink.PAGE_W + 3f, Ink.PAGE_H + 9f, 10f, 10f, shadow)
            c.drawRoundRect(-1f, 1f, Ink.PAGE_W + 1f, Ink.PAGE_H + 4f, 6f, 6f, shadow)
            c.scale(Ink.PAGE_W / b.width, Ink.PAGE_H / b.height); c.drawBitmap(b, 0f, 0f, bmpPaint); c.restore()
        }
        if (pcache.size > last - first + 3) { val keep = (maxOf(0, first - 1)..minOf(pages.size - 1, last + 1)).map { pages[it].id }.toHashSet()
            pcache.keys.filter { it !in keep }.forEach { pcache.remove(it)?.bmp?.recycle() } }
        val center = ((height / 2f - ty) / step).toInt().coerceIn(0, pages.size - 1)
        if (center != reported) { reported = center; post { onPage(center) } }
        c.save(); c.concat(matrix); c.translate(0f, pageOy)
        if (hasSelection()) {
            c.save(); c.translate(selDx, selDy); selIt.forEach { Ink.drawItem(c, it) }; sel.forEach { Ink.drawStroke(c, it) }
            dash.strokeWidth = 2f / scale; c.drawRect(selBounds, dash); c.restore()
        }
        if (mode == Mode.DRAW && curN > 0) Ink.drawLive(c, cur, curN + predN, if (curHl) hlWidth else if (curStyle == 11) tapeWidth else penWidth, curStyle, if (curHl) hlColor(color) else color, tapePattern)
        if (mode == Mode.LASSO) { dash.strokeWidth = 2f / scale; c.drawPath(lasso, dash) }
        c.restore()
        if (laser.isNotEmpty()) {
            val now = System.currentTimeMillis().toFloat() % 1e7f
            laser.removeAll { now - it[2] > 650f || now < it[2] }
            for (k in 1 until laser.size) { val a0 = laser[k - 1]; val a1 = laser[k]; val f = 1f - (now - a1[2]) / 650f
                laserPaint.color = (color and 0x00FFFFFF) or ((90 * f).toInt() shl 24); laserPaint.strokeWidth = dp(10f); c.drawLine(a0[0], a0[1], a1[0], a1[1], laserPaint)
                laserPaint.color = (0x00FFFFFF) or ((230 * f).toInt() shl 24); laserPaint.strokeWidth = dp(3f); c.drawLine(a0[0], a0[1], a1[0], a1[1], laserPaint) }
            postInvalidateOnAnimation()
        }
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
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { scroller.forceFinished(true); vt?.recycle(); vt = android.view.VelocityTracker.obtain() }
        vt?.addMovement(e)
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
        pid = e.getPointerId(i); scroller.forceFinished(true); activateAt(e.getY(i))
        if (isStylus) { lastPenX = e.getX(i); lastPenY = e.getY(i) }
        val eraserBtn = e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER || (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val t = if (eraserBtn) Tool.ERASER else tool
        val sx = e.getX(i); val sy = e.getY(i); val q = toPage(sx, sy); val x = q[0]; val y = q[1]
        if (tapMode) { mode = Mode.TAP; tdx = sx; tdy = sy; lastX = x; lastY = y; return }
        when (t) {
            Tool.PEN, Tool.HIGHLIGHTER -> {
                clearSelection(); mode = Mode.DRAW; curHl = t == Tool.HIGHLIGHTER; curStyle = if (curHl) 6 else penStyle; curN = 0; snapped = false; shapeLocked = false
                add(x, y, pressure(e.getPressure(i))); sigX = sx; sigY = sy; postSnap(); onInking(true)
            }
            Tool.ERASER -> { mode = Mode.ERASE; erased.clear(); pieces.clear(); ex = sx; ey = sy; eraseAt(x, y); onInking(true) }
            Tool.TAPE -> { val hitT = hitStroke(x, y)?.takeIf { it.style == 11 }
                if (hitT != null) { hitT.hid = !hitT.hid; pcache[page!!.id]?.valid = false; mode = Mode.NONE; onChange() }
                else { clearSelection(); mode = Mode.DRAW; curHl = false; curStyle = 11; curN = 0; snapped = true; shapeLocked = false; sx0 = x; sy0 = y; add(x, y, 0.5f); add(x, y, 0.5f) } }
            Tool.LASER -> { mode = Mode.LASER; laser.add(floatArrayOf(sx, sy, System.currentTimeMillis().toFloat() % 1e7f)) }
            Tool.TEXT -> { mode = Mode.TAP; tdx = sx; tdy = sy; lastX = x; lastY = y }
            Tool.LASSO -> if (hasSelection() && RectF(selBounds).apply { inset(-30f / scale, -30f / scale) }.contains(x, y)) {
                mode = Mode.MOVE_SEL; lastX = x; lastY = y
                resizing = sel.isEmpty() && selIt.size == 1 && selIt[0].kind == 1 && hypot(x - selBounds.right, y - selBounds.bottom) < 40f / scale }
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
        if (isStylus) { lastPenX = sx; lastPenY = sy }
        val q = toPage(sx, sy); val x = q[0]; val y = q[1]
        when (mode) {
            Mode.DRAW -> {
                if (curStyle == 11) { curN = 3; add(x, y, 0.5f); return }
                if (snapped) { if (!shapeLocked) line(x, y); return }
                if (hypot(sx - sigX, sy - sigY) > 6f) { sigX = sx; sigY = sy; postSnap() }
                if (hypot(x - cur[curN - 3], y - cur[curN - 2]) * scale < 0.8f) return
                val k = 1f - stabilize * 0.88f
                add(cur[curN - 3] + (x - cur[curN - 3]) * k, cur[curN - 2] + (y - cur[curN - 2]) * k, pressure(pr))
            }
            Mode.ERASE -> { ex = sx; ey = sy; eraseAt(x, y) }
            Mode.LASER -> laser.add(floatArrayOf(sx, sy, System.currentTimeMillis().toFloat() % 1e7f))
            Mode.LASSO -> lasso.lineTo(x, y)
            Mode.TAP -> if (hypot(sx - tdx, sy - tdy) > 16f) mode = Mode.NONE
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
            val lim = (r + s.width / 2).let { it * it }; val a = s.pts
            if (eraserPixel && s.style != 11) {
                // Borrador de píxel: parte el trazo en los tramos que quedan fuera del círculo.
                val keep = BooleanArray(a.size / 3) { k -> val dx = a[3 * k] - x; val dy = a[3 * k + 1] - y; dx * dx + dy * dy > (r * r) }
                if (keep.all { it }) continue
                p.strokes.removeAt(idx); if (!pieces.remove(s)) erased += idx to s
                var k = 0
                while (k < keep.size) { if (!keep[k]) { k++; continue }; val st = k; while (k < keep.size && keep[k]) k++
                    if (k - st >= 2) { val np = Stroke(System.nanoTime() + k, s.color, s.width, s.hl, a.copyOfRange(st * 3, k * 3), s.t, s.style); p.strokes.add(np); pieces += np } }
                hit = true; continue
            }
            var j = 0
            while (j < a.size) {
                val dx = a[j] - x; val dy = a[j + 1] - y
                if (dx * dx + dy * dy <= lim) { p.strokes.removeAt(idx); erased += idx to s; hit = true; break }
                j += 3
            }
        }
        if (hit) pcache[p.id]?.valid = false
        invalidate()
    }

    private fun endDraw() {
        removeCallbacks(snap); predN = 0; onInking(false)
        val p = page ?: return
        when (mode) {
            Mode.DRAW -> if (curN >= 3) {
                val s = Stroke(if (curStyle == 11) (System.nanoTime() and 0x0FFFFFFFFFFFFFFFL) or (tapePattern.toLong() shl 60) else System.nanoTime(), if (curHl) hlColor(color) else color, if (curHl) hlWidth else if (curStyle == 11) tapeWidth else penWidth, curHl, if (curStyle == 11) floatArrayOf(cur[0], cur[1], 0.5f, cur[curN - 3], cur[curN - 2], 0.5f) else cur.copyOf(curN), System.currentTimeMillis(), curStyle)
                p.strokes.add(s); push(Op.Add(s))
                pcache[p.id]?.takeIf { it.valid }?.let { val cc = Canvas(it.bmp); cc.scale(it.res, it.res); Ink.drawStroke(cc, s) }
                if (p === pages.lastOrNull()) onNeedPage()
                curN = 0; onChange()
            }
            Mode.ERASE -> { ex = -1f; if (erased.isNotEmpty() || pieces.isNotEmpty()) { push(if (eraserPixel) Op.Replace(ArrayList(erased), ArrayList(pieces)) else Op.Remove(ArrayList(erased))); erased.clear(); pieces.clear(); onChange() } }
            Mode.LASER -> postInvalidateOnAnimation()
            Mode.LASSO -> {
                lasso.close()
                val rg = Region().apply { setPath(lasso, Region(-20000, -20000, 40000, 40000)) }
                selBounds.setEmpty()
                for (s in p.strokes) {
                    var inside = 0; var tot = 0; var j = 0
                    while (j < s.pts.size) { tot++; if (rg.contains(s.pts[j].toInt(), s.pts[j + 1].toInt())) inside++; j += 3 }
                    if (tot > 0 && inside * 2 >= tot) { sel += s; selBounds.union(s.bounds) }
                }
                for (it in p.items) { val b = it.bounds(); if (rg.contains(b.centerX().toInt(), b.centerY().toInt())) { selIt += it; selBounds.union(b) } }
                lasso.reset(); cacheDirty = true; notifyState()
            }
            Mode.TAP -> if (tool == Tool.TEXT && !tapMode) {
                val hit = p.items.lastOrNull { it.kind == 0 && it.bounds().contains(lastX, lastY) }
                if (hit != null) onTextEdit(hit, false) else onTextEdit(Item(System.nanoTime(), 0, lastX, lastY, (Ink.PAGE_W - lastX - 60f).coerceAtLeast(240f), color = color), true)
            } else hitStroke(lastX, lastY)?.let(onStrokeTap)
            Mode.MOVE_SEL -> if (resizing) {
                val it = selIt[0]; val ow = it.w; val oh = it.h; val nw = (ow + selDx).coerceAtLeast(60f); val nh = oh * nw / ow
                val op = Op.Resize(it, ow, oh, nw, nh); apply(op); push(op); selDx = 0f; selDy = 0f; resizing = false
                selBounds.set(it.bounds()); cacheDirty = true; onChange()
            } else if (selDx != 0f || selDy != 0f) {
                val op = Op.Move(ArrayList(sel), ArrayList(selIt), selDx, selDy); apply(op); undos().addLast(op); redos().clear()
                selBounds.offset(selDx, selDy); selDx = 0f; selDy = 0f; notifyState(); onChange()
            }
            else -> {}
        }
        invalidate()
    }

    private fun hitStroke(x: Float, y: Float): Stroke? {
        val r = 24f / scale
        for (s in page!!.strokes.asReversed()) {
            val b = s.bounds; if (x < b.left - r || x > b.right + r || y < b.top - r || y > b.bottom + r) continue
            val lim = (r + s.width / 2).let { it * it }; var j = 0
            while (j < s.pts.size) { val dx = s.pts[j] - x; val dy = s.pts[j + 1] - y; if (dx * dx + dy * dy <= lim) return s; j += 3 }
        }
        return null
    }

    private fun cancelDraw() {
        removeCallbacks(snap); onInking(false)
        if (mode == Mode.ERASE && erased.isNotEmpty()) { push(Op.Remove(ArrayList(erased))); erased.clear(); onChange() }
        curN = 0; lasso.reset(); selDx = 0f; selDy = 0f; ex = -1f; invalidate()
    }

    // ---------- hover / botones del lápiz ----------
    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (!stylusAt(e, 0)) return super.onHoverEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hoverX = e.x; hoverY = e.y; lastPenX = e.x; lastPenY = e.y }
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
        lfx = fx; lfy = fy; clamp(); updateMatrix(); invalidate()
    }
    private fun endNav() {
        vt?.let { v -> v.computeCurrentVelocity(1000); val vx = v.xVelocity; val vy = v.yVelocity
            if (kotlin.math.abs(vy) > 300 || kotlin.math.abs(vx) > 300)
                scroller.fling(tx.toInt(), ty.toInt(), vx.toInt(), vy.toInt(), Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2) }
        mode = Mode.NONE; postInvalidateOnAnimation()
    }

    companion object { const val DESK = 0xFFE9E5DC.toInt() }
}
