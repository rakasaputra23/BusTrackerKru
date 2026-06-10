package com.example.buskrutracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary       = Blue600,
    secondary     = Green500,
    tertiary      = Yellow400,
    background    = Gray50,
    surface       = White,
    onPrimary     = White,
    onSecondary   = White,
    onBackground  = Gray900,
    onSurface     = Gray900,
    error         = Red500
)

@Composable
fun BusKruTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content     = content
    )
}