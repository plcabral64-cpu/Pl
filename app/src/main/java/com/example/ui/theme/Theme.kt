package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GoldMetallic,
    onPrimary = NavyDark,
    secondary = GoldLight,
    onSecondary = NavyDark,
    tertiary = StatusPronto,
    background = NavyDark,
    onBackground = PureWhite,
    surface = NavyMedium,
    onSurface = PureWhite,
    surfaceVariant = NavyLight,
    onSurfaceVariant = OffWhite
)

private val LightColorScheme = lightColorScheme(
    primary = NavyMedium,
    onPrimary = PureWhite,
    secondary = GoldMetallic,
    onSecondary = Charcoal,
    tertiary = StatusPronto,
    background = OffWhite,
    onBackground = NavyDark,
    surface = PureWhite,
    onSurface = NavyDark,
    surfaceVariant = OffWhite,
    onSurfaceVariant = NavyMedium
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
