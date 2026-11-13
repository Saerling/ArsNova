package com.arsnova

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.arsnova.data.Page
import com.arsnova.data.Template
import com.arsnova.data.Tool
import com.arsnova.ink.InkView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Herramientas del lienzo manejadas con trazos de lápiz simulados: formas, cinta, borrador selectivo, lazo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkToolsTest {
    private fun view(): Pair<InkView, Page> {
        val v = InkView(RuntimeEnvironment.getApplication()); val pg = Page(1, Template.BLANK); v.pages = listOf(pg)
        v.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1280, 800)
        v.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1280, 800, android.graphics.Bitmap.Config.ARGB_8888)))
        return v to pg
    }
    private var clock = SystemClock.uptimeMillis()
    private fun ev(v: InkView, action: Int, x: Float, y: Float, t0: Long) {
        val pp = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val pc = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f; size = 0.1f }
        clock += 8; val e = MotionEvent.obtain(t0, clock, action, 1, arrayOf(pp), arrayOf(pc), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0)
        v.onTouchEvent(e); e.recycle()
    }
    /** Trazo de lápiz de (x0,y0) a (x1,y1) en coordenadas de pantalla. */
    private fun drag(v: InkView, x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 14) {
        clock += 50; val t0 = clock; ev(v, MotionEvent.ACTION_DOWN, x0, y0, t0)
        for (i in 1..n) ev(v, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, t0)
        ev(v, MotionEvent.ACTION_UP, x1, y1, t0)
    }
    private fun pen(v: InkView) { v.tool = Tool.PEN; v.penStyle = 0; v.penWidth = 4f; v.shapesOn = false; v.holdErase = false }

    @Test fun shapes() { val (v, pg) = view(); pen(v)
        for (sh in 1..6) { v.shape = sh; drag(v, 400f, 200f, 620f, 380f) }
        assertEquals("una forma por arrastre", 6, pg.strokes.size)
        val rect = pg.strokes[2]; val b = rect.bounds
        assertTrue("el rectángulo cierra donde empezó", kotlin.math.abs(rect.pts[0] - rect.pts[rect.pts.size - 3]) < 0.5f && kotlin.math.abs(rect.pts[1] - rect.pts[rect.pts.size - 2]) < 0.5f)
        assertTrue("y ocupa el arrastre, no una diagonal fina: ${b.width()}×${b.height()}", b.width() > 20f && b.height() > 20f)
        val ell = pg.strokes[3]; assertTrue("la elipse toca los cuatro lados", kotlin.math.abs(ell.bounds.width() - b.width()) < 2f && kotlin.math.abs(ell.bounds.height() - b.height()) < 2f)
        v.shape = 0; drag(v, 400f, 500f, 620f, 560f); assertEquals(7, pg.strokes.size)
        v.undo(); assertEquals(6, pg.strokes.size)
    }

    @Test fun tapeModes() { val (v, pg) = view(); v.tool = Tool.TAPE; v.tapeWidth = 40f
        v.tapeMode = 0; drag(v, 400f, 200f, 620f, 260f); assertEquals("recta: dos puntos", 6, pg.strokes[0].pts.size)
        v.tapeAxis = true; drag(v, 400f, 300f, 620f, 330f); assertEquals("solo horizontal", pg.strokes[1].pts[1], pg.strokes[1].pts[4], 0.01f); v.tapeAxis = false
        v.tapeMode = 1; drag(v, 400f, 400f, 620f, 460f); assertTrue("libre: sigue el trazo", pg.strokes[2].pts.size > 6)
        v.tapeMode = 2; drag(v, 400f, 500f, 620f, 600f); val r = pg.strokes[3]
        assertEquals("rectángulo: horizontal", r.pts[1], r.pts[4], 0.01f); assertTrue("con el alto del arrastre (${r.width}) y no el grosor de la cinta", r.width > 41f)
        assertTrue(pg.strokes.all { it.style == 11 })
        v.tapeMode = 0; drag(v, 500f, 230f, 500f, 230f, 1); assertEquals("un toque no crea cinta", 4, pg.strokes.size); assertTrue("la destapa", pg.strokes[0].hid)
        v.tapesAll(true); assertTrue(pg.strokes.all { it.hid }); v.tapesAll(false); assertTrue(pg.strokes.none { it.hid })
        v.removeTapes(); assertEquals(0, pg.strokes.size); v.undo(); assertEquals("deshacer devuelve las cintas en su orden", 4, pg.strokes.size); assertTrue(pg.strokes[3].width > 41f)
    }

    @Test fun eraserOnlyAndClear() { val (v, pg) = view(); pen(v)
        drag(v, 400f, 300f, 700f, 300f)
        v.tool = Tool.HIGHLIGHTER; v.hlWidth = 24f; drag(v, 400f, 300f, 700f, 300f)
        v.tool = Tool.TAPE; v.tapeWidth = 40f; drag(v, 400f, 300f, 700f, 300f)
        assertEquals(3, pg.strokes.size)
        var back = 0; v.onErased = { back++ }
        v.tool = Tool.ERASER; v.eraserRadius = 16f; v.eraserPixel = false
        v.eraseOnly = 1; drag(v, 550f, 250f, 550f, 350f); assertEquals("solo resaltador", 2, pg.strokes.size); assertTrue(pg.strokes.none { it.hl })
        v.eraseOnly = 2; drag(v, 550f, 250f, 550f, 350f); assertEquals("solo cinta (en medio de sus dos extremos)", 1, pg.strokes.size); assertTrue(pg.strokes.none { it.style == 11 })
        v.eraseOnly = 0; v.undo(); v.undo(); assertEquals(3, pg.strokes.size)
        org.robolectric.shadows.ShadowLooper.idleMainLooper(); assertEquals("avisa para volver al lápiz", 2, back)
        v.clearPage(); assertEquals(0, pg.strokes.size); v.undo(); assertEquals(3, pg.strokes.size)
    }

    @Test fun lassoRectAndFilter() { val (v, pg) = view(); pen(v)
        drag(v, 400f, 300f, 700f, 300f); v.tool = Tool.HIGHLIGHTER; v.hlWidth = 24f; drag(v, 400f, 340f, 700f, 340f)
        v.tool = Tool.LASSO; v.lassoRect = true; v.lassoMask = 15
        var selected = false; v.onState = { _, _, s -> selected = s }
        drag(v, 350f, 250f, 750f, 400f); assertTrue("el rectángulo selecciona lo que encierra", selected)
        v.deleteSelection(); assertEquals(0, pg.strokes.size); v.undo(); assertEquals(2, pg.strokes.size)
        v.lassoMask = 2; drag(v, 350f, 250f, 750f, 400f); v.deleteSelection(); assertEquals("con el filtro solo cae el resaltador", 1, pg.strokes.size); assertTrue(!pg.strokes[0].hl)
    }
}
