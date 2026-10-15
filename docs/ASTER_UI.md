# Aster UI source port

This branch transplants UI source from [lyravoid/Aster](https://github.com/lyravoid/Aster),
revision [`84a9116606b01e436e9b58852acee9e8b9b9e627`](https://github.com/lyravoid/Aster/tree/84a9116606b01e436e9b58852acee9e8b9b9e627),
onto KernelSU Next. Both repositories use GPL-3.0. Original source comments are retained;
credit also belongs to Miuix and the FolkPatch-derived floating navigation components.

## UI source and adaptation boundaries

- `ui/home/AsterHomeScene.kt` extracts Aster's HomeScenePanel, SceneHero, greeting, status
  columns, status heading, actions/reboot sheets and wallpaper appearance controls. The
  original geometry, wallpaper gradient, parallax, landscape split and Miuix widgets remain.
  `AsterHomeState` supplies KernelSU data instead of APatch capabilities.
- `ui/shell/HomeSceneChrome.kt` contains Aster's clock, battery, scene rail and blurred backdrop.
- `ui/home/HomeWallpaper*` preserves Aster's day/night slots, private image storage, crop and
  crossfade. Coil imports are adapted to the manager's existing Coil 2 image loader. A wallpaper
  selected in the earlier preview migrates automatically, with the original retained as backup.
- `ui/component/FloatingBottomBar.kt`, its drag/highlight helpers and shell navigation components
  come from Aster. Standard mode uses Miuix bottom navigation; panorama uses the scene rail on
  Home and the floating capsule on other primary pages. Detail pages have no primary navigation.
- Theme seed, wallpaper palette extraction and palette specification code come from Aster.
  Miuix supplies the actual theme; existing KSUN detail screens receive matching Material colors.
- Settings uses Aster's grouped preferences and navigation components, connected to KSUN options.
- Primary transitions use Aster's EaseInOut curve and distance-based duration. Detail transitions
  retain its separate slide (400 ms), fade (300 ms), and scale (500 ms) tracks. Rail and floating
  navigation translate and fade with the Home transition. KSUN's NavHost and route handling remain;
  this does not replace them with Aster's persistent primary-page Pager.

Root authorization, module management, installation, native code, daemon commands, kernel
features, app lock and intent handling remain KernelSU Next implementations. APatch's backend,
KernelPatch probes and separate KPM/APM management are not imported. UI labels describe KSUN's
actual state. Existing warnings and protected installation conditions remain in place.

Both layouts are retained and can be switched in Settings. Home appearance also offers the
layout choice, wallpaper controls and clock styles. No screenshot wallpaper or third-party font
is bundled. The preview retains the user's existing selected wallpaper.

## Build

The real Miuix 0.9.3 source API requires a newer toolchain: compile SDK 37, AGP 9.1.1,
Gradle 9.3.1, Kotlin 2.4.10 and Compose BOM 2026.08.00. The native toolchain remains NDK 29.
Target SDK and native manager behavior are not changed for this UI migration.

`./gradlew :app:assemblePreview` builds `com.rifsxd.ksunext.aster.preview` with the local Android
debug key and label **Aster UI Preview** (**Aster 预览版**). It updates the previous preview
without replacing the normal manager. Launcher shortcuts target this separate package.
The preview enables the release R8 and resource optimizations and is not debuggable. Its signing
certificate remains the local debug certificate so updates preserve the preview's data.

Automatic Root requests and installation entry points remain enabled, as requested. The local
UI preview does not bundle the daemon/kernel installation assets and is not a full manager
release. A complete functional release requires the repository's normal ksud/kernel asset build
and a signing identity accepted by the kernel. No official private signing key is available here.

## Startup and rendering

The decorative rotating startup overlay is removed. The normal Android launch window remains,
and pending installation/navigation intents still wait for the existing application lock.
Wallpaper initialization is distinct from an empty selection, preventing a transient image-picker
prompt and premature clearing of the saved theme seed. Wallpaper crossfades read animation state
in the graphics layer; theme and shell observe only the palette fields they actually render.

Initial Root shell acquisition, module counts, and module-update HTTP checks run outside UI
composition/on IO. Automatic Root requests and the original KSUN authorization checks remain.
Pull-to-refresh reloads Home data without restarting the Activity. Core refresh work controls
the refresh indicator; card data effects re-run without clearing their previous values or UI state.
The manager-only daemon installation order is unchanged. Dark mode bridges Material's content
foreground into the Miuix shell so legacy cards retain readable text.

## Verification

Device verification uses the side-by-side preview on OnePlus PJZ110 (Android 17, 120 Hz).
`assemblePreview` and `lintPreview` passed (zero lint errors; existing warnings remain).
Cold-start recording showed the saved wallpaper without the empty-image selection prompt.
Appearance, nested clock and quote dialogs, Settings appearance details, and the layout dialog
in both panorama and standard mode were opened and dismissed without a process crash. The
saved wallpaper and custom quote remained. Dark-mode issue/contributor cards were visually checked.
Repeated pull-to-refresh retained the same ActivityRecord and an expanded device-information card;
the previous implementation created a new ActivityRecord on every pull.

On the optimized preview, eight Home/Settings round trips recorded 613 frames, 23 janky (3.75%),
p95 29 ms and p99 40 ms. Five appearance-sheet open/close cycles recorded 455 frames,
16 janky (3.52%), p95 14 ms and p99 21 ms. These are Android `dumpsys gfxinfo` samples,
not an FPS estimate or a controlled official-Aster benchmark. The previous debug sample was
514 frames, 46 janky (8.95%), p95 61 ms and p99 150 ms; build mode and animation changes
mean the samples do not isolate one individual optimization.

Root, flashing, module mutation and reboot operations were not executed in this UI-only preview.
The preview signature remains SHA-256
`47f1bf86449c4d3021dad441982204c0f8531fdc388e1251ecb31fb9d04a3a4c`.
