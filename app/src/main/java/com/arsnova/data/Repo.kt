package com.arsnova.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class Repo(private val ctx: Context) {
    companion object { /** Carpeta interna de la papelera. */ const val TRASH = "\u0001papelera" }
    val dir = File(ctx.filesDir, "nb").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val indexFile = File(dir, "index.json")

    @Synchronized fun list(): List<NotebookMeta> =
        runCatching { json.decodeFromString<List<NotebookMeta>>(indexFile.readText()) }.getOrDefault(emptyList())
            .sortedByDescending { it.updated }

    fun thumb(id: String) = File(dir, "$id.png")

    /** Copia de seguridad: todos los cuadernos, imágenes, PDF y audios en un .zip. */
    fun exportZip(out: java.io.OutputStream) = java.util.zip.ZipOutputStream(out).use { z ->
        dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".bak") }?.forEach { f ->
            z.putNextEntry(java.util.zip.ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
    }

    /** Restaura una copia: añade sus cuadernos (los que ya existan con el mismo identificador se reemplazan). Devuelve cuántos trae. */
    @Synchronized fun importZip(inp: java.io.InputStream): Int {
        var n = 0; val incoming = ArrayList<NotebookMeta>()
        java.util.zip.ZipInputStream(inp).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val name = File(e.name).name   // solo el nombre: nada puede escribirse fuera de la carpeta
                if (name == "index.json") incoming += runCatching { json.decodeFromString<List<NotebookMeta>>(z.readBytes().decodeToString()) }.getOrDefault(emptyList())
                else if (!e.isDirectory && name.isNotBlank()) { File(dir, name).outputStream().use { z.copyTo(it) }; if (name.endsWith(".json")) n++ }
                e = z.nextEntry
            }
        }
        val ids = incoming.map { it.id }.toSet(); saveIndex(list().filter { it.id !in ids } + incoming); return n
    }

    /** Si el archivo principal está dañado, se abre la copia del guardado anterior. */
    fun load(id: String): Notebook = runCatching { json.decodeFromString<Notebook>(File(dir, "$id.json").readText()) }
        .getOrElse { json.decodeFromString(File(dir, "$id.bak").readText()) }

    fun create(title: String, cover: Int, t: Template, pattern: Int = 0, folder: String = "", paper: Int = -1, spacing: Float = 0f): String {
        val nb = Notebook(UUID.randomUUID().toString(), title, cover, t, System.currentTimeMillis(),
            mutableListOf(Page(System.nanoTime(), t, paper = paper, spacing = spacing)), pattern = pattern, folder = folder)
        write(nb.id, json.encodeToString(nb), meta(nb), null)
        return nb.id
    }

    /** Toma una copia ligera en el hilo de la interfaz (solo listas) y serializa y escribe en segundo plano: guardar no frena la escritura. */
    fun saveAsync(nb: Notebook, thumb: Bitmap?) {
        nb.updated = System.currentTimeMillis()
        val snap = Notebook(nb.id, nb.title, nb.cover, nb.template, nb.updated,
            nb.pages.mapTo(ArrayList()) { Page(it.id, it.template, ArrayList(it.strokes), it.bg, ArrayList(it.items), it.ocr, it.paper, it.spacing, it.lines, it.tex) }, ArrayList(nb.recs), nb.pattern, nb.folder)
        io.execute { runCatching { write(snap.id, json.encodeToString(snap), meta(snap), thumb) } }
    }

    fun rename(id: String, title: String) = io.execute {
        runCatching { val nb = load(id); nb.title = title; write(id, json.encodeToString(nb), meta(nb), null) }
    }

    fun setFolder(id: String, folder: String) = io.execute {
        runCatching { val nb = load(id); nb.folder = folder; write(id, json.encodeToString(nb), meta(nb), null) }
    }

    fun delete(id: String) = io.execute {
        synchronized(this) {
            File(dir, "$id.json").delete(); File(dir, "$id.bak").delete(); File(dir, "$id.tmp").delete(); thumb(id).delete()
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
        val tmp = File(dir, "$id.tmp"); tmp.writeText(s)
        val main = File(dir, "$id.json"); if (main.length() > 0) main.renameTo(File(dir, "$id.bak"))   // el guardado anterior queda de respaldo
        tmp.renameTo(main)
        thumb?.let { b -> thumb(id).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        saveIndex(list().filter { it.id != id } + m)
    }

    private fun saveIndex(l: List<NotebookMeta>) {
        val tmp = File(dir, "index.tmp"); tmp.writeText(json.encodeToString(l)); tmp.renameTo(indexFile)
    }
}
