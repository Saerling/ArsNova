package com.arsnova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import com.arsnova.data.Repo
import com.arsnova.data.Stroke
import com.arsnova.data.Template
import com.arsnova.ui.ArsTheme
import com.arsnova.ui.EditorScreen
import com.arsnova.ui.LibraryScreen
import com.arsnova.ui.SettingsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Capturas de las pantallas (tablet apaisada) en build/shots para revisar el diseño a ojo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun repo(): Pair<Repo, String> {
        val r = Repo(RuntimeEnvironment.getApplication()); com.arsnova.ink.Ink.bgDir = r.dir
        val id = r.create("Termodinámica", 0xFF2D5BD9.toInt(), Template.GRID, 0)
        r.create("Balance de materia", 0xFF1C1C1E.toInt(), Template.LINED, 4); r.create("Ideas", 0xFF1F8A5B.toInt(), Template.DOTS, 1)
        val nb = r.load(id)
        repeat(5) { k -> val n = 160; val a = FloatArray(n * 3)
            for (i in 0 until n) { val t = i / (n - 1f); val w = t * 7 * PI.toFloat(); a[3 * i] = 150 + t * 900 + 26 * sin(w); a[3 * i + 1] = 300f + k * 130 + 30 * cos(w); a[3 * i + 2] = 0.3f + 0.5f * t }
            nb.pages[0].strokes += Stroke(k.toLong() + 1, listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFFD9481F, 0xFF1F8A5B, 0xFF8E44AD)[k].toInt(), 6f, pts = a, style = k) }
        r.saveAsync(nb, com.arsnova.ink.Ink.render(nb.pages[0], 0.25f)); Thread.sleep(600)
        return r to id
    }
    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("build/shots/$name.png") }

    @Test fun libraryLight() { val (r, _) = repo(); rule.setContent { ArsTheme(1) { LibraryScreen(r, {}) {} } }; shot("library-light") }
    @Test fun libraryDark() { val (r, _) = repo(); rule.setContent { ArsTheme(2) { LibraryScreen(r, {}) {} } }; shot("library-dark") }
    @Test fun editorLight() { val (r, id) = repo(); rule.setContent { ArsTheme(1) { EditorScreen(r, id) {} } }; shot("editor-light") }
    @Test fun editorDark() { val (r, id) = repo(); rule.setContent { ArsTheme(2) { EditorScreen(r, id) {} } }; shot("editor-dark") }
    @Test fun settingsDark() { rule.setContent { ArsTheme(2) { SettingsScreen(2, {}) {} } }; shot("settings-dark") }

    @Test fun wheel() { rule.setContent { ArsTheme(2) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.arsnova.ui.Ars.Bg)) {
        com.arsnova.ui.ToolWheel(androidx.compose.ui.geometry.Offset(1280f, 760f), com.arsnova.ui.Slots.defaults, 0, listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30).map { it.toInt() }, true, false, {}, {}, {}, {}, {}, {}) {} } } }; shot("wheel") }
    @Test fun panel() { val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("ars", 0)
        rule.setContent { ArsTheme(1) { androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxSize().background(com.arsnova.ui.Ars.Bg).padding(24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
            com.arsnova.ui.ToolSettings(com.arsnova.ui.Slots.defaults[0].copy(style = 1, color = 0xFF2D5BD9.toInt(), width = 6f), listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30).map { it.toInt() }, true, prefs, {}, {}, null, {})
            com.arsnova.ui.ToolSettings(com.arsnova.ui.Slots.defaults[1], listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30).map { it.toInt() }, true, prefs, {}, {}, null, {})
            com.arsnova.ui.LatexView("\\Delta G = \\Delta H - T\\Delta S \\qquad \\int_{0}^{\\infty} e^{-x^2}\\,dx = \\frac{\\sqrt{\\pi}}{2}", androidx.compose.ui.Modifier.size(420.dp, 140.dp).background(com.arsnova.ui.Ars.Card))
        } } }; shot("panel") }
    @Test fun newNotebook() { val (r, _) = repo(); rule.setContent { ArsTheme(2) { LibraryScreen(r, {}) {} } }
        rule.onNode(androidx.compose.ui.test.hasText("Nueva nota")).performClick(); rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().captureRoboImage("build/shots/new-notebook.png") }
}
