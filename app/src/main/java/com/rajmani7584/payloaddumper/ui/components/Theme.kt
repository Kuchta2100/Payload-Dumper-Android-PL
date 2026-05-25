package com.rajmani7584.payloaddumper.ui.components

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rajmani7584.payloaddumper.model.ColorTheme
import com.rajmani7584.payloaddumper.model.DarkMode
import com.rajmani7584.payloaddumper.ui.components.foundation.ripple
import com.rajmani7584.payloaddumper.ui.theme.DarkColorScheme
import com.rajmani7584.payloaddumper.ui.theme.LightColorScheme

object AppTheme {
    val colors: Colors
        @ReadOnlyComposable @Composable
        get() = LocalColors.current.toOwned()

    val typography: Typography
        @ReadOnlyComposable @Composable
        get() = LocalTypography.current
}

@Composable
fun AppTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.APP,
    content: @Composable () -> Unit,
) {
    val rippleIndication = ripple()
    val selectionColors = rememberTextSelectionColors(LightColors)
    val typography = provideTypography()

    val context = LocalContext.current
    val colors = when {
        colorTheme == ColorTheme.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        colorTheme == ColorTheme.APP ->
            if (isDarkTheme) DarkColors.toColorScheme(true) else LightColors.toColorScheme(false)

        else ->
            if (isDarkTheme) DarkColorScheme else LightColorScheme
    }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalIndication provides rippleIndication,
        LocalTextSelectionColors provides selectionColors,
        LocalContentColor provides colors.contentColorFor(colors.background),
        LocalTextStyle provides typography.body1
    ) {
        MaterialTheme (
            colorScheme = colors,
            typography = MaterialTheme.typography
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
                content()
            }
        }
    }
}

private fun ColorScheme.toOwned(): Colors = Colors(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    tertiary = tertiary,
    onTertiary = onTertiary,
    error = error,
    onError = onError,
    success = tertiaryContainer,
    onSuccess = onTertiaryContainer,
    disabled = onSurface.copy(alpha = 0.38f),
    onDisabled = onSurface.copy(alpha = 0.38f),
    surface = surface,
    onSurface = onSurface,
    background = background,
    onBackground = onBackground,
    outline = outline,
    text = onBackground,
    textSecondary = onSurfaceVariant,
    textDisabled = onSurface.copy(alpha = 0.38f),
    scrim = scrim,
    elevation = surfaceVariant,
)

fun Colors.toColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        tertiary = tertiary,
        onTertiary = onTertiary,
        error = error,
        onError = onError,
        surface = surface,
        onSurface = onSurface,
        background = background,
        onBackground = onBackground,
        outline = outline,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        tertiary = tertiary,
        onTertiary = onTertiary,
        error = error,
        onError = onError,
        surface = surface,
        onSurface = onSurface,
        background = background,
        onBackground = onBackground,
        outline = outline,
        scrim = scrim,
    )
}

@Composable
fun contentColorFor(color: Color): Color {
    return AppTheme.colors.contentColorFor(color)
}

@Composable
internal fun rememberTextSelectionColors(colorScheme: Colors): TextSelectionColors {
    val primaryColor = colorScheme.primary
    return remember(primaryColor) {
        TextSelectionColors(
            handleColor = primaryColor,
            backgroundColor = primaryColor.copy(alpha = TextSelectionBackgroundOpacity),
        )
    }
}

internal const val TextSelectionBackgroundOpacity = 0.4f
