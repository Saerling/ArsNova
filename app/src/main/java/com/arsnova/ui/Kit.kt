package com.arsnova.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arsnova.ui.mochy.AppSwitch
import com.arsnova.ui.mochy.SmoothCornerShape
import com.arsnova.ui.mochy.pressBounce

/*
 * Piezas base de la interfaz con el tacto de Mochy Music: todo lo pulsable se encoge bajo el dedo y
 * vuelve con un pequeño rebote. Se llaman igual que las de Material a propósito: dentro de este
 * paquete tienen prioridad, así que cada pantalla las usa sin cambiar su código.
 */

/** Esquinas continuas ("squircle") de Mochy. */
fun squircle(radius: Int): Shape = when (Look.corners) {
    1 -> androidx.compose.foundation.shape.CornerSize(radius.dp).let { com.arsnova.ui.bitchord.ContinuousCornerShape(it, it, it, it) }
    2 -> androidx.compose.foundation.shape.RoundedCornerShape(radius.dp)
    else -> SmoothCornerShape(radius.dp)
}

/** Pulsación con rebote, sin onda. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, scale: Float = 0.9f, role: Role? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return (if (Look.bounce) pressBounce(src, scale) else this).clickable(src, if (Look.bounce) null else androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(modifier.size(44.dp).alpha(if (enabled) 1f else 0.32f).bounceClick(enabled, 0.82f, Role.Button, onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(modifier.alpha(if (enabled) 1f else 0.38f).bounceClick(enabled, 0.93f, Role.Button, onClick).clip(CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge, LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** Botón principal: cápsula con el color del texto (blanca en oscuro, negra en claro). */
@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = CircleShape, content: @Composable RowScope.() -> Unit) {
    Row(modifier.alpha(if (enabled) 1f else 0.38f).bounceClick(enabled, 0.93f, Role.Button, onClick).clip(shape).background(Ars.Text).defaultMinSize(minHeight = 44.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge, LocalContentColor provides Ars.Bg) { content() }
    }
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) =
    AppSwitch(checked, onCheckedChange, modifier, enabled)

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, color: Color = Ars.Line) =
    androidx.compose.material3.HorizontalDivider(modifier.padding(horizontal = 18.dp), 1.dp, color)

@Composable
fun DropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, scrollState: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) =
    androidx.compose.material3.DropdownMenu(expanded, onDismissRequest, modifier, scrollState = scrollState, shape = squircle(22), containerColor = Ars.Sheet,
        tonalElevation = 0.dp, shadowElevation = 14.dp, border = BorderStroke(1.dp, Ars.GlassBorder), content = content)

/** Diálogo como hoja de Mochy: tarjeta de esquinas continuas que entra con un leve aumento, acciones en píldora. */
@Composable
fun AlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    androidx.compose.ui.window.Dialog(onDismissRequest) {
        val a = remember { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(Unit) { a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) }
        androidx.compose.material3.Surface(modifier.alpha(a.value.coerceIn(0f, 1f)).scale(0.92f + 0.08f * a.value), shape = squircle(30), color = Ars.Sheet, border = BorderStroke(1.dp, Ars.GlassBorder), shadowElevation = 18.dp) {
            androidx.compose.foundation.layout.Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 12.dp)) {
                if (title != null) CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge, LocalContentColor provides Ars.Text) { title() }
                if (text != null) Box(Modifier.padding(top = if (title != null) 12.dp else 0.dp).weight(1f, fill = false)) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium, LocalContentColor provides Ars.Text) { text() } }
                Row(Modifier.padding(top = 10.dp).align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { dismissButton?.invoke(); confirmButton() }
            }
        }
    }
}
/** Fila de menú con el resalte redondeado de Mochy (sin onda). */
@Composable
fun DropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, enabled: Boolean = true) {
    Row(modifier.padding(horizontal = 6.dp).clip(squircle(14)).clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.4f).defaultMinSize(minWidth = 190.dp).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leadingIcon() }; androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp)) }
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) { Box(Modifier.weight(1f, fill = trailingIcon != null)) { text() } }
        if (trailingIcon != null) { androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp)); trailingIcon() }
    }
}

/** Selector de opciones: pista en píldora y la opción elegida invertida (como las pestañas activas de Mochy). */
@Composable
fun SingleChoiceSegmentedButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.clip(squircle(15)).background(Ars.PillFill).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
fun RowScope.SegmentedButton(selected: Boolean, onClick: () -> Unit, shape: Shape, modifier: Modifier = Modifier, label: @Composable () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(if (selected) Ars.Text else Color.Transparent, androidx.compose.animation.core.tween(180), label = "seg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) Ars.Bg else Ars.Text, androidx.compose.animation.core.tween(180), label = "segT")
    Box(modifier.weight(1f).bounceClick(scale = 0.95f, onClick = onClick).clip(squircle(12)).background(bg).padding(horizontal = 8.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge, LocalContentColor provides fg) { label() } }
}

/** Reordenar manteniendo pulsado: el elemento sigue al dedo, los demás le abren hueco y el cambio se aplica al soltar. */
class DragOrder {
    var from by androidx.compose.runtime.mutableIntStateOf(-1); var off by androidx.compose.runtime.mutableFloatStateOf(0f); var step = 1f
    fun target(n: Int) = (from + Math.round(off / step)).coerceIn(0, n - 1)
    /** Desplazamiento visual del elemento `j` mientras se arrastra otro. */
    fun shift(j: Int, n: Int): Float { if (from < 0) return 0f; if (j == from) return off; val t = target(n); return if (j in (from + 1)..t) -step else if (j in t until from) step else 0f }
}
fun Modifier.dragOrder(st: DragOrder, pos: Int, n: Int, vertical: Boolean, onStart: () -> Unit = {}, onMove: (Int, Int) -> Unit): Modifier = pointerInput(pos, n) {
    detectDragGesturesAfterLongPress(onDragStart = { st.from = pos; st.off = 0f; onStart() },
        onDragEnd = { val f = st.from; val t = st.target(n); st.from = -1; if (f >= 0 && t != f) onMove(f, t) }, onDragCancel = { st.from = -1 }) { ch, d -> ch.consume(); st.off += if (vertical) d.y else d.x } }
