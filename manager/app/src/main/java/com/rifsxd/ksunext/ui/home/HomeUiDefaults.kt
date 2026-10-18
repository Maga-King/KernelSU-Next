package com.rifsxd.ksunext.ui.home

import android.content.Context
import java.io.File

/** A first-install preset, never a migration of an existing user's appearance. */
internal object HomeUiDefaults {
    const val PENDING_WALLPAPER = "aster_default_wallpaper_pending"
    const val WALLPAPER_ASSET = "home/default_wallpaper.image"
    private const val INITIALIZED = "aster_ui_defaults_initialized"

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (preferences.contains(INITIALIZED)) return

        // Even an existing installation with no wallpaper may have deliberately removed it.
        // Only seed an unconfigured installation; existing settings always take precedence.
        val fresh = preferences.all.isEmpty() &&
            !File(context.filesDir, "aster_home_wallpaper.jpg").exists() &&
            !File(context.filesDir, "home_wallpaper").exists()
        val editor = preferences.edit().putBoolean(INITIALIZED, true)
        if (fresh) {
            editor.putString("global_layout", "panorama")
                .putString("aster_layout", "panorama")
                .putBoolean("aster_panorama", true)
                .putBoolean("scene_sidebar_expanded", true)
                .putBoolean("home_wallpaper_enabled", true)
                .putBoolean("home_wallpaper_night_enabled", false)
                .putFloat("home_wallpaper_zoom", 1f)
                .putFloat("home_wallpaper_bias_x", -0.19073689f)
                .putFloat("home_wallpaper_bias_y", -0.06634891f)
                .putBoolean(PENDING_WALLPAPER, true)
        }
        // Only small preferences are initialized here, before any UI observes them.
        // HomeWallpaperStore copies the bundled image on its existing IO dispatcher.
        editor.apply()
    }
}
