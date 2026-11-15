package com.arsnova.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class Repo(private val ctx: Context) {
    companion object { /** Carpeta interna de la papelera. */ const val TRASH = "\u0001papelera" }
    val dir = File(ctx.filesDir, "nb").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val indexFile = File(dir, "index.json")

    /** Aviso (en el hilo de guardado) cuando un guardado falla: sin esto el error se perdía en silencio. */
    @Volatile var onSaveError: ((Throwable) -> Unit)? = null

    @Synchronized fun list(): List<NotebookMeta> =
        (runCatching { json.decodeFromString<List<NotebookMeta>>(indexFile.readText()) }.getOrNull() ?: rebuildIndex())
            .sortedByDescending { it.updated }

    /**
     * Índice perdido o dañado: se reconstruye leyendo los cuadernos que hay en disco. Antes se tomaba como
     * "biblioteca vacía" y el siguiente guardado dejaba el índice con un solo cuaderno (los demás desaparecían de la lista).
     */
    private fun rebuildIndex(): List<NotebookMeta> {
        val ids = dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".bak")) && !it.name.startsWith("index") }
            ?.map { it.name.substringBeforeLast('.') }?.distinct() ?: return emptyList()
        val l = ids.mapNotNull { id -> runCatching { meta(load(id)) }.getOrNull() }
        if (l.isNotEmpty()) runCatching { saveIndex(l) }
        return l
    }

    fun thumb(id: String) = File(dir, "$id.png")

    /** Copia de seguridad: todos los cuadernos, imágenes, PDF y audios en un .zip. */
    fun exportZip(out: java.io.OutputStream) = java.util.zip.ZipOutputStream(out).use { z ->
        dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".bak") }?.forEach { f ->
            z.putNextEntry(java.util.zip.ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
    }

    /** Restaura una copia: añade sus cuadernos (los que ya existan con el mismo identificador se reemplazan). Devuelve cuántos trae. */
    @Synchronized fun importZip(inp: java.io.InputStream): Int {
        var n = 0; var total = 0L; val incoming = ArrayList<NotebookMeta>()
        java.util.zip.ZipInputStream(inp).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val name = File(e.name).name   // solo el nombre: nada puede escribirse fuera de la carpeta
                if (name == "index.json") incoming += runCatching { json.decodeFromString<List<NotebookMeta>>(z.readBytes().decodeToString()) }.getOrDefault(emptyList())
                else if (!e.isDirectory && name.isNotBlank() && !name.startsWith(".") && name != "index.tmp") {
                    // Tope de 512 MB por archivo y 4 GB por copia: un .zip manipulado no puede llenar el almacenamiento.
                    val f = File(dir, name); var escritos = 0L
                    f.outputStream().use { o -> val buf = ByteArray(64 * 1024); while (true) { val r = z.read(buf); if (r < 0) break; escritos += r; total += r
                        if (escritos > 512L shl 20 || total > 4L shl 30) { o.close(); f.delete(); error("La copia es demasiado grande o está dañada") }
                        o.write(buf, 0, r) } }
                    if (name.endsWith(".json")) n++
                }
                e = z.nextEntry
            }
        }
        val ids = incoming.map { it.id }.toSet(); saveIndex(list().filter { it.id !in ids } + incoming); return n
    }

    /** Si el archivo principal está dañado, se abre la copia del guardado anterior. */
    fun load(id: String): Notebook = runCatching { read(File(dir, "$id.json")) }.getOrElse { read(File(dir, "$id.bak")) }

    /** Lee por flujo (sin cargar el archivo entero como texto): un cuaderno grande ocupa la mitad de memoria al abrir. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun read(f: File): Notebook = f.inputStream().buffered(1 shl 16).use { json.decodeFromStream<Notebook>(it) }

    fun create(title: String, cover: Int, t: Template, pattern: Int = 0, folder: String = "", paper: Int = -1, spacing: Float = 0f): String {
        val nb = Notebook(UUID.randomUUID().toString(), title, cover, t, System.currentTimeMillis(),
            mutableListOf(Page(System.nanoTime(), t, paper = paper, spacing = spacing)), pattern = pattern, folder = folder)
        write(nb, null)
        return nb.id
    }

    /** Toma una copia ligera en el hilo de la interfaz (solo listas) y serializa y escribe en segundo plano: guardar no frena la escritura. */
    fun saveAsync(nb: Notebook, thumb: Bitmap?) {
        nb.updated = System.currentTimeMillis()
        val snap = Notebook(nb.id, nb.title, nb.cover, nb.template, nb.updated,
            nb.pages.mapTo(ArrayList()) { Page(it.id, it.template, ArrayList(it.strokes), it.bg, ArrayList(it.items), it.ocr, it.paper, it.spacing, it.lines, it.tex) }, ArrayList(nb.recs), nb.pattern, nb.folder)
        io.execute { try { write(snap, thumb) } catch (e: Throwable) { onSaveError?.invoke(e) } }
    }

    /** Espera a que terminen los guardados pendientes (al salir de la app o en pruebas). */
    fun flush(ms: Long = 4000) { runCatching { io.submit {}.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS) } }

    fun rename(id: String, title: String) = io.execute {
        runCatching { val nb = load(id); nb.title = title; write(nb, null) }
    }

    fun setFolder(id: String, folder: String) = io.execute {
        runCatching { val nb = load(id); nb.folder = folder; write(nb, null) }
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
                if (r.pageCount == 0) error("El PDF no tiene páginas")
                for (i in 0 until r.pageCount) r.openPage(i).use { pg ->
                    val w = 2106; val h = (w.toFloat() * pg.height / pg.width).toInt().coerceIn(1, 6000)
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(android.graphics.Color.WHITE)
                    pg.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val name = "$id-$i.jpg"
                    File(dir, name).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }; b.recycle()
                    pages += Page(System.nanoTime(), Template.BLANK, bg = name)
                }
            }
        }
        val nb = Notebook(id, title, cover, Template.BLANK, System.currentTimeMillis(), pages)
        write(nb, com.arsnova.ink.Ink.render(pages[0], 0.25f))
        return id
    }

    private fun meta(nb: Notebook) = NotebookMeta(nb.id, nb.title, nb.cover, nb.updated, nb.pages.size,
        nb.pages.joinToString(" ") { p -> p.ocr + " " + p.items.joinToString(" ") { it.text } }.take(6000), nb.pattern, nb.folder)

    /**
     * Guardado atómico: se escribe a un temporal, se fuerza a disco (fsync) y solo entonces sustituye al archivo
     * principal; el guardado anterior queda como .bak. Si el proceso muere a mitad, queda el principal o el respaldo, nunca un archivo a medias.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Synchronized private fun write(nb: Notebook, thumb: Bitmap?) {
        val id = nb.id; val tmp = File(dir, "$id.tmp")
        java.io.FileOutputStream(tmp).use { fo -> val bo = fo.buffered(1 shl 16); json.encodeToStream(nb, bo); bo.flush(); fo.fd.sync() }
        if (tmp.length() < 2L) { tmp.delete(); error("No se pudo escribir el cuaderno") }
        val main = File(dir, "$id.json"); val bak = File(dir, "$id.bak")
        // el respaldo solo se pisa con un archivo que se puede leer: un principal dañado no borra el último respaldo bueno
        if (main.length() > 0 && (!bak.exists() || legible(main))) main.renameTo(bak)
        if (!tmp.renameTo(main)) { main.delete(); if (!tmp.renameTo(main)) error("No se pudo guardar el cuaderno") }
        thumb?.let { b -> runCatching { val t = File(dir, "$id.png.tmp"); t.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) }; t.renameTo(thumb(id)) } }
        saveIndex(list().filter { it.id != id } + meta(nb))
    }

    /** Comprobación barata de que un cuaderno guardado está entero (termina cerrando el objeto JSON). */
    private fun legible(f: File): Boolean = runCatching {
        java.io.RandomAccessFile(f, "r").use { r -> if (r.length() < 2) return false; r.seek(r.length() - 1); var c = r.read(); var back = 2L
            while ((c == 10 || c == 13 || c == 32) && back <= r.length()) { r.seek(r.length() - back); c = r.read(); back++ }; c == '}'.code }
    }.getOrDefault(false)

    private fun saveIndex(l: List<NotebookMeta>) {
        val tmp = File(dir, "index.tmp")
        java.io.FileOutputStream(tmp).use { fo -> fo.write(json.encodeToString(l).toByteArray()); fo.fd.sync() }
        if (!tmp.renameTo(indexFile)) { indexFile.delete(); tmp.renameTo(indexFile) }
    }
}
