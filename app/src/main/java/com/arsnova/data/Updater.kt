package com.arsnova.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Actualización desde la propia app usando los Releases de GitHub (misma firma → se instala encima). */
object Updater {
    const val REPO = "Saerling/ArsNova"
    data class Rel(val code: Int, val tag: String, val url: String, val notes: String)

    fun current(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
    }

    fun latest(): Rel? = runCatching {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json"); c.connectTimeout = 10000
        val o = Json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
        val tag = o["tag_name"]!!.jsonPrimitive.content
        val asset = o["assets"]!!.jsonArray.first { it.jsonObject["name"]!!.jsonPrimitive.content.endsWith(".apk") }.jsonObject
        Rel(tag.substringAfterLast('.').toInt(), tag, asset["browser_download_url"]!!.jsonPrimitive.content, o["body"]?.jsonPrimitive?.contentOrNull ?: "")
    }.getOrNull()

    fun download(ctx: Context, r: Rel, progress: (Float) -> Unit): File {
        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "ArsNova-update.apk")
        var url = URL(r.url); var c: HttpURLConnection
        while (true) { c = url.openConnection() as HttpURLConnection; c.instanceFollowRedirects = false
            if (c.responseCode in 300..399) url = URL(c.getHeaderField("Location")) else break }
        val total = c.contentLengthLong.coerceAtLeast(1)
        c.inputStream.use { inp -> f.outputStream().use { out ->
            val buf = ByteArray(64 * 1024); var n: Int; var got = 0L
            while (inp.read(buf).also { n = it } > 0) { out.write(buf, 0, n); got += n; progress(got.toFloat() / total) }
        } }
        return f
    }

    fun install(ctx: Context, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        val uri = FileProvider.getUriForFile(ctx, "com.arsnova.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
