package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PremiumDarkColorScheme = darkColorScheme(
    primary = ElectricSky,
    onPrimary = DarkSkyText,
    primaryContainer = SlateCardVariant,
    onPrimaryContainer = ElectricSky,
    secondary = MintTeal,
    onSecondary = DarkTealText,
    secondaryContainer = SlateCardVariant,
    onSecondaryContainer = MintTeal,
    tertiary = VioletAccent,
    background = SlateDarkBg,
    onBackground = TextPrimary,
    surface = SlateCardBg,
    onSurface = TextPrimary,
    surfaceVariant = SlateCardVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = SlateCardVariant,
    error = CoralError,
    onError = Color(0xFF450A0A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to premium dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = PremiumDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
