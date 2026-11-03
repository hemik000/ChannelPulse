package com.channelpulse.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object Pulse {
    val bg = Color(0xFF0B0B10)
    val surface = Color(0xFF16161D)
    val surfaceHi = Color(0xFF23232E)
    val line = Color(0xFF2A2A36)
    val text = Color(0xFFF2F2F5)
    val dim = Color(0xFF9A9AA8)
    val faint = Color(0xFF5E5E6C)
    val accent = Color(0xFFFF3B48)
    val up = Color(0xFF3DDC84)
    val down = Color(0xFFFF7A7A)
    val warn = Color(0xFFFFB020)
}

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pulse.accent,
            onPrimary = Color.White,
            secondary = Pulse.surfaceHi,
            onSecondary = Pulse.text,
            background = Pulse.bg,
            onBackground = Pulse.text,
            surface = Pulse.surface,
            onSurface = Pulse.text,
            surfaceVariant = Pulse.surface,
            onSurfaceVariant = Pulse.text,
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Pulse.text) {
            content()
        }
    }
}
