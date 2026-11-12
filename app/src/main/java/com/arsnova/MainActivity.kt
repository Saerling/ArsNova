package com.arsnova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveable
import com.arsnova.data.Repo
import com.arsnova.ui.ArsTheme
import com.arsnova.ui.EditorScreen
import com.arsnova.ui.LibraryScreen

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        com.arsnova.ui.StylusKeys.handler?.invoke(event) == true || super.dispatchKeyEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Repo(applicationContext)
        com.arsnova.ink.Ink.bgDir = repo.dir
        com.arsnova.ink.Latex.init(this)
        com.arsnova.ui.Look.load(getSharedPreferences("ars", MODE_PRIVATE))
        setContent {
            val prefs = remember { getSharedPreferences("ars", MODE_PRIVATE) }
            var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0)) }
            ArsTheme(theme) {
                var open by rememberSaveable { mutableStateOf<String?>(null) }
                var settings by rememberSaveable { mutableStateOf(false) }
                val screen = when { settings -> "s"; open == null -> "l"; else -> "e:$open" }
                // cambio de pantalla: la nueva entra con un leve aumento mientras la anterior se desvanece
                androidx.compose.animation.AnimatedContent(screen, transitionSpec = {
                    val e = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, 60)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(320, easing = e), if (targetState.startsWith("e")) 0.94f else 1.04f))
                        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                }, label = "screen") { sc ->
                    when {
                        sc == "s" -> { BackHandler { settings = false }; com.arsnova.ui.SettingsScreen(theme, { theme = it; prefs.edit().putInt("theme", it).apply() }) { settings = false } }
                        sc == "l" -> LibraryScreen(repo, { settings = true }) { open = it }
                        else -> { BackHandler { open = null }; EditorScreen(repo, sc.substring(2)) { open = null } }
                    }
                }
            }
        }
    }
}
