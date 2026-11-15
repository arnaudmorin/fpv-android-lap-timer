package com.gfpv.camtimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LapGreen = Color(0xFF4ADE80)
val TriggerAmber = Color(0xFFFBBF24)
val StopRed = Color(0xFFF87171)
val CardBlack = Color(0xFF0E0E0E)

// True black everywhere the screen is mostly idle: OLED pixels showing black are off.
private val TrueBlackScheme = darkColorScheme(
    primary = LapGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF14361F),
    onPrimaryContainer = LapGreen,
    secondary = TriggerAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A2C05),
    onSecondaryContainer = TriggerAmber,
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color.Black,
    error = StopRed,
    onError = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = CardBlack,
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF262626),
)

@Composable
fun CamTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TrueBlackScheme, content = content)
}
