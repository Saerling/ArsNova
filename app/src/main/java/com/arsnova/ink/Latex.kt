package com.arsnova.ink

import android.graphics.drawable.Drawable
import android.util.LruCache
import ru.noties.jlatexmath.JLatexMathDrawable

/** Fórmulas LaTeX como dibujo vectorial (JLaTeXMath): se ven nítidas a cualquier zoom y salen en el PDF. */
object Latex {
    private val cache = LruCache<String, Drawable>(48)
    @Volatile private var ready = false
    /** Hay que llamarlo una vez al arrancar: la librería carga sus fuentes desde la app. */
    fun init(ctx: android.content.Context) { if (!ready) { ru.noties.jlatexmath.JLatexMathAndroid.init(ctx.applicationContext); ready = true } }
    /** null si la fórmula está incompleta o tiene un error. */
    fun get(tex: String, size: Float, color: Int): Drawable? {
        if (tex.isBlank() || !ready) return null
        val key = "$size|$color|$tex"
        cache.get(key)?.let { return it }
        return runCatching { JLatexMathDrawable.builder(tex).textSize(size).color(color).padding(0).build() as Drawable }.getOrNull()?.also { cache.put(key, it) }
    }
}
