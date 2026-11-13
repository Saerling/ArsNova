package com.arsnova

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.arsnova.data.Repo
import com.arsnova.data.Stroke
import com.arsnova.data.Template
import com.arsnova.data.Tool
import com.arsnova.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Auditoría de menús: abre y pulsa cada herramienta, menú y desplegable; anota qué falla en build/shots/menus-*.txt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val bad = ArrayList<String>(); private val ok = ArrayList<String>()
    /** Si algo no llega a reposo, que falle en segundos y quede anotado en vez de colgar toda la auditoría. */
    @org.junit.Before fun fast() { androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(8, java.util.concurrent.TimeUnit.SECONDS) }
    /** Los diálogos con campo de texto no llegan a reposo bajo Robolectric (su estrategia de espera pide layout sin parar): se abren, se componen un rato y se comprueba que no fallen. */
    private fun opens(open: () -> Unit) { open(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500)) }

    private fun back() = runCatching { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun step(name: String, f: () -> Unit) {
        try { f(); rule.waitForIdle(); ok += name } catch (e: Throwable) { bad += "$name → ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.take(4)?.joinToString(" | ")}"; back() }
    }
    private fun tap(text: String) { rule.onAllNodesWithText(text, substring = true).onFirst().performClick(); rule.waitForIdle() }
    private fun desc(d: String, i: Int = 0) { rule.onAllNodesWithContentDescription(d)[i].performClick(); rule.waitForIdle() }
    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).onFirst().assertExists()
    private fun gone(text: String) = assertTrue("«$text» sigue en pantalla", rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty())
    private fun shot(name: String) { rule.waitForIdle(); runCatching { com.github.takahirom.roborazzi.captureScreenRoboImage("build/shots/$name.png") } }
    private fun done(file: String) {
        File("build/shots").mkdirs()
        File("build/shots/$file.txt").writeText("OK ${ok.size}\n" + ok.joinToString("\n") + "\n\nFALLOS ${bad.size}\n" + bad.joinToString("\n") + "\n")
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
    private fun repo(): Pair<Repo, String> {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("ars", 0).edit().putBoolean("autoUpdate", false).commit()
        val r = Repo(app); com.arsnova.ink.Ink.bgDir = r.dir; com.arsnova.ink.Latex.init(app)
        val id = r.create("Termodinámica", 0xFF2D5BD9.toInt(), Template.GRID, 0); r.create("Ideas", 0xFF1F8A5B.toInt(), Template.DOTS, 1)
        val nb = r.load(id)
        nb.pages[0].strokes += Stroke(1, 0xFF1D1B18.toInt(), 5f, pts = FloatArray(90) { i -> if (i % 3 == 0) 200f + i * 8 else if (i % 3 == 1) 400f + (i % 7) * 9 else 0.5f })
        nb.pages[0].strokes += Stroke(2, 0xFF7FB3D5.toInt(), 44f, pts = floatArrayOf(200f, 700f, .5f, 900f, 700f, .5f), style = 11)
        r.saveAsync(nb, com.arsnova.ink.Ink.render(nb.pages[0], 0.25f)); Thread.sleep(600)
        return r to id
    }

    @Test fun editor() {
        val (r, id) = repo(); rule.setContent { ArsTheme(2) { EditorScreen(r, id) {} } }; rule.waitForIdle()
        shot("bar-dark")
        for (s in Slots.defaults) step("Barra › ${s.name} (elegir y abrir ajustes)") {
            desc(s.name); desc(s.name)
            when (s.tool) {
                Tool.PEN -> { has("Grosor"); if (s.style == 0) shot("menu-pen") }
                Tool.HIGHLIGHTER -> { has("Grosor"); tap("Trazar en línea recta"); tap("Trazar en línea recta") }
                Tool.ERASER -> { tap("Píxel"); tap("Objeto"); has("Qué borra"); tap("Resaltador"); tap("Cinta"); tap("Todo"); tap("Volver al lápiz"); tap("Volver al lápiz"); has("Mantener para borrar")
                    tap("Limpiar la página"); rule.onNodeWithContentDescription("Deshacer").assertIsEnabled().performClick(); shot("menu-eraser") }
                Tool.TAPE -> { has("Recta"); tap("Libre"); tap("Rectángulo"); tap("Zigzag"); tap("Ondas"); tap("Mostrar todas"); shot("menu-tape"); tap("Ocultar todas"); tap("Recta"); tap("Solo horizontal"); tap("Solo horizontal"); tap("Liso")
                    tap("Quitar las de esta página"); rule.onNodeWithContentDescription("Deshacer").performClick(); tap("De todas") }
                Tool.LASSO -> { tap("Rectangular"); tap("Escritura"); tap("Escritura"); tap("Cinta"); tap("Cinta"); shot("menu-lasso"); tap("Libre") }
                Tool.LASER -> { tap("Punto"); has("Duración"); shot("menu-laser"); tap("Línea") }
                else -> {}
            }
            back() // Atrás cierra el panel (que repite nombres de lápiz en su selector de estilo)
        }
        step("Barra › Lazo visible") { rule.onNodeWithContentDescription("Lazo").assertExists().assertIsDisplayed() }
        step("Barra › Deshacer / Rehacer") { rule.onNodeWithContentDescription("Deshacer").assertExists(); rule.onNodeWithContentDescription("Rehacer").assertExists() }
        step("Barra › color rápido") { desc("Bolígrafo"); desc("Más colores"); back() }
        step("Insertar › Formas") { desc("Insertar"); tap("Formas"); for (f in listOf("Línea", "Flecha", "Elipse", "Triángulo", "Rombo", "Rectángulo")) desc(f); shot("menu-shapes"); desc("Salir de formas")
            assertTrue("la tira de formas sigue abierta", rule.onAllNodesWithContentDescription("Salir de formas").fetchSemanticsNodes().isEmpty()) }
        for ((item, close) in listOf("Stickers" to "Cerrar", "Líneas y cuadrículas" to null, "Página" to null))
            step("Insertar › $item") { desc("Insertar"); has("Imagen"); tap(item); if (close != null) { tap(close); gone(close) } else if (item.startsWith("Líneas")) back() }
        step("⋯ › Editar barra") { desc("Bolígrafo"); desc("Opciones de la barra"); shot("menu-bar"); tap("Editar barra"); shot("menu-editbar"); has("Editar barra y rueda"); tap("Agrupar los lápices"); tap("Agrupar los lápices")
            tap("Lápices con forma"); shot("bar-icons"); tap("Lápices con forma")
            // mantener pulsada la primera fila y bajarla dos puestos: el Bolígrafo pasa a ser la tercera herramienta
            rule.onAllNodesWithText("Bolígrafo", substring = true).onFirst().performTouchInput { down(center); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(0f, 30f)); moveBy(androidx.compose.ui.geometry.Offset(0f, 90f)); up() }
            rule.waitForIdle(); shot("menu-editbar2"); tap("Listo"); gone("Editar barra y rueda") }
        step("Barra › arrastrar para reordenar") {
            val x0 = rule.onNodeWithContentDescription("Bolígrafo").fetchSemanticsNode().boundsInRoot.left; val xl = rule.onNodeWithContentDescription("Lazo").fetchSemanticsNode().boundsInRoot.left
            assertTrue("el editor de barra movió el Bolígrafo ($x0)", x0 > rule.onNodeWithContentDescription("Pluma estilográfica").fetchSemanticsNode().boundsInRoot.left)
            rule.onNodeWithContentDescription("Lazo").performTouchInput { down(center); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(-20f, 0f)); moveBy(androidx.compose.ui.geometry.Offset(-2000f, 0f)); up() }
            rule.waitForIdle(); val now = rule.onNodeWithContentDescription("Lazo").fetchSemanticsNode().boundsInRoot.left
            assertTrue("el Lazo no cambió de sitio ($xl → $now)", now < xl - 50f && now < rule.onNodeWithContentDescription("Pluma estilográfica").fetchSemanticsNode().boundsInRoot.left); shot("bar-moved") }
        step("Dial › todos los lápices") {
            fun find(v: android.view.View): com.arsnova.ink.InkView? = (v as? com.arsnova.ink.InkView) ?: (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
            val ink = find(rule.activity.window.decorView)!!; desc("Pluma estilográfica"); rule.runOnUiThread { ink.onWheel(640f, 400f) }; rule.waitForIdle()
            tap("Todos los lápices"); rule.mainClock.advanceTimeBy(600); shot("wheel-all"); rule.onAllNodesWithContentDescription("Crayón").onLast().performClick(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
            rule.onNodeWithContentDescription("Crayón").assertExists(); assertTrue("la pluma debía volverse crayón", rule.onAllNodesWithContentDescription("Pluma estilográfica").fetchSemanticsNodes().isEmpty())
            rule.runOnUiThread { ink.onWheel(640f, 400f) }; rule.waitForIdle(); tap("Mi barra"); back() }
        step("⋯ › Ajustes del lápiz") { desc("Opciones de la barra"); tap("Ajustes del lápiz"); back() }
        step("⋯ › Líneas y cuadrículas") { desc("Opciones de la barra"); tap("Líneas y cuadrículas"); back() }
        for (t in listOf("Dibujar con el dedo", "Mejorar escritura a mano", "Minimizar automáticamente")) step("⋯ › $t") { desc("Opciones de la barra"); tap(t); tap(t); back() }
        for ((item, close) in listOf("Añadir página" to null, "Eliminar página" to null, "Plantilla:" to null, "Solo lápiz" to null, "Ajustar a pantalla" to null))
            step("⋮ › $item") { desc("Menú de la nota"); has("Exportar PDF"); tap(item); if (close != null) { tap(close); gone(close) } }
        step("⋮ › Configurar doble toque") { desc("Menú de la nota"); tap("Configurar doble toque"); shot("menu-learn"); tap("Cancelar"); gone("Haz doble toque") }
        step("⋮ › Vista LaTeX") { desc("Menú de la nota"); tap("Vista LaTeX"); has("Manuscrito"); for (v in listOf("Código", "Vista previa", "Dividida", "Manuscrito")) tap(v); shot("menu-latex"); desc("Volver") }
        step("Zoom › menú") { tap(" %"); has("Ajustar al ancho"); tap("Bloquear zoom"); tap(" %"); tap("Bloquear zoom"); tap(" %"); tap("Guardar zoom actual"); tap(" %"); shot("menu-zoom"); tap("Ajustar al ancho") }
        step("Buscador de la nota") { desc("Buscar en la nota"); desc("Cerrar") }
        step("Páginas") { tap("/"); shot("menu-pages"); back() }
        step("Minimizar y volver a mostrar") { desc("Minimizar"); desc("Mostrar herramientas"); rule.onNodeWithContentDescription("Lazo").assertExists() }
        shot("bar-end"); done("menus-editor")
    }


    @Test fun dialogText() { val (r, id) = repo(); rule.setContent { ArsTheme(2) { EditorScreen(r, id) {} } }; rule.waitForIdle()
        desc("Insertar"); opens { rule.onAllNodesWithText("Texto").onFirst().performClick() } }
    @Test fun dialogFormula() { val (r, id) = repo(); rule.setContent { ArsTheme(2) { EditorScreen(r, id) {} } }; rule.waitForIdle()
        desc("Insertar"); opens { rule.onAllNodesWithText("Fórmula (LaTeX)").onFirst().performClick() } }
    @Test fun dialogAiKey() { val (r, id) = repo(); rule.setContent { ArsTheme(2) { EditorScreen(r, id) {} } }; rule.waitForIdle()
        desc("Menú de la nota"); opens { rule.onAllNodesWithText("Resumen y tarjetas", substring = true).onFirst().performClick() } }
    @Test fun dialogRename() { val (r, _) = repo(); rule.setContent { ArsTheme(2) { LibraryScreen(r, {}) {} } }; rule.waitForIdle()
        rule.onAllNodesWithText("Ideas").onFirst().performTouchInput { longClick() }; rule.waitForIdle(); opens { rule.onAllNodesWithText("Renombrar").onFirst().performClick() } }


    @Test fun editorLightBar() { val (r, id) = repo(); rule.setContent { ArsTheme(1) { EditorScreen(r, id) {} } }; shot("bar-light") }

    @Test fun wheel() {
        var picked = -1
        rule.setContent { ArsTheme(2) { Box(Modifier.fillMaxSize().background(Ars.Bg)) {
            ToolWheel(Offset(1280f, 760f), Slots.defaults, 0, listOf(0xFF1D1B18, 0xFF2D5BD9, 0xFF34C759, 0xFFFFCC00, 0xFFFF3B30).map { it.toInt() }, true, false, { picked = it }, {}, {}, {}, {}, {}, onAll = {}, onDismiss = {}) } } }
        rule.mainClock.advanceTimeBy(900); shot("wheel-pens")
        Slots.defaults.forEachIndexed { i, s -> step("Dial › ${s.name}") { desc(s.name); assertTrue("eligió $picked, no $i", picked == i) } }
        done("menus-wheel")
    }

    @Test fun library() {
        val (r, _) = repo(); var settings = false
        rule.setContent { ArsTheme(2) { LibraryScreen(r, { settings = true }) {} } }; rule.waitForIdle()
        step("Ajustes") { desc("Ajustes"); assertTrue(settings) }
        step("Nueva nota › Crear") { tap("Nueva nota"); has("Crear nota"); shot("menu-new"); tap("Crear") }
        step("Nota › menú") { rule.onAllNodesWithText("Ideas").onFirst().performTouchInput { longClick() }; rule.waitForIdle(); has("Renombrar"); has("Mover a la papelera"); back() }
        step("Nota › mover a carpeta") { rule.onAllNodesWithText("Ideas").onFirst().performTouchInput { longClick() }; rule.waitForIdle(); tap("Mover a carpeta"); tap("Cerrar") }
        step("Nota › papelera y restaurar") { rule.onAllNodesWithText("Ideas").onFirst().performTouchInput { longClick() }; rule.waitForIdle(); tap("Mover a la papelera")
            tap("Papelera"); rule.onAllNodesWithText("Ideas").onFirst().performTouchInput { longClick() }; rule.waitForIdle(); tap("Restaurar"); tap("Todas"); has("Ideas") }
        done("menus-library")
    }

    @Test fun settings() {
        var back = false
        rule.setContent { ArsTheme(2) { SettingsScreen(2, {}) { back = true } } }; rule.waitForIdle()
        val toggles = rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size
        step("Interruptores ($toggles)") { assertTrue("no hay interruptores accesibles", toggles > 0); for (i in 0 until toggles) { rule.onAllNodes(isToggleable())[i].performScrollTo().performClick(); rule.onAllNodes(isToggleable())[i].performClick() } }
        step("Atrás") { desc("Atrás"); assertTrue(back) }
        done("menus-settings")
    }
}
