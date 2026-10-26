package com.arsnova.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class Repo(ctx: Context) {
    private val dir = File(ctx.filesDir, "nb").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val indexFile = File(dir, "index.json")

    @Synchronized fun list(): List<NotebookMeta> =
        runCatching { json.decodeFromString<List<NotebookMeta>>(indexFile.readText()) }.getOrDefault(emptyList())
            .sortedByDescending { it.updated }

    fun thumb(id: String) = File(dir, "$id.png")

    fun load(id: String): Notebook = json.decodeFromString(File(dir, "$id.json").readText())

    fun create(title: String, cover: Int, t: Template): String {
        val nb = Notebook(UUID.randomUUID().toString(), title, cover, t, System.currentTimeMillis(),
            mutableListOf(Page(System.nanoTime(), t)))
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

    fun delete(id: String) = io.execute {
        synchronized(this) {
            File(dir, "$id.json").delete(); thumb(id).delete()
            saveIndex(list().filter { it.id != id })
        }
    }

    private fun meta(nb: Notebook) = NotebookMeta(nb.id, nb.title, nb.cover, nb.updated, nb.pages.size)

    @Synchronized private fun write(id: String, s: String, m: NotebookMeta, thumb: Bitmap?) {
        val tmp = File(dir, "$id.tmp"); tmp.writeText(s); tmp.renameTo(File(dir, "$id.json"))
        thumb?.let { b -> thumb(id).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        saveIndex(list().filter { it.id != id } + m)
    }

    private fun saveIndex(l: List<NotebookMeta>) {
        val tmp = File(dir, "index.tmp"); tmp.writeText(json.encodeToString(l)); tmp.renameTo(indexFile)
    }
}
