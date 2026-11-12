package com.arsnova

import com.arsnova.ink.OneEuro
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** El estabilizador debe quitar temblor al ir despacio sin dejar el trazo atrás al ir rápido. */
class OneEuroTest {
    /** Devuelve (temblor que queda: desviación alrededor del retraso medio, retraso medio). */
    private fun run(speed: Float, noise: Float, cutoff: Float): Pair<Float, Float> {
        val f = OneEuro(cutoff, 0.012f); val r = java.util.Random(3); val n = 600; val dt = 1f / 240f; val d = ArrayList<Float>()
        for (i in 0 until n) { val truth = speed * i * dt; val out = f.filter(truth + (r.nextFloat() - 0.5f) * 2 * noise, dt); if (i > 120) d += truth - out }
        val mean = d.average().toFloat()
        return Pair(sqrt(d.map { (it - mean) * (it - mean) }.average()).toFloat(), abs(mean))
    }
    @Test fun slowIsSmoothed() {
        val (rms, _) = run(speed = 40f, noise = 1.5f, cutoff = 4f)   // escribir despacio con temblor de ±1.5
        assertTrue("apenas filtra: $rms", rms < 0.4f)                 // el temblor crudo tiene desviación ≈ 0.87
    }
    @Test fun fastDoesNotLag() {
        val (_, lag) = run(speed = 2500f, noise = 0f, cutoff = 4f)    // trazo rápido
        assertTrue("se queda atrás: $lag", lag < 12f)
    }
}
