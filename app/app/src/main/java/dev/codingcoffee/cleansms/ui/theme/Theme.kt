package dev.codingcoffee.cleansms.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    primaryContainer = BlueContainer30,
    secondary = Slate80,
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    primaryContainer = BlueContainer90,
    secondary = Slate40,
)

@Composable
fun CleanSmsTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme.animated(),
        typography = Typography,
        content = content,
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme =
    copy(
        primary = primary.animated(),
        onPrimary = onPrimary.animated(),
        primaryContainer = primaryContainer.animated(),
        onPrimaryContainer = onPrimaryContainer.animated(),
        inversePrimary = inversePrimary.animated(),
        secondary = secondary.animated(),
        onSecondary = onSecondary.animated(),
        secondaryContainer = secondaryContainer.animated(),
        onSecondaryContainer = onSecondaryContainer.animated(),
        tertiary = tertiary.animated(),
        onTertiary = onTertiary.animated(),
        tertiaryContainer = tertiaryContainer.animated(),
        onTertiaryContainer = onTertiaryContainer.animated(),
        background = background.animated(),
        onBackground = onBackground.animated(),
        surface = surface.animated(),
        onSurface = onSurface.animated(),
        surfaceVariant = surfaceVariant.animated(),
        onSurfaceVariant = onSurfaceVariant.animated(),
        surfaceTint = surfaceTint.animated(),
        inverseSurface = inverseSurface.animated(),
        inverseOnSurface = inverseOnSurface.animated(),
        error = error.animated(),
        onError = onError.animated(),
        errorContainer = errorContainer.animated(),
        onErrorContainer = onErrorContainer.animated(),
        outline = outline.animated(),
        outlineVariant = outlineVariant.animated(),
        scrim = scrim.animated(),
        surfaceBright = surfaceBright.animated(),
        surfaceDim = surfaceDim.animated(),
        surfaceContainer = surfaceContainer.animated(),
        surfaceContainerHigh = surfaceContainerHigh.animated(),
        surfaceContainerHighest = surfaceContainerHighest.animated(),
        surfaceContainerLow = surfaceContainerLow.animated(),
        surfaceContainerLowest = surfaceContainerLowest.animated(),
    )

@Composable
private fun Color.animated(): Color = animateColorAsState(this, tween(400), label = "theme").value
