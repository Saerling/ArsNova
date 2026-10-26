package com.arsnova.data

import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/** IA opcional con la clave de API de Claude del usuario (se guarda solo en el dispositivo). */
object Ai {
    fun ask(key: String, prompt: String): String {
        val body = buildJsonObject {
            put("model", "claude-haiku-5-5"); put("max_tokens", 1500)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", prompt) } }
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

    fun studyPrompt(text: String) = """Eres un asistente de estudio. A partir de estas notas manuscritas (transcritas, pueden tener errores de reconocimiento), escribe en español:
1) Un resumen breve con las ideas clave en viñetas.
2) 5 tarjetas de estudio en formato "P: … / R: …".
Notas:
$text"""
}
