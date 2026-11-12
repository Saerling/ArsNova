package com.arsnova.ink

import com.arsnova.data.Stroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import kotlinx.coroutines.tasks.await
import com.google.mlkit.vision.digitalink.Ink as MlInk

/** Reconocimiento de escritura a mano en el dispositivo (ML Kit, sin Google Play Services). */
object Recognizer {
    private val model by lazy {
        DigitalInkRecognitionModel.builder(DigitalInkRecognitionModelIdentifier.fromLanguageTag("es")!!).build()
    }
    private val client by lazy { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build()) }
    private var ready = false
    /** Por qué no está disponible el reconocimiento (null = sin problema conocido). */
    @Volatile var error: String? = null

    /** Descarga el modelo la primera vez (~20 MB); después funciona sin internet. */
    suspend fun ensure(): Boolean = try {
        if (!ready) {
            val mm = RemoteModelManager.getInstance()
            if (!mm.isModelDownloaded(model).await()) mm.download(model, DownloadConditions.Builder().build()).await()
            ready = true
        }
        error = null; true
    } catch (e: Throwable) { error = e.message ?: e.javaClass.simpleName; false }

    /** Agrupa la tinta en renglones (por solape vertical) y reconoce cada uno, con su caja en la página. */
    suspend fun lines(strokes: List<Stroke>): List<com.arsnova.data.OcrLine> {
        val ink = strokes.filter { !it.hl && it.style != 11 && it.pts.size >= 6 }
        if (ink.isEmpty() || !ensure()) return emptyList()
        val rows = ArrayList<Pair<android.graphics.RectF, MutableList<Stroke>>>()
        for (s in ink.sortedBy { it.bounds.centerY() }) {
            val cy = s.bounds.centerY(); val row = rows.lastOrNull()
            if (row != null && cy < row.first.bottom + 10f && maxOf(row.first.bottom, s.bounds.bottom) - row.first.top < 150f) { row.first.union(s.bounds); row.second += s }
            else rows += android.graphics.RectF(s.bounds) to mutableListOf(s)
        }
        return rows.mapNotNull { (box, line) ->
            val b = MlInk.builder()
            for (s in line.sortedBy { it.bounds.left }) {
                val sb = MlInk.Stroke.builder(); var j = 0
                while (j < s.pts.size) { sb.addPoint(MlInk.Point.create(s.pts[j], s.pts[j + 1])); j += 3 }
                b.addStroke(sb.build())
            }
            runCatching { client.recognize(b.build()).await().candidates.firstOrNull()?.text }.getOrNull()?.takeIf { it.isNotBlank() }?.let { com.arsnova.data.OcrLine(it, box.left, box.top, box.right, box.bottom) }
        }
    }

    suspend fun recognize(strokes: List<Stroke>): String = lines(strokes).joinToString("\n") { it.text }
}
