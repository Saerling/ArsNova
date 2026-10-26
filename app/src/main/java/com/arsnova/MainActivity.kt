package com.arsnova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
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
        setContent {
            val prefs = remember { getSharedPreferences("ars", MODE_PRIVATE) }
            var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0)) }
            ArsTheme(theme) {
                var open by rememberSaveable { mutableStateOf<String?>(null) }
                var settings by rememberSaveable { mutableStateOf(false) }
                val id = open
                if (settings) { BackHandler { settings = false }; com.arsnova.ui.SettingsScreen(theme, { theme = it; prefs.edit().putInt("theme", it).apply() }) { settings = false } }
                else if (id == null) LibraryScreen(repo, { settings = true }) { open = it }
                else { BackHandler { open = null }; EditorScreen(repo, id) { open = null } }
            }
        }
    }
}
