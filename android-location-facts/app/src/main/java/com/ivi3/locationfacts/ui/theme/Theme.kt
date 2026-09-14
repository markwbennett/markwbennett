package com.ivi3.locationfacts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Pine = Color(0xFF1B3A2F)
private val Moss = Color(0xFF5B8C6E)
private val Parchment = Color(0xFFF5F1E6)

private val LightScheme = lightColorScheme(
    primary = Pine,
    secondary = Moss,
    background = Parchment,
    surface = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Moss,
    secondary = Pine,
)

@Composable
fun LocationFactsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        // Material You where the platform offers it, the hand-picked palette otherwise.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colors, content = content)
}
