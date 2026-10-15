package com.rifsxd.ksunext.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Aster / LyraVoid (GPL-3.0): soft tonal surfaces and generous rounded groups,
// adapted to the existing Material 3 components without replacing manager logic.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D5E4),
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF154E58),
    onPrimaryContainer = Color(0xFFB8EDF4),
    secondary = Color(0xFFC6C5DF),
    secondaryContainer = Color(0xFF343447),
    onSecondaryContainer = Color(0xFFE6E4F7),
    tertiary = Color(0xFFF1B8CA),
    background = Color(0xFF111116),
    surface = Color(0xFF111116),
    onSurface = Color(0xFFE8E6EE),
    onSurfaceVariant = Color(0xFFB7B5C3),
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1B1B23),
    surfaceContainer = Color(0xFF22222D),
    surfaceContainerHigh = Color(0xFF292935),
    surfaceContainerHighest = Color(0xFF32323F),
    surfaceVariant = Color(0xFF333340),
    outlineVariant = Color(0xFF444451)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EDF3),
    onPrimaryContainer = Color(0xFF004F5B),
    secondary = Color(0xFF5C5B75),
    secondaryContainer = Color(0xFFE9E8F7),
    onSecondaryContainer = Color(0xFF323247),
    tertiary = Color(0xFF875167),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFF8F7FC),
    onSurface = Color(0xFF202029),
    onSurfaceVariant = Color(0xFF646271),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F0F9),
    surfaceContainer = Color(0xFFEBEAF6),
    surfaceContainerHigh = Color(0xFFE5E4F1),
    surfaceContainerHighest = Color(0xFFDEDEEC),
    surfaceVariant = Color(0xFFE5E4F0),
    outlineVariant = Color(0xFFD1CFDF)
)

fun Color.blend(other: Color, ratio: Float): Color {
    val inverse = 1f - ratio
    return Color(
        red = red * inverse + other.red * ratio,
        green = green * inverse + other.green * ratio,
        blue = blue * inverse + other.blue * ratio,
        alpha = alpha
    )
}

@Composable
fun KernelSUTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoledMode && darkTheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamicScheme = dynamicDarkColorScheme(context)
            dynamicScheme.copy(
                background = AMOLED_BLACK,
                surface = AMOLED_BLACK,
                surfaceVariant = dynamicScheme.surfaceVariant.blend(AMOLED_BLACK, 0.6f),
                surfaceContainer = dynamicScheme.surfaceContainer.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerLow = dynamicScheme.surfaceContainerLow.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerLowest = dynamicScheme.surfaceContainerLowest.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerHigh = dynamicScheme.surfaceContainerHigh.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerHighest = dynamicScheme.surfaceContainerHighest.blend(AMOLED_BLACK, 0.6f)
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        amoledMode && darkTheme -> {
            DarkColorScheme.copy(
                background = AMOLED_BLACK,
                surface = AMOLED_BLACK,
                surfaceVariant = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainer = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerLow = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerLowest = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerHigh = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerHighest = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode },
            navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}
