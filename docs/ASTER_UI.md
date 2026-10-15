# Aster UI adaptation

KernelSU-Next now offers two layouts under **Settings → Customization → Panoramic layout**:

- **On (default):** a side navigation rail and a panoramic Home with optional personal wallpaper.
- **Off:** a labelled floating bottom navigation bar and a compact Home without the wallpaper hero.

The choice applies immediately and is saved across restarts. Use the wallpaper button on the
panoramic Home to choose or clear an image. Images are resized and copied into private app storage;
no storage permission is required. Standard mode keeps the chosen wallpaper for the next switch.

Home, Superuser, Modules and Settings share rounded cards, quieter surfaces and revised typography.
Module actions wrap on narrow screens. Navigation remains visible on primary pages; detail pages
and installation logs use the full screen. Unavailable root tabs explain their requirements when
tapped. Existing KernelSU operations, package identity, app lock and signing configuration remain
in the KernelSU-Next implementation.

## Attribution and implementation

UI reference: [LyraVoid/Aster](https://github.com/LyraVoid/Aster), revision
[`84a9116606b01e436e9b58852acee9e8b9b9e627`](https://github.com/LyraVoid/Aster/tree/84a9116606b01e436e9b58852acee9e8b9b9e627).
In particular, `ui/shell/AsterAppShell.kt`, `ui/shell/HomeSceneChrome.kt`, the Home layout,
theme and grouped settings informed this adaptation. Credit belongs to Aster's contributors and
the Miuix project for the original visual design. Both projects' root licenses are GPL-3.0;
the existing repository license applies to this adaptation.

This is an adaptation of Aster's visual layout onto the existing Android Compose/Material3
components. It does not import APatch/KernelPatch code or require Aster's newer Kotlin/Miuix
toolchain. It does not copy the third-party wallpaper or custom font shown in Aster's screenshots.
Aster-specific KPM/APM entries are represented by KernelSU-Next's own Superuser and Modules pages.
Advanced Aster options such as downloadable fonts, alternate launch icons and wallpaper-derived
palettes are not included.

## Validation

UI/resource compilation: `cd manager && ./gradlew :app:compileDebugKotlin`.
Static Android checks: `./gradlew :app:lintDebug`.

Local resource, KSP, Kotlin and Java compilation and `lintDebug` passed on 2026-10-15 with
JDK 21 and Gradle 9.2.0. Lint has no Error/Fatal findings; existing warning-level findings remain.
The 19 new strings are present in all 23 existing translation folders.
A local, untracked Gradle init script selected the installed Windows NDK 28.2 for UI-only checks
to avoid downloading a native toolchain. Tracked builds still use NDK 29.0.14206865. No native
compilation, signed APK packaging or on-device verification is implied by those results.

These checks do not validate an installed APK or root operations. A complete APK still requires
the repository's normal ksud/native build and its configured release signing. Do not substitute
an old daemon binary when packaging this UI.

Device review should cover both layouts, switching and restart persistence, light/dark/AMOLED,
Chinese/English, large fonts, landscape, returning Home from each tab, scrolling to the final
setting/module action, wallpaper choose/replace/clear, app lock and existing deep links. No
connected online device was available during the initial adaptation.
