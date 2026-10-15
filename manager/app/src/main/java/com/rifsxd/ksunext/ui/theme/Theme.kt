// Theme controller and palette selection transplanted from lyravoid/Aster
// revision 84a9116606b01e436e9b58852acee9e8b9b9e627, GPL-3.0.
package com.rifsxd.ksunext.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.rifsxd.ksunext.ui.shell.GlobalLayout
import com.rifsxd.ksunext.ui.shell.rememberGlobalLayout
import com.rifsxd.ksunext.ui.shell.rememberVisualChoice
import com.rifsxd.ksunext.ui.shell.rememberVisualFlag
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
data class ThemeModeState(val isDark: Boolean, val isDynamicColor: Boolean)
val LocalThemeModeState = compositionLocalOf { ThemeModeState(false, true) }
val refreshTheme = mutableStateOf(false)

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
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val activity = LocalActivity.current as? ComponentActivity
    val useSystemColor by rememberVisualFlag(SystemDynamicColorKey, true)
    val effectiveDynamicColor = dynamicColor && useSystemColor
    val customColorScheme by rememberVisualChoice(PresetColorKey, DefaultPresetColor)
    // A wallpaper read reports loading/error progress too. Only its enabled flag and seed
    // can change the palette; keep those progress updates out of the theme composition.
    val initialWallpaperPalette = remember { WallpaperColorTheme.state.value.let { it.enabled to it.seed } }
    val wallpaperPalette by remember {
        WallpaperColorTheme.state.map { it.enabled to it.seed }.distinctUntilChanged()
    }.collectAsState(initial = initialWallpaperPalette)
    val useWallpaperColor = wallpaperPalette.first
    val wallpaperColorSeed = wallpaperPalette.second
    val themeColorScheme = rememberThemeColorSchemeState()
    val panoramaHome = rememberGlobalLayout().value == GlobalLayout.Panorama
    val miuixColorSchemeMode = when {
        darkTheme -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetLight
    }
    // A palette of the reader's own only means anything when it is built from a seed of the
    // reader's own, so the system seed is read only once they have chosen one. Left alone, the key
    // colour below stays null and Miuix draws the platform's colours as they are, down to the
    // style and the spec — the palette this app had before the choice existed.
    val paletteChosen = !themeColorScheme.isDefaultPalette()
    val systemColorSeed = rememberSystemPaletteSeed(enabled = effectiveDynamicColor && paletteChosen)
    val themeColorChoice = resolveThemeColorChoice(
        panoramaHome = panoramaHome,
        wallpaperEnabled = useWallpaperColor,
        wallpaperSeed = wallpaperColorSeed,
        systemDynamicEnabled = effectiveDynamicColor,
        systemSeed = systemColorSeed,
        paletteChosen = paletteChosen,
        presetSeed = presetThemeSeed(customColorScheme),
    )
    // Seed zero is how the resolver says "let the platform decide"; a real one is the colour the
    // style and the spec are applied to.
    val miuixKeyColor = themeColorChoice.seed.takeIf { it != 0 }?.let { argb -> Color(argb) }
    val miuixThemeController = remember(
        miuixColorSchemeMode,
        miuixKeyColor,
        themeColorScheme.style,
        themeColorScheme.spec,
    ) {
        ThemeController(
            colorSchemeMode = miuixColorSchemeMode,
            keyColor = miuixKeyColor,
            colorSpec = themeColorScheme.spec,
            paletteStyle = themeColorScheme.style,
        )
    }


    val resolvedColors = miuixThemeController.currentColors()
    val uiColors = if (amoledMode && darkTheme) resolvedColors.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainer = resolvedColors.surfaceContainer.blend(Color.Black, 0.6f),
        surfaceContainerHigh = resolvedColors.surfaceContainerHigh.blend(Color.Black, 0.6f),
        surfaceContainerHighest = resolvedColors.surfaceContainerHighest.blend(Color.Black, 0.6f),
    ) else resolvedColors
    MiuixTheme(colors = uiColors) {
        val colors = MiuixTheme.colorScheme
        // Existing KSUN detail screens keep their APIs while reading the same real Aster palette.
        val base = if (darkTheme) darkColorScheme() else lightColorScheme()
        val material = base.copy(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            background = if (amoledMode && darkTheme) Color.Black else colors.background,
            surface = if (amoledMode && darkTheme) Color.Black else colors.surface,
            onSurface = colors.onSurface,
            onBackground = colors.onBackground,
            surfaceContainer = colors.surfaceContainer,
            surfaceContainerLow = colors.surfaceContainer,
            surfaceContainerHigh = colors.surfaceContainer,
            surfaceVariant = colors.surfaceContainer,
            onSurfaceVariant = colors.onSurfaceContainer,
        )
        DisposableEffect(activity, darkTheme) {
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { darkTheme },
                navigationBarStyle = if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb())
                    else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            )
            onDispose { }
        }
        CompositionLocalProvider(
            LocalThemeModeState provides ThemeModeState(darkTheme, effectiveDynamicColor),
            // Miuix and Material expose separate locals. Legacy KSUN text/icons must
            // inherit the same foreground when hosted inside an Aster card.
            androidx.compose.material3.LocalContentColor provides material.onSurface,
        ) {
            MaterialTheme(
                colorScheme = material,
                typography = Typography,
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp),
                    extraLarge = RoundedCornerShape(32.dp),
                ),
                content = content,
            )
        }
    }
}

/** The preset colour as a seed, and the last resort when no other source can offer one. */
internal fun presetThemeSeed(colorName: String?): Int =
    (LegacyMiuixThemeSeeds[colorName] ?: LegacyMiuixThemeSeeds.getValue("blue")).toArgb()

private val LegacyMiuixThemeSeeds = mapOf(
    "amber" to Color(0xFFFFC107),
    "blue_grey" to Color(0xFF607D8B),
    "blue" to Color(0xFF2196F3),
    "brown" to Color(0xFF795548),
    "cyan" to Color(0xFF00BCD4),
    "deep_orange" to Color(0xFFFF5722),
    "deep_purple" to Color(0xFF673AB7),
    "green" to Color(0xFF4CAF50),
    "indigo" to Color(0xFF3F51B5),
    "light_blue" to Color(0xFF03A9F4),
    "light_green" to Color(0xFF8BC34A),
    "lime" to Color(0xFFCDDC39),
    "orange" to Color(0xFFFF9800),
    "pink" to Color(0xFFE91E63),
    "purple" to Color(0xFF9C27B0),
    "red" to Color(0xFFF44336),
    "sakura" to Color(0xFFE88AA6),
    "teal" to Color(0xFF009688),
    "yellow" to Color(0xFFFFD600),
)

/**
 * The preset colours by name, in the order they are offered.
 *
 * The desktop icon carries a built form of each, because a launcher cannot be handed a colour that
 * only exists in our preferences. Reading the names off the map itself is what keeps the two lists
 * from drifting apart.
 */
internal val presetColorNames: List<String> = LegacyMiuixThemeSeeds.keys.toList()
