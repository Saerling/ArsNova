package com.arsnova.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class Repo(private val ctx: Context) {
    val dir = File(ctx.filesDir, "nb").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val indexFile = File(dir, "index.json")

    @Synchronized fun list(): List<NotebookMeta> =
        runCatching { json.decodeFromString<List<NotebookMeta>>(indexFile.readText()) }.getOrDefault(emptyList())
            .sortedByDescending { it.updated }

    fun thumb(id: String) = File(dir, "$id.png")

    fun load(id: String): Notebook = json.decodeFromString(File(dir, "$id.json").readText())

    fun create(title: String, cover: Int, t: Template, pattern: Int = 0, folder: String = "", paper: Int = -1, spacing: Float = 0f): String {
        val nb = Notebook(UUID.randomUUID().toString(), title, cover, t, System.currentTimeMillis(),
            mutableListOf(Page(System.nanoTime(), t, paper = paper, spacing = spacing)), pattern = pattern, folder = folder)
        write(nb.id, json.encodeToString(nb), meta(nb), null)
        return nb.id
    }

    /** Serializa en el hilo actual (UI, para evitar carreras) y escribe en segundo plano. */
    fun saveAsync(nb: Notebook, thumb: Bitmap?) {
        nb.updated = System.currentTimeMillis()
        val s = json.encodeToString(nb); val m = meta(nb)
        io.execute { write(nb.id, s, m, thumb) }
    }

    fun rename(id: String, title: String) = io.execute {
        runCatching { val nb = load(id); nb.title = title; write(id, json.encodeToString(nb), meta(nb), null) }
    }

    fun setFolder(id: String, folder: String) = io.execute {
        runCatching { val nb = load(id); nb.folder = folder; write(id, json.encodeToString(nb), meta(nb), null) }
    }

    fun delete(id: String) = io.execute {
        synchronized(this) {
            File(dir, "$id.json").delete(); thumb(id).delete()
            dir.listFiles { f -> f.name.startsWith("$id-") }?.forEach { it.delete() }
            saveIndex(list().filter { it.id != id })
        }
    }

    /** Importa un PDF: cada página se rasteriza como fondo anotable. Llamar fuera del hilo UI. */
    fun importPdf(uri: android.net.Uri, title: String, cover: Int): String {
        val id = UUID.randomUUID().toString(); val pages = mutableListOf<Page>()
        ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r ->
                for (i in 0 until r.pageCount) r.openPage(i).use { pg ->
                    val w = 2106; val h = (w.toFloat() * pg.height / pg.width).toInt()
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(android.graphics.Color.WHITE)
                    pg.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val name = "$id-$i.jpg"
                    File(dir, name).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }; b.recycle()
                    pages += Page(System.nanoTime(), Template.BLANK, bg = name)
                }
            }
        }
        val nb = Notebook(id, title, cover, Template.BLANK, System.currentTimeMillis(), pages)
        write(id, json.encodeToString(nb), meta(nb), com.arsnova.ink.Ink.render(pages[0], 0.25f))
        return id
    }

    private fun meta(nb: Notebook) = NotebookMeta(nb.id, nb.title, nb.cover, nb.updated, nb.pages.size,
        nb.pages.joinToString(" ") { p -> p.ocr + " " + p.items.joinToString(" ") { it.text } }.take(6000), nb.pattern, nb.folder)

    @Synchronized private fun write(id: String, s: String, m: NotebookMeta, thumb: Bitmap?) {
        val tmp = File(dir, "$id.tmp"); tmp.writeText(s); tmp.renameTo(File(dir, "$id.json"))
        thumb?.let { b -> thumb(id).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        saveIndex(list().filter { it.id != id } + m)
    }

    private fun saveIndex(l: List<NotebookMeta>) {
        val tmp = File(dir, "index.tmp"); tmp.writeText(json.encodeToString(l)); tmp.renameTo(indexFile)
    }
}
