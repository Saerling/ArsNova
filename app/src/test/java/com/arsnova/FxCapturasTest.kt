package com.arsnova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.arsnova.fx.BibliotecaFxPantalla
import com.arsnova.fx.Fx
import com.arsnova.fx.FxForzar
import com.arsnova.ui.Ars
import com.arsnova.ui.ArsTheme
import com.arsnova.ui.IdsFxArsNova
import com.arsnova.ui.catalogoArsNova
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Verificación de la biblioteca de animaciones: cada efecto pintado en su pieza real, en build/fx/<id>.png. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FxCapturasTest(private val id: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun ids(): List<Array<Any>> = (IdsFxArsNova + "_biblioteca").map { arrayOf<Any>(it) }
    }
    @get:Rule val rule = createComposeRule()

    @Test fun captura() {
        val app = RuntimeEnvironment.getApplication()
        val p = app.getSharedPreferences("ars", 0)
        val r = com.arsnova.data.Repo(app); com.arsnova.ink.Ink.bgDir = r.dir
        if (r.list().isEmpty()) r.create("Termodinámica", 0xFF2D5BD9.toInt(), com.arsnova.data.Template.GRID, 3)
        Fx.init(app)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ArsTheme(2) {
                if (id == "_biblioteca") BibliotecaFxPantalla(catalogoArsNova(p), {})
                else { val item = catalogoArsNova(p).first { it.id == id }
                    Box(Modifier.fillMaxSize().background(Ars.Bg).padding(16.dp)) { FxForzar(id) { item.vista!!() } } }
            }
        }
        repeat(12) { rule.mainClock.advanceTimeBy(100) }
        rule.onRoot().captureRoboImage("build/fx/$id.png")
    }
}
