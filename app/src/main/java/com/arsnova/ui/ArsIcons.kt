package com.arsnova.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Iconos propios de ArsNova: una sola familia de trazo (24×24, línea de 1.8 con puntas redondas).
 * Cada icono es una lista de trazados; los que empiezan por "F:" van rellenos.
 */
object ArsIcons {
    val defs: Map<String, List<String>> = linkedMapOf(
        "Add" to listOf("M12 5v14M5 12h14"),
        "Check" to listOf("M5 12.5l4.5 4.5L19 7.5"),
        "Close" to listOf("M6 6l12 12M18 6L6 18"),
        "Back" to listOf("M14.5 5.5L8 12l6.5 6.5"),
        "Forward" to listOf("M9.5 5.5L16 12l-6.5 6.5"),
        "Down" to listOf("M5.5 9.5L12 16l6.5-6.5"),
        "Undo" to listOf("M8.5 5l-4 4 4 4", "M4.5 9H14a5.5 5.5 0 0 1 0 11H9"),
        "Redo" to listOf("M15.5 5l4 4-4 4", "M19.5 9H10a5.5 5.5 0 0 0 0 11h5"),
        "More" to listOf("F:M4.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0zM10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0zM16.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0z"),
        "MoreV" to listOf("F:M10.4 6a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0zM10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0zM10.4 18a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0z"),
        "Search" to listOf("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0z", "M16 16l4 4"),
        "Settings" to listOf("M4 7.5h7M17 7.5h3M4 16.5h3M13 16.5h7", "M11 7.5a3 3 0 1 0 6 0a3 3 0 1 0-6 0z", "M7 16.5a3 3 0 1 0 6 0a3 3 0 1 0-6 0z"),
        "Pen" to listOf("M4 20l1.2-4.6L16.4 4.2a1.7 1.7 0 0 1 2.4 0l1 1a1.7 1.7 0 0 1 0 2.4L8.6 18.8z", "M14.4 6.2l3.4 3.4"),
        "Highlighter" to listOf("M13.8 4.2l6 6-7.6 7.6H8.4l-2.2-2.2v-3.8z", "M7.3 16.7L4.5 19.5h4.2l1.2-1.2", "M10.2 7.8l6 6"),
        "Eraser" to listOf("M13.5 4.6a1.6 1.6 0 0 1 2.3 0l4.2 4.2a1.6 1.6 0 0 1 0 2.3L12 19.2H8L4.4 15.6a1.6 1.6 0 0 1 0-2.3z", "M9 9.1l6.5 6.5", "M12 19.2h8"),
        "Lasso" to listOf("M12 4C7 4 3 6.7 3 10s4 6 9 6 9-2.7 9-6-4-6-9-6z", "M7 15.5c-1.2 1.2-1.4 3 .2 3.9 1.4.8 2.8-.3 2.3-1.8"),
        "Tape" to listOf("M3.5 10.5l17-3.5v6.5l-17 3.5z", "M8 9.6v6.5M12.5 8.7v6.5M17 7.7v6.5"),
        "Laser" to listOf("M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0z", "M12 4v2.5M12 17.5V20M4 12h2.5M17.5 12H20M6.3 6.3l1.8 1.8M15.9 15.9l1.8 1.8M17.7 6.3l-1.8 1.8M8.1 15.9l-1.8 1.8"),
        "Text" to listOf("M5 7V5h14v2", "M12 5v14", "M9.5 19h5"),
        "Image" to listOf("M4.5 6.5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2z", "M4.5 16l4.2-4.2 3.8 3.8 2.3-2.3 4.7 4.7", "M13.8 9a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0-2.4 0z"),
        "PageAdd" to listOf("M7.5 3.5H14l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19V5a1.5 1.5 0 0 1 1.5-1.5z", "M12 10.5v6M9 13.5h6"),
        "Grid" to listOf("M4.5 4.5h15v15h-15z", "M4.5 9.5h15M4.5 14.5h15M9.5 4.5v15M14.5 4.5v15"),
        "Trash" to listOf("M5 7h14", "M10 7V4.8h4V7", "M7 7l.8 12a1.5 1.5 0 0 0 1.5 1.4h5.4a1.5 1.5 0 0 0 1.5-1.4L17 7", "M10.3 11v5.5M13.7 11v5.5"),
        "Export" to listOf("M12 15V4.5M8 8.5l4-4 4 4", "M5 13v5a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 18v-5"),
        "Import" to listOf("M12 4.5V15M8 11l4 4 4-4", "M5 13v5a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 18v-5"),
        "Mic" to listOf("M12 3.5a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0v-5a3 3 0 0 0-3-3z", "M6 11.5a6 6 0 0 0 12 0", "M12 17.5v3"),
        "Stop" to listOf("F:M8.5 7h7A1.5 1.5 0 0 1 17 8.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 7 15.5v-7A1.5 1.5 0 0 1 8.5 7z"),
        "Headphones" to listOf("M4.5 14v-2a7.5 7.5 0 0 1 15 0v2", "M4.5 14H7v5H6a1.5 1.5 0 0 1-1.5-1.5z", "M19.5 14H17v5h1a1.5 1.5 0 0 0 1.5-1.5z"),
        "Play" to listOf("F:M8.5 5.6a.8.8 0 0 1 1.2-.7l9.4 6.4a.8.8 0 0 1 0 1.4l-9.4 6.4a.8.8 0 0 1-1.2-.7z"),
        "Pause" to listOf("F:M7 5.5h3v13H7zM14 5.5h3v13h-3z"),
        "Sparkle" to listOf("M11 4l1.8 5.2L18 11l-5.2 1.8L11 18l-1.8-5.2L4 11l5.2-1.8z", "M18.5 15.5v4M16.5 17.5h4"),
        "Fit" to listOf("M4.5 9V4.5H9M15 4.5h4.5V9M19.5 15v4.5H15M9 19.5H4.5V15"),
        "Smile" to listOf("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0z", "M8.5 14a4.2 4.2 0 0 0 7 0", "M9 9.3v.9M15 9.3v.9"),
        "Copy" to listOf("M10.5 9h7A1.5 1.5 0 0 1 19 10.5v7a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 9 17.5v-7A1.5 1.5 0 0 1 10.5 9z", "M5 15V6.5A1.5 1.5 0 0 1 6.5 5H15"),
        "AlignLeft" to listOf("M5 6h14M5 10h9M5 14h14M5 18h9"),
        "AlignCenter" to listOf("M5 6h14M7.5 10h9M5 14h14M7.5 18h9"),
        "AlignRight" to listOf("M5 6h14M10 10h9M5 14h14M10 18h9"),
        "Bold" to listOf("M7.5 5h5.5a3.5 3.5 0 0 1 0 7H7.5z", "M7.5 12h6.3a3.5 3.5 0 0 1 0 7H7.5z"),
        "Folder" to listOf("M4 7a1.5 1.5 0 0 1 1.5-1.5h4l2 2.5h7A1.5 1.5 0 0 1 20 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z"),
        "Edit" to listOf("M4 20l1.2-4.6L16.4 4.2a1.7 1.7 0 0 1 2.4 0l1 1a1.7 1.7 0 0 1 0 2.4L8.6 18.8z", "M13 20h7"),
        "Shapes" to listOf("M4.5 13.5h7v7h-7z", "M13 17a3.75 3.75 0 1 0 7.5 0a3.75 3.75 0 1 0-7.5 0z", "M12 3.5l4.2 7H7.8z"),
    )

