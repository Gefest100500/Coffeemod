package com.coffemod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра: эспрессо-графит + синий акцент + кремовый текст.
private val Blue = Color(0xFF4C8DFF)
private val BlueDark = Color(0xFF2F6BE0)
private val BlueLight = Color(0xFF8FB8FF)

private val Cream = Color(0xFFF3EDE3)
private val Sand = Color(0xFFB8AFA2)

private val Espresso = Color(0xFF12100E)
private val EspressoSoft = Color(0xFF1C1917)
private val EspressoCard = Color(0xFF26221E)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF0B1020),
    primaryContainer = BlueDark,
    onPrimaryContainer = Color.White,
    secondary = BlueLight,
    onSecondary = Color(0xFF0B1020),
    secondaryContainer = Color(0xFF23324D),
    onSecondaryContainer = BlueLight,
    background = Espresso,
    onBackground = Cream,
    surface = EspressoSoft,
    onSurface = Cream,
    surfaceVariant = EspressoCard,
    onSurfaceVariant = Sand,
    outline = Color(0xFF4A433C),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF0B1B3A),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E4F5),
    onSecondaryContainer = Color(0xFF132238),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1E1A16),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1E1A16),
    surfaceVariant = Color(0xFFEDE6DA),
    onSurfaceVariant = Color(0xFF4C463F),
    outline = Color(0xFFBFB5A7),
    error = Color(0xFFB3261E),
)

@Composable
fun CoffeModTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
