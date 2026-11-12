package com.arsnova.data

import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/** IA opcional con la clave de API de Claude del usuario (se guarda solo en el dispositivo). */
object Ai {
    fun ask(key: String, prompt: String, png: ByteArray? = null): String {
        val body = buildJsonObject {
            put("model", "claude-haiku-5-5"); put("max_tokens", 4000)
            putJsonArray("messages") { addJsonObject { put("role", "user")
                if (png == null) put("content", prompt) else putJsonArray("content") {
                    addJsonObject { put("type", "image"); putJsonObject("source") { put("type", "base64"); put("media_type", "image/png"); put("data", android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)) } }
                    addJsonObject { put("type", "text"); put("text", prompt) } } } }
        }.toString()
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("content-type", "application/json"); c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toByteArray()) }
        val ok = c.responseCode in 200..299
        val txt = (if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (!ok) error(Json.parseToJsonElement(txt).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "HTTP ${c.responseCode}")
        return Json.parseToJsonElement(txt).jsonObject["content"]!!.jsonArray.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
    }

    /** Escritura a mano (imagen) → LaTeX. */
    fun latex(key: String, png: ByteArray): String = ask(key, "Transcribe la expresión matemática manuscrita de la imagen a LaTeX. Responde solo con el código LaTeX, sin signos de dólar, sin explicaciones y sin bloques de código.", png)
        .trim().removePrefix("```latex").removePrefix("```").removeSuffix("```").trim().trim('$').trim()

    /** Página de apuntes (imagen) → documento LaTeX completo. */
    fun latexDoc(key: String, png: ByteArray): String = ask(key, "Convierte esta página de apuntes manuscritos en un documento LaTeX completo y compilable (article, con amsmath): conserva títulos, texto y ecuaciones en su orden. Responde solo con el código LaTeX, sin bloques de código.", png)
        .trim().removePrefix("```latex").removePrefix("```").removeSuffix("```").trim()

    fun studyPrompt(text: String) = """Eres un asistente de estudio. A partir de estas notas manuscritas (transcritas, pueden tener errores de reconocimiento), escribe en español:
1) Un resumen breve con las ideas clave en viñetas.
2) 5 tarjetas de estudio en formato "P: … / R: …".
Notas:
$text"""
}