    private val cache = HashMap<String, ImageVector>()
    operator fun get(name: String): ImageVector = cache.getOrPut(name) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in defs.getValue(name)) {
                if (d.startsWith("F:")) addPath(PathParser().parsePathString(d.substring(2)).toNodes(), fill = SolidColor(Color.Black))
                else addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
    }

    val Add get() = this["Add"]; val Check get() = this["Check"]; val Close get() = this["Close"]; val Back get() = this["Back"]
    val Forward get() = this["Forward"]; val Down get() = this["Down"]; val Undo get() = this["Undo"]; val Redo get() = this["Redo"]
    val More get() = this["More"]; val MoreV get() = this["MoreV"]; val Search get() = this["Search"]; val Settings get() = this["Settings"]
    val Pen get() = this["Pen"]; val Highlighter get() = this["Highlighter"]; val Eraser get() = this["Eraser"]; val Lasso get() = this["Lasso"]
    val Tape get() = this["Tape"]; val Laser get() = this["Laser"]; val Text get() = this["Text"]; val Image get() = this["Image"]
    val PageAdd get() = this["PageAdd"]; val Grid get() = this["Grid"]; val Trash get() = this["Trash"]; val Export get() = this["Export"]
    val Import get() = this["Import"]; val Mic get() = this["Mic"]; val Stop get() = this["Stop"]; val Headphones get() = this["Headphones"]
    val Play get() = this["Play"]; val Pause get() = this["Pause"]; val Sparkle get() = this["Sparkle"]; val Fit get() = this["Fit"]
    val Smile get() = this["Smile"]; val Copy get() = this["Copy"]; val AlignLeft get() = this["AlignLeft"]; val AlignCenter get() = this["AlignCenter"]
    val AlignRight get() = this["AlignRight"]; val Bold get() = this["Bold"]; val Folder get() = this["Folder"]; val Edit get() = this["Edit"]
    val Shapes get() = this["Shapes"]
}
