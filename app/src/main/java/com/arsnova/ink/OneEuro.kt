package com.arsnova.ink

import kotlin.math.PI
import kotlin.math.abs

/**
 * Filtro "1 €" (Casiez, Roussel y Vogel, CHI 2012; algoritmo público): un paso bajo cuya frecuencia de corte sube con
 * la velocidad. Despacio filtra fuerte (quita el temblor); rápido casi no filtra (no deja el trazo atrás). Es lo que
 * hace que la estabilización se sienta natural al escribir, a diferencia de un promedio fijo.
 */
class OneEuro(var minCutoff: Float, var beta: Float, private val dCutoff: Float = 1f) {
    private var x = 0f; private var dx = 0f; private var has = false
    fun reset() { has = false; dx = 0f }
    private fun alpha(cutoff: Float, dt: Float) = 1f / (1f + (1f / (2f * PI.toFloat() * cutoff)) / dt)
    fun filter(v: Float, dt: Float): Float {
        if (!has) { has = true; x = v; return v }
        val d = (v - x) / dt
        dx += alpha(dCutoff, dt) * (d - dx)
        x += alpha(minCutoff + beta * abs(dx), dt) * (v - x)
        return x
    }
}
