package com.jpetrydesign.pyng.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Ocean, the default palette from the design. */
object Ocean {
    val primary = Color(0xFF1E5FD1)
    val primaryDark = Color(0xFF7FA9FF)
    val accent = Color(0xFF38A8E0)
    val tintLight = Color(0xFFEAF1FC)
    val tintDark = Color(0xFF1C2433)
    val ink = Color(0xFF15171D)
    val inkDark = Color(0xFFF1F2F6)
}

@Composable
fun PyngTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Ocean.primaryDark,
            onPrimary = Color(0xFF0D1118),
            secondary = Ocean.accent,
            background = Color(0xFF0D1118),
            surface = Color(0xFF0D1118),
            surfaceVariant = Ocean.tintDark,
            onBackground = Ocean.inkDark,
            onSurface = Ocean.inkDark,
        )
    } else {
        lightColorScheme(
            primary = Ocean.primary,
            onPrimary = Color.White,
            secondary = Ocean.accent,
            background = Color(0xFFF7FAFE),
            surface = Color(0xFFF7FAFE),
            surfaceVariant = Ocean.tintLight,
            onBackground = Ocean.ink,
            onSurface = Ocean.ink,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
