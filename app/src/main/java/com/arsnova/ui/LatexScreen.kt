package com.arsnova.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsnova.ink.Latex

/** Una línea de LaTeX dibujada a su tamaño natural (se encoge solo si no cabe). Si no se puede dibujar, se ve el código. */
@Composable
fun LatexLine(tex: String, size: Float, center: Boolean = false) {
    val color = Ars.Text.toArgb(); val d = LocalDensity.current
    val dr = remember(tex, color, size) { Latex.get(tex, with(d) { size.sp.toPx() }, color) }
    if (dr == null) { Text(tex, fontFamily = FontFamily.Monospace, color = Ars.Muted, style = MaterialTheme.typography.bodySmall); return }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val k = minOf(1f, constraints.maxWidth / dr.intrinsicWidth.toFloat().coerceAtLeast(1f))
        Canvas(Modifier.align(if (center) Alignment.TopCenter else Alignment.TopStart).size(with(d) { (dr.intrinsicWidth * k).toDp() }, with(d) { (dr.intrinsicHeight * k).toDp() })) {
            drawContext.canvas.nativeCanvas.let { c -> c.save(); c.scale(k, k); dr.setBounds(0, 0, dr.intrinsicWidth, dr.intrinsicHeight); dr.draw(c); c.restore() }
        }
    }
}

private sealed class Blk { class H(val t: String, val lvl: Int) : Blk(); class M(val tex: String) : Blk(); class P(val t: String) : Blk(); class Li(val t: String) : Blk() }
private val displayMath = Regex("""\\\[(.+?)\\\]|\$\$(.+?)\$\$|\\begin\{(equation\*?|align\*?|gather\*?|multline\*?)\}(.+?)\\end\{\3\}""", RegexOption.DOT_MATCHES_ALL)
private val heading = Regex("""\\(title|section\*?|subsection\*?|subsubsection\*?)\{([^}]*)\}""")
private val inlineMath = Regex("""\$(.+?)\$|\\\((.+?)\\\)""")

private fun clean(t: String) = t.replace(Regex("""\\(documentclass|usepackage|author|date|label|geometry)(\[[^\]]*\])?\{[^}]*\}"""), "")
    .replace(Regex("""\\(maketitle|noindent|centering|newpage|tableofcontents|bigskip|medskip|smallskip|par)\b"""), "")
    .replace(Regex("""\\(begin|end)\{[a-z*]+\}"""), "").replace(Regex("""\\(textbf|textit|emph|underline|text|textrm)\{([^}]*)\}"""), "$2").replace("\\\\", " ").trim()

/** Lectura sencilla de un documento LaTeX: títulos, párrafos (con matemática en línea), listas y ecuaciones. */
private fun parse(src: String): List<Blk> {
    val s = src.substringAfter("\\begin{document}", src).substringBefore("\\end{document}").replace(Regex("""(?m)(?<!\\)%.*$"""), "")
    val out = ArrayList<Blk>()
    fun text(t: String) {
        for (para in t.split(Regex("""\n\s*\n"""))) {
            val acc = StringBuilder()
            fun flush() { val c = clean(acc.toString()); if (c.isNotBlank()) out += Blk.P(c.replace(Regex("""\s+"""), " ")); acc.setLength(0) }
            for (line in para.lines()) {
                val h = heading.find(line)
                when {
                    h != null -> { flush(); out += Blk.H(h.groupValues[2], when { h.groupValues[1] == "title" -> 0; h.groupValues[1].startsWith("subsub") -> 3; h.groupValues[1].startsWith("sub") -> 2; else -> 1 }) }
                    line.trim().startsWith("\\item") -> { flush(); out += Blk.Li(clean(line.trim().removePrefix("\\item"))) }
                    else -> acc.append(line).append(' ')
                }
            }
            flush()
        }
    }
    var pos = 0
    for (m in displayMath.findAll(s)) {
        text(s.substring(pos, m.range.first)); val env = m.groupValues[3]; val body = (m.groupValues[1] + m.groupValues[2] + m.groupValues[4]).trim()
        out += Blk.M(if (env.startsWith("align") || env.startsWith("gather") || env.startsWith("multline")) "\\begin{aligned}$body\\end{aligned}" else body); pos = m.range.last + 1
    }
    text(s.substring(pos)); return out
}

