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

    suspend fun ensure(): Boolean = runCatching {
        if (!ready) {
            val mm = RemoteModelManager.getInstance()
            if (!mm.isModelDownloaded(model).await()) mm.download(model, DownloadConditions.Builder().build()).await()
            ready = true
        }
        true
    }.getOrDefault(false)

    /** Agrupa los trazos en renglones por altura y reconoce cada uno. */
    suspend fun recognize(strokes: List<Stroke>): String {
        val ink = strokes.filter { !it.hl && it.pts.size >= 6 }
        if (ink.isEmpty() || !ensure()) return ""
        val lines = ArrayList<MutableList<Stroke>>()
        for (s in ink.sortedBy { it.bounds.centerY() }) {
            val l = lines.lastOrNull()
            if (l != null && kotlin.math.abs(l.last().bounds.centerY() - s.bounds.centerY()) < 48f) l += s else lines += mutableListOf(s)
        }
        return lines.mapNotNull { line ->
            val b = MlInk.builder()
            for (s in line.sortedBy { it.bounds.left }) {
                val sb = MlInk.Stroke.builder(); var j = 0
                while (j < s.pts.size) { sb.addPoint(MlInk.Point.create(s.pts[j], s.pts[j + 1])); j += 3 }
                b.addStroke(sb.build())
            }
            runCatching { client.recognize(b.build()).await().candidates.firstOrNull()?.text }.getOrNull()
        }.joinToString("\n")
    }
}
