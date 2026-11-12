package com.arsnova.data

import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Tool { PEN, HIGHLIGHTER, ERASER, LASSO, TEXT, TAPE, LASER }

@Serializable enum class Template { BLANK, LINED, GRID, DOTS, LINED_N, LINED_W, GRID_S, GRID_L, MARGIN, CORNELL, TODO, WEEKLY, MUSIC, HANDWRITING }

/** Puntos empaquetados x,y,presión -> base64 binario (≈4x más compacto que JSON numérico). */
object PtsSerializer : KSerializer<FloatArray> {
    override val descriptor = PrimitiveSerialDescriptor("pts", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: FloatArray) {
        val bb = ByteBuffer.allocate(value.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asFloatBuffer().put(value)
        encoder.encodeString(Base64.encodeToString(bb.array(), Base64.NO_WRAP))
    }
    override fun deserialize(decoder: Decoder): FloatArray {
        val fb = ByteBuffer.wrap(Base64.decode(decoder.decodeString(), Base64.NO_WRAP))
            .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }
}

@Serializable
class Stroke(
    val id: Long,
    var color: Int,
    var width: Float,
    val hl: Boolean = false,
    @Serializable(with = PtsSerializer::class) var pts: FloatArray,
    val t: Long = 0,
    val style: Int = 0,
    var hid: Boolean = false,
) {
    @Transient var path: Path? = null
    @Transient private var b: RectF? = null
    val bounds: RectF
        get() = b ?: RectF().also { r ->
            // mínimos y máximos a mano: RectF.union(x, y) sobre un rectángulo vacío falla cuando el trazo solo avanza
            // hacia la izquierda o hacia arriba (una línea recta, una cinta), y el trazo quedaba "sin sitio":
            // no se podía borrar y desaparecía al repintar la zona.
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var rr = -Float.MAX_VALUE; var bb = -Float.MAX_VALUE; var i = 0
            while (i + 1 < pts.size) { val x = pts[i]; val y = pts[i + 1]; if (x < l) l = x; if (x > rr) rr = x; if (y < t) t = y; if (y > bb) bb = y; i += 3 }
            if (l > rr) { l = 0f; rr = 0f; t = 0f; bb = 0f }
            val m = (if (style == 8 || style == 9) width * 2.4f else width).coerceAtLeast(1f)
            r.set(l - m, t - m, rr + m, bb + m); b = r
        }

    fun offset(dx: Float, dy: Float) {
        var i = 0
        while (i < pts.size) { pts[i] += dx; pts[i + 1] += dy; i += 3 }
        path?.offset(dx, dy); b?.offset(dx, dy)
    }

    /** Escala el trazo (puntos y grosor) alrededor de (ox, oy). */
    fun scale(ox: Float, oy: Float, f: Float) {
        var i = 0
        while (i < pts.size) { pts[i] = ox + (pts[i] - ox) * f; pts[i + 1] = oy + (pts[i + 1] - oy) * f; i += 3 }
        width *= f; path = null; b = null
    }
}

@Serializable
class Page(val id: Long, var template: Template, val strokes: MutableList<Stroke> = mutableListOf(), var bg: String? = null,
    val items: MutableList<Item> = mutableListOf(), var ocr: String = "", var paper: Int = -1, var spacing: Float = 0f,
    /** Renglones reconocidos (texto y dónde están) para buscar dentro de la nota. */
    var lines: List<OcrLine> = emptyList(),
    /** Versión LaTeX de la página. */
    var tex: String = "") {
    fun next() = Page(System.nanoTime(), template, paper = paper, spacing = spacing)
}

@Serializable
class OcrLine(val text: String, val l: Float, val t: Float, val r: Float, val b: Float)

/** Elemento no-tinta: 2 = fórmula LaTeX; 0 = texto, 1 = imagen. */
@Serializable
class Item(val id: Long, val kind: Int, var x: Float, var y: Float, var w: Float, var h: Float = 0f,
    var text: String = "", val file: String? = null, var size: Float = 40f, var color: Int = 0xFF1D1B18.toInt(), var align: Int = 0, var bold: Boolean = false) {
    fun bounds() = RectF(x, y, x + w, y + h)
    fun offset(dx: Float, dy: Float) { x += dx; y += dy }
    fun scale(ox: Float, oy: Float, f: Float) { x = ox + (x - ox) * f; y = oy + (y - oy) * f; w *= f; h *= f; size *= f }
}

@Serializable
class Notebook(
    val id: String,
    var title: String,
    var cover: Int,
    var template: Template,
    var updated: Long,
    val pages: MutableList<Page>,
    val recs: MutableList<Rec> = mutableListOf(),
    var pattern: Int = 0,
    var folder: String = "",
)

@Serializable
class Rec(val file: String, val start: Long, var dur: Long = 0)

@Serializable
data class NotebookMeta(val id: String, val title: String, val cover: Int, val updated: Long, val pages: Int, val text: String = "", val pattern: Int = 0, val folder: String = "")