/** Párrafo con matemática en línea: se parte en renglones y cada uno se dibuja como texto + fórmulas. */
private fun mathLines(p: String): List<String> {
    val segs = ArrayList<Pair<Boolean, String>>(); var pos = 0
    for (m in inlineMath.findAll(p)) { if (m.range.first > pos) p.substring(pos, m.range.first).split(' ').filter { it.isNotEmpty() }.forEach { segs += false to it }
        segs += true to (m.groupValues[1] + m.groupValues[2]); pos = m.range.last + 1 }
    if (pos < p.length) p.substring(pos).split(' ').filter { it.isNotEmpty() }.forEach { segs += false to it }
    fun esc(t: String) = t.replace("{", "").replace("}", "").replace("\\", "").replace("_", "\\_").replace("%", "\\%").replace("&", "\\&").replace("#", "\\#").replace("^", "").replace("~", " ")
    val lines = ArrayList<String>(); val cur = StringBuilder(); var n = 0
    for ((isMath, t) in segs) {
        if (n + t.length > 62 && n > 0) { lines += cur.toString(); cur.setLength(0); n = 0 }
        cur.append(if (isMath) " $t\\ " else "\\text{${esc(t)} }"); n += t.length + 1
    }
    if (cur.isNotEmpty()) lines += cur.toString()
    return lines
}

/** Vista previa del documento. */
@Composable
fun LatexDoc(tex: String, modifier: Modifier = Modifier) {
    val blocks = remember(tex) { runCatching { parse(tex) }.getOrDefault(emptyList()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (blocks.isEmpty()) Text("Aquí verás el documento. Convierte la página o escribe LaTeX en «Código».", color = Ars.Muted)
        for (b in blocks) when (b) {
            is Blk.H -> Text(b.t, style = when (b.lvl) { 0 -> MaterialTheme.typography.headlineMedium; 1 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium }, modifier = Modifier.padding(top = if (b.lvl <= 1) 8.dp else 2.dp))
            is Blk.M -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { LatexLine(b.tex, 19f, center = true) }
            is Blk.Li -> Row { Text("•  ", color = Ars.Muted); Column { if (inlineMath.containsMatchIn(b.t)) mathLines(b.t).forEach { LatexLine(it, 16f) } else Text(b.t) } }
            is Blk.P -> if (inlineMath.containsMatchIn(b.t)) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { mathLines(b.t).forEach { LatexLine(it, 16f) } } else Text(b.t, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Vista LaTeX de una página, con cambio de vistas como en MathWrite: Manuscrito, Código, Vista previa o Dividida
 * (tu letra a la izquierda; el documento y su código a la derecha).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatexPane(title: String, sheet: ImageBitmap, tex: String, busy: Boolean, onTex: (String) -> Unit, onConvert: () -> Unit, onExport: () -> Unit, onClose: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(3) }
    val clip = LocalClipboardManager.current
    androidx.activity.compose.BackHandler(onBack = onClose)
    @Composable fun Sheet(m: Modifier) = Box(m.clip(squircle(22)).background(Color.White).verticalScroll(rememberScrollState())) { Image(sheet, "Manuscrito", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
    @Composable fun Code(m: Modifier) = OutlinedTextField(tex, onTex, m, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), shape = squircle(22),
        placeholder = { Text("\\section{Título}\nTexto con $ x^2 $ y ecuaciones:\n\\[ E = mc^2 \\]") },
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Ars.Card, focusedContainerColor = Ars.Card, unfocusedBorderColor = Color.Transparent, focusedBorderColor = Ars.Line))
    @Composable fun Doc(m: Modifier) = Box(m.clip(squircle(22)).background(Ars.Card).verticalScroll(rememberScrollState())) { LatexDoc(tex, Modifier.fillMaxWidth().padding(22.dp)) }
    Column(Modifier.fillMaxSize().background(Ars.Bg).pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(ArsIcons.Back, "Volver") }
            Text("LaTeX · $title", style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onConvert, enabled = !busy) { Icon(ArsIcons.Sparkle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (busy) "Convirtiendo…" else "Convertir página") }
            IconButton({ clip.setText(AnnotatedString(tex)) }, enabled = tex.isNotBlank()) { Icon(ArsIcons.Copy, "Copiar código") }
            IconButton(onExport, enabled = tex.isNotBlank()) { Icon(ArsIcons.Export, "Exportar .tex") }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally)) {
            listOf("Manuscrito", "Código", "Vista previa", "Dividida").forEachIndexed { i, l -> SegmentedButton(tab == i, { tab = i }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(l, maxLines = 1) } }
        }
        AnimatedContent(tab, Modifier.weight(1f).padding(vertical = 12.dp), transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) }, label = "vista") { t ->
            when (t) {
                0 -> Sheet(Modifier.fillMaxSize())
                1 -> Code(Modifier.fillMaxSize())
                2 -> Doc(Modifier.fillMaxSize())
                else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sheet(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) { Doc(Modifier.weight(1.4f).fillMaxWidth()); Code(Modifier.weight(1f).fillMaxWidth()) }
                }
            }
        }
    }
}
