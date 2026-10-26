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

enum class Tool { PEN, HIGHLIGHTER, ERASER, LASSO, TEXT }

@Serializable enum class Template { BLANK, LINED, GRID, DOTS }

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
    val width: Float,
    val hl: Boolean = false,
    @Serializable(with = PtsSerializer::class) var pts: FloatArray,
    val t: Long = 0,
) {
    @Transient var path: Path? = null
    @Transient private var b: RectF? = null
    val bounds: RectF
        get() = b ?: RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE).also { r ->
            var i = 0
            while (i < pts.size) { r.union(pts[i], pts[i + 1]); i += 3 }
            r.inset(-width, -width); b = r
        }

    fun offset(dx: Float, dy: Float) {
        var i = 0
        while (i < pts.size) { pts[i] += dx; pts[i + 1] += dy; i += 3 }
        path?.offset(dx, dy); b?.offset(dx, dy)
    }
}

@Serializable
class Page(val id: Long, var template: Template, val strokes: MutableList<Stroke> = mutableListOf(), var bg: String? = null,
    val items: MutableList<Item> = mutableListOf(), var ocr: String = "")

/** Elemento no-tinta: 0 = texto, 1 = imagen. */
@Serializable
class Item(val id: Long, val kind: Int, var x: Float, var y: Float, var w: Float, var h: Float = 0f,
    var text: String = "", val file: String? = null, var size: Float = 40f, var color: Int = 0xFF1D1B18.toInt()) {
    fun bounds() = RectF(x, y, x + w, y + h)
    fun offset(dx: Float, dy: Float) { x += dx; y += dy }
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
)

@Serializable
class Rec(val file: String, val start: Long, var dur: Long = 0)

@Serializable
data class NotebookMeta(val id: String, val title: String, val cover: Int, val updated: Long, val pages: Int, val text: String = "")
