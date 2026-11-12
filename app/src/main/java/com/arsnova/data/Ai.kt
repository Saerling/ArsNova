package com.arsnova.data

import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/** IA opcional con la clave de API de Claude del usuario (se guarda solo en el dispositivo). */
object Ai {
    /** Las claves de Gemini (gratis en aistudio.google.com/apikey) empiezan por AIza; las de Claude por sk-ant. */
    fun isGemini(key: String) = !key.trim().startsWith("sk-ant")

    private fun gemini(key: String, prompt: String, png: ByteArray?): String {
        val body = buildJsonObject { putJsonArray("contents") { addJsonObject { putJsonArray("parts") {
            if (png != null) addJsonObject { putJsonObject("inline_data") { put("mime_type", "image/png"); put("data", android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)) } }
            addJsonObject { put("text", prompt) } } } } }.toString()
        var last = "sin respuesta"
        for (model in listOf("gemini-flash-latest", "gemini-2.5-flash")) {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 90000
            c.setRequestProperty("content-type", "application/json"); c.setRequestProperty("x-goog-api-key", key.trim())
            c.outputStream.use { it.write(body.toByteArray()) }
            val ok = c.responseCode in 200..299
            val txt = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (ok) return Json.parseToJsonElement(txt).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            last = runCatching { Json.parseToJsonElement(txt).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }.getOrDefault("HTTP ${c.responseCode}")
            if (c.responseCode != 404) break   // 404 = ese nombre de modelo ya no existe: se prueba el siguiente
        }
        error(last)
    }

    fun ask(key: String, prompt: String, png: ByteArray? = null): String {
        if (isGemini(key)) return gemini(key, prompt, png)
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
