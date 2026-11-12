package com.arsnova.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
fun squircle(radius: Int): Shape = SmoothCornerShape(radius.dp)

/** Pulsación con rebote, sin onda. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, scale: Float = 0.9f, role: Role? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return pressBounce(src, scale).clickable(src, null, enabled = enabled, role = role, onClick = onClick)
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
