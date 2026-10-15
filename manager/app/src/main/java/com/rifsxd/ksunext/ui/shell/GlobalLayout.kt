/* Ported from LyraVoid/Aster (GPL-3.0); see docs/ASTER_UI.md. */
package com.rifsxd.ksunext.ui.shell

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.rifsxd.ksunext.ksuApp
import com.rifsxd.ksunext.R
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

/**
 * Top level layout family. [Panorama] owns its chrome (wallpaper scene on Home, floating
 * navigation elsewhere); [Standard] keeps the classic bottom bar or sidebar shell.
 *
 * The label names the family and the summary says what living in it means, so the list of them can
 * be offered wherever the choice is made, the way a destination carries its own label.
 */
enum class GlobalLayout(
    val value: String,
    @param:StringRes val label: Int,
    @param:StringRes val summary: Int,
) {
    Panorama("panorama", R.string.global_layout_panorama, R.string.global_layout_panorama_summary),
    Standard("standard", R.string.global_layout_standard, R.string.global_layout_standard_summary);

    companion object {
        fun fromValue(value: String?): GlobalLayout =
            entries.firstOrNull { it.value == value } ?: Standard
    }
}

private const val GlobalLayoutKey = "global_layout"
private const val LegacyPanoramaKey = "aster_panorama"

private fun readGlobalLayout(preferences: SharedPreferences): GlobalLayout = when {
    // The existing customization switch writes this key; it must remain authoritative.
    preferences.contains(LegacyPanoramaKey) -> if (preferences.getBoolean(LegacyPanoramaKey, true)) {
        GlobalLayout.Panorama
    } else GlobalLayout.Standard
    preferences.contains(GlobalLayoutKey) -> GlobalLayout.fromValue(preferences.getString(GlobalLayoutKey, null))
    preferences.contains("aster_layout") -> GlobalLayout.fromValue(preferences.getString("aster_layout", null))
    else -> GlobalLayout.Panorama
}

@Composable
fun rememberGlobalLayout(): State<GlobalLayout> {
    val preferences = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val state = remember(preferences) {
        mutableStateOf(readGlobalLayout(preferences))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == GlobalLayoutKey || key == LegacyPanoramaKey || key == "aster_layout" || key == null) {
                state.value = readGlobalLayout(prefs)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.value = readGlobalLayout(preferences)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setGlobalLayout(layout: GlobalLayout) {
    ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
        putString(GlobalLayoutKey, layout.value)
        putString("aster_layout", layout.value)
        putBoolean(LegacyPanoramaKey, layout == GlobalLayout.Panorama)
    }
}

/**
 * The layout family in force, for callers that are not drawing a screen.
 *
 * Whether the wallpaper is a colour source depends on which Home is in use, and the desktop icon
 * has to be able to ask that question without being on one.
 */
fun currentGlobalLayout(): GlobalLayout =
    readGlobalLayout(ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE))

// Persist visual choices independently of the standard navigation layout.
@Composable
fun rememberVisualFlag(key: String, default: Boolean): State<Boolean> {
    val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val state = remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, default)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
            if (changed == key || changed == null) state.value = p.getBoolean(key, default)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
fun setVisualFlag(key: String, value: Boolean) {
    ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean(key, value) }
}

/**
 * Some visual choices have more than two answers, such as the shape of the scene clock. They are
 * stored the same way as the flags above and read back as the raw string, which the caller turns
 * into its own type so that an unknown stored value can fall back to its default.
 */
@Composable
fun rememberVisualChoice(key: String, default: String): State<String> {
    val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val state = remember(prefs, key) { mutableStateOf(prefs.getString(key, null) ?: default) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
            if (changed == key || changed == null) state.value = p.getString(key, null) ?: default
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setVisualChoice(key: String, value: String) {
    ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putString(key, value) }
}
val LocalFloatingNavigationInset = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.unit.Dp(0f) }
val LocalSceneProgress = androidx.compose.runtime.compositionLocalOf { 1f }
val LocalSceneRailWidth = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.unit.Dp(0f) }
val LocalSceneActive = androidx.compose.runtime.compositionLocalOf { false }

/**
 * The families Home can be drawn in, offered as one either-or choice. Settings and the Home
 * appearance sheet both open this dialog, so the two entry points cannot drift apart when another
 * family is added.
 */
@Composable
fun GlobalLayoutDialog(
    show: Boolean,
    selected: GlobalLayout,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.global_layout_title),
        onDismissRequest = onDismissRequest,
    ) {
        GlobalLayout.entries.forEach { layout ->
            RadioButtonPreference(
                title = stringResource(layout.label),
                summary = stringResource(layout.summary),
                selected = selected == layout,
                onClick = {
                    onDismissRequest()
                    setGlobalLayout(layout)
                },
            )
        }
    }
}
