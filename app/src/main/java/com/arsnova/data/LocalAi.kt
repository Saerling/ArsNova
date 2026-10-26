package com.arsnova.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** IA en el dispositivo: Qwen2.5 0.5B (q8, ~547 MB) con MediaPipe LLM Inference. Sin internet tras la descarga. */
object LocalAi {
    const val NAME = "Qwen2.5 0.5B Instruct"
    private const val URL_MODEL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    private var llm: LlmInference? = null

    fun file(ctx: Context) = File(File(ctx.filesDir, "models").apply { mkdirs() }, "qwen05.task")
    fun ready(ctx: Context) = file(ctx).let { it.exists() && it.length() > 100_000_000 }

    fun download(ctx: Context, progress: (Float) -> Unit) {
        val tmp = File(file(ctx).path + ".part")
        var url = URL(URL_MODEL); var c: HttpURLConnection
        while (true) { c = url.openConnection() as HttpURLConnection; c.instanceFollowRedirects = false
            if (c.responseCode in 300..399) url = URL(url, c.getHeaderField("Location")) else break }
        val total = c.contentLengthLong.coerceAtLeast(1)
        c.inputStream.use { i -> tmp.outputStream().use { o ->
            val b = ByteArray(256 * 1024); var n: Int; var got = 0L
            while (i.read(b).also { n = it } > 0) { o.write(b, 0, n); got += n; progress(got.toFloat() / total) }
        } }
        tmp.renameTo(file(ctx))
    }

    fun delete(ctx: Context) { llm?.close(); llm = null; file(ctx).delete() }

    @Synchronized fun generate(ctx: Context, prompt: String): String {
        val m = llm ?: LlmInference.createFromOptions(ctx, LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file(ctx).path).setMaxTokens(1280).build()).also { llm = it }
        return m.generateResponse("<|im_start|>system\nEres un asistente de estudio conciso. Responde en español.<|im_end|>\n<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n").trim()
    }

    fun studyPrompt(text: String) = "Resume en viñetas las ideas clave de estas notas y luego escribe 4 preguntas de estudio con su respuesta (P:/R:).\nNotas:\n" + text.take(1800)
}
