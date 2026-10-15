// SPDX-License-Identifier: GPL-3.0-only
// Source: LyraVoid/Aster 84a9116606b01e436e9b58852acee9e8b9b9e627,
// ui/screen/Home.kt. Original scene layout/rendering retained; state and actions
// are supplied by KernelSU Next rather than the APatch capability chain.
package com.rifsxd.ksunext.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.LocalTime
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.shell.*
import top.yukonga.miuix.kmp.basic.*
import com.rifsxd.ksunext.ui.component.IndicatorSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.core.content.edit
import com.rifsxd.ksunext.ui.theme.WallpaperColorTheme
import com.rifsxd.ksunext.ui.theme.rememberWallpaperColorThemeState
import java.util.Locale

/** Presentation values from the existing KSUN backend. No APatch probes or commands. */
data class AsterHomeState(
    val title: String,
    val summary: String,
    val installed: Boolean,
    val needsAccess: Boolean,
    val firstLabel: String,
    val firstValue: String,
    val secondLabel: String,
    val secondValue: String,
    val deviceName: String,
    val checking: Boolean = false,
)

@Composable
fun AsterHomeScene(
    state: AsterHomeState,
    wallpaperState: HomeWallpaperState,
    onTools: () -> Unit,
    onRefresh: () -> Unit,
    onAppearance: () -> Unit,
    content: @Composable () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize().padding(
        start = LocalSceneRailWidth.current * LocalSceneProgress.current,
    )) {
        val sceneProgress = LocalSceneProgress.current
        val sidebarExpanded by rememberVisualFlag("scene_sidebar_expanded", true)
        val toggleSidebar = { setVisualFlag("scene_sidebar_expanded", !sidebarExpanded) }
        val layout = homeSceneLayout(
            width = maxWidth,
            height = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp),
        )
        val scrollState = rememberScrollState()

        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
                .clip(RoundedCornerShape(topStart = 26.dp * sceneProgress, bottomStart = 26.dp * sceneProgress))
                .background(MiuixTheme.colorScheme.background),
        ) {
            if (layout.across) {
                Row(Modifier.fillMaxSize()) {
                    SceneHero(
                        state = state,
                        wallpaperState = wallpaperState,
                        sidebarExpanded = sidebarExpanded,
                        onToggleSidebar = toggleSidebar,
                        onTools = onTools,
                        onAppearance = onAppearance,
                        scrollState = scrollState,
                        scrollsWithPage = false,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .padding(start = 14.dp, end = 7.dp, top = 12.dp, bottom = 12.dp),
                    )
                    // The photo no longer scrolls away with the page, so the refresh gesture belongs
                    // to the page pane alone instead of opening across both of them.
                    PullToRefresh(
                        isRefreshing = state.checking,
                        onRefresh = onRefresh,
                        modifier = Modifier.weight(0.54f).fillMaxHeight(),
                        refreshTexts = listOf(stringResource(R.string.refresh_pulling), stringResource(R.string.refresh_release), stringResource(R.string.refresh_refreshing), stringResource(R.string.refresh_complete)),
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(start = 7.dp, end = 14.dp, top = 16.dp),
                        ) {
                            SceneGreeting(state)
                            SceneInfoCards(bottomInset, content = content)
                        }
                    }
                }
            } else {
                PullToRefresh(
                    isRefreshing = state.checking,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    refreshTexts = listOf(stringResource(R.string.refresh_pulling), stringResource(R.string.refresh_release), stringResource(R.string.refresh_refreshing), stringResource(R.string.refresh_complete)),
                ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    Spacer(Modifier.height(12.dp))

                    SceneHero(
                        state = state,
                        wallpaperState = wallpaperState,
                        sidebarExpanded = sidebarExpanded,
                        onToggleSidebar = toggleSidebar,
                        onTools = onTools,
                        onAppearance = onAppearance,
                        scrollState = scrollState,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .height(layout.heroHeight),
                    )

                    SceneGreeting(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 18.dp),
                    )

                    SceneInfoCards(bottomInset, Modifier.fillMaxWidth()
                        .padding(horizontal = 14.dp).padding(top = 18.dp), content)
                }
                }
            }
        }

    }
}

@Composable
private fun SceneHero(
    state: AsterHomeState,
    wallpaperState: HomeWallpaperState,
    sidebarExpanded: Boolean,
    onToggleSidebar: () -> Unit,
    onTools: () -> Unit,
    onAppearance: () -> Unit,
    scrollState: ScrollState,
    shape: Shape,
    modifier: Modifier = Modifier,
    scrollsWithPage: Boolean = true,
) {
    Box(
        Modifier
            .then(modifier)
            .pointerInput(sidebarExpanded) {
                detectTapGestures(onDoubleTap = { onToggleSidebar() })
            }
            .clip(shape),
    ) {
        if (!wallpaperState.isReady) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MiuixTheme.colorScheme.primaryContainer,
                                MiuixTheme.colorScheme.secondaryContainer,
                            )
                        )
                    )
            )
            if (wallpaperState.isInitialized) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (wallpaperState.phase == HomeWallpaperPhase.LOADING) {
                                R.string.home_wallpaper_loading
                            } else {
                                R.string.home_wallpaper_empty
                            }
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                    // Without this the only way into the wallpaper sheet is the rail icon
                    // at the very bottom of the scene, which is easy to miss while the
                    // hero is still an empty placeholder.
                    if (wallpaperState.phase != HomeWallpaperPhase.LOADING) {
                        Button(
                            onClick = onAppearance,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(stringResource(R.string.home_wallpaper_choose))
                        }
                    }
                }
            }
        }
        HomeWallpaperImage(
            state = wallpaperState,
            modifier = Modifier
                .fillMaxSize()
                // Upstream's fillMaxHeight(1.35f) is clamped to the parent's
                // maximum height. Use its actual measured size without an invalid fraction.
                .graphicsLayer {
                    translationY = (if (scrollsWithPage) scrollState.value * SceneParallaxRate else 0f)
                        .coerceAtMost(size.height * SceneParallaxRate / SceneParallaxOverscan)
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // Fade the photo into the panel colour instead of a black
                        // scrim, so the status strip stays readable on any wallpaper.
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.78f to MiuixTheme.colorScheme.background.copy(alpha = 0.88f),
                        0.90f to MiuixTheme.colorScheme.background,
                        1f to MiuixTheme.colorScheme.background,
                    )
                )
        )
        // As the hero slides under the page top it would otherwise show a sliced
        // photo edge. Wash its own top with the panel colour, in proportion to how
        // far it has scrolled, so it dissolves instead of being cut. At rest the
        // wash is fully transparent.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (scrollsWithPage) {
                        (scrollState.value / size.height).coerceIn(0f, 1f) * 0.95f
                    } else {
                        0f
                    }
                }
                .background(
                    Brush.verticalGradient(
                        0f to MiuixTheme.colorScheme.background,
                        0.42f to Color.Transparent,
                    )
                )
        )
        IconButton(
            onClick = onToggleSidebar,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp),
            backgroundColor = Color.Transparent,
        ) {
            Icon(
                painter = painterResource(if (sidebarExpanded) R.drawable.wallpaper_expand else R.drawable.wallpaper_collapse),
                contentDescription = stringResource(if (sidebarExpanded) R.string.scene_expand else R.string.scene_restore),
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SceneRootStatus(
                state = state,
                onTools = onTools,
            )
            SceneStatusStrip(state = state)
        }
    }
}

@Composable
private fun SceneGreeting(
    state: AsterHomeState,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = listOfNotNull(
                sceneGreeting(),
                state.deviceName.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = sceneQuote(),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SceneInfoCards(bottomInset: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        Spacer(Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun SceneStatusStrip(
    state: AsterHomeState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SceneStatusColumn(
            title = state.firstLabel,
            value = state.firstValue,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .width(1.dp)
                .height(30.dp)
                .background(MiuixTheme.colorScheme.dividerLine)
        )
        SceneStatusColumn(
            title = state.secondLabel,
            value = state.secondValue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SceneStatusColumn(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title.uppercase(),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SceneRootStatus(state: AsterHomeState, onTools: () -> Unit) {
    val needsAccess = state.needsAccess
    val statusColor = when {
        needsAccess -> MiuixTheme.colorScheme.error
        state.installed -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(statusColor.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.installed && !needsAccess) MiuixIcons.Ok else MiuixIcons.Lock,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = statusColor,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            // The two patch values below already explain the healthy state. Only exceptions
            // need a second paragraph; never substitute a detected patch for usable root.
            if (state.summary.isNotBlank()) {
                Text(
                    text = state.summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        IconButton(onClick = onTools, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = stringResource(R.string.home_more),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private const val SceneParallaxOverscan = 1.35f
private const val SceneParallaxRate = 0.35f

@Composable
private fun sceneGreeting(): String {
    val hour = LocalTime.now().hour
    val res = when (hour) {
        in 5..11 -> R.string.home_scene_greeting_morning
        in 12..17 -> R.string.home_scene_greeting_afternoon
        in 18..22 -> R.string.home_scene_greeting_evening
        else -> R.string.home_scene_greeting_night
    }
    return stringResource(res)
}

@Composable
private fun sceneQuote(): String {
    // The reader's own lines when they have written any, and the set this app ships with when they
    // have not; see HomeSceneQuote for how the day turns them over.
    val builtIn = stringArrayResource(R.array.home_scene_quotes)
    val custom by rememberVisualChoice("aster_scene_quote", "")
    val day = LocalDate.now().dayOfYear
    return remember(custom, builtIn, day) {
        HomeSceneQuote.resolve(custom, builtIn.toList(), day)
    }
}

@Composable
fun AsterHomeWallpaperSheet(
    show: Boolean,
    state: HomeWallpaperState,
    onDismissRequest: () -> Unit,
    onNightEnabledChange: (Boolean) -> Unit,
    onChooseImage: (HomeWallpaperSlot) -> Unit,
    onRemoveImage: (HomeWallpaperSlot) -> Unit,
    onCropChange: (HomeWallpaperSlot, HomeWallpaperCrop) -> Unit,
    onRegenerateColor: () -> Unit,
) {
    val homeLayoutFamily by rememberGlobalLayout()
    val scenePreferences = LocalContext.current.getSharedPreferences("settings", 0)
    // The sheet edits one wallpaper at a time. It opens on whichever theme is showing, and the
    // buttons below move to the other one when the user asks for it.
    var editingSlot by remember(show) { mutableStateOf(state.activeSlot) }
    // Flipping the dark-wallpaper switch moves the editor onto the picture it just turned on, so
    // that choice can be made without hunting for the slot buttons.
    LaunchedEffect(state.nightEnabled) {
        editingSlot = if (state.nightEnabled) HomeWallpaperSlot.NIGHT else HomeWallpaperSlot.LIGHT
    }
    val slotState = state.slot(editingSlot)
    val hasAnyWallpaper = state.light.hasImage || state.night.hasImage
    val busy = slotState.phase == HomeWallpaperPhase.LOADING
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val useWallpaperColor = wallpaperColorTheme.enabled
    val wallpaperColorSeed = wallpaperColorTheme.seed
    var zoom by remember(show, editingSlot, slotState.imagePath, slotState.revision, slotState.crop) {
        mutableFloatStateOf(slotState.crop.zoom)
    }
    var biasX by remember(show, editingSlot, slotState.imagePath, slotState.revision, slotState.crop) {
        mutableFloatStateOf(slotState.crop.biasX)
    }
    var biasY by remember(show, editingSlot, slotState.imagePath, slotState.revision, slotState.crop) {
        mutableFloatStateOf(slotState.crop.biasY)
    }
    val draftCrop = HomeWallpaperCrop(
        zoom = zoom,
        biasX = biasX,
        biasY = biasY,
    )

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.home_appearance),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Which family Home is drawn in is one choice with as many answers as there are
            // families, so it is offered as a list rather than as a switch, and the same list the
            // Settings row opens.
            var choosingLayoutFamily by remember { mutableStateOf(false) }
            SceneChoiceRow(
                title = stringResource(R.string.global_layout_title),
                value = stringResource(homeLayoutFamily.label),
                onClick = { choosingLayoutFamily = true },
            )
            GlobalLayoutDialog(
                show = choosingLayoutFamily,
                selected = homeLayoutFamily,
                onDismissRequest = { choosingLayoutFamily = false },
            )

            // The rail extras only exist in panorama mode, so their switches travel with the mode
            // they belong to.
            if (homeLayoutFamily == GlobalLayout.Panorama) {
                val showSceneClock by rememberVisualFlag(
                    SceneRailClockFlag,
                    true,
                )
                val showSceneLabels by rememberVisualFlag(
                    SceneRailLabelsFlag,
                    true,
                )
                SceneSwitchRow(
                    title = stringResource(R.string.home_scene_clock_switch),
                    summary = stringResource(R.string.home_scene_clock_switch_summary),
                    checked = showSceneClock,
                    onCheckedChange = {
                        setVisualFlag(
                            SceneRailClockFlag,
                            it,
                        )
                    },
                )
                // The shape of the clock only matters while the clock is there, so the choice
                // follows the switch the same way the rail options follow the mode they belong to.
                if (showSceneClock) {
                    val clockStyle by rememberVisualChoice(
                        SceneClockStyleFlag,
                        SceneClockStyle.Default.value,
                    )
                    var choosingClockStyle by remember { mutableStateOf(false) }
                    // A row that names the shape in use and opens the list of them, the way
                    // Settings offers every other either-or choice: four filled buttons would
                    // shout louder than the switches around them.
                    SceneChoiceRow(
                        title = stringResource(R.string.home_scene_clock_style),
                        value = stringResource(
                            SceneClockStyle.fromValue(clockStyle).label,
                        ),
                        onClick = { choosingClockStyle = true },
                    )
                    OverlayDialog(
                        show = choosingClockStyle,
                        title = stringResource(R.string.home_scene_clock_style),
                        onDismissRequest = { choosingClockStyle = false },
                    ) {
                        SceneClockStyle.entries.forEach { choice ->
                            RadioButtonPreference(
                                title = stringResource(choice.label),
                                summary = stringResource(choice.summary),
                                selected = choice.value == clockStyle,
                                onClick = {
                                    choosingClockStyle = false
                                    setVisualChoice(
                                        SceneClockStyleFlag,
                                        choice.value,
                                    )
                                },
                            )
                        }
                    }
                }
                SceneSwitchRow(
                    title = stringResource(R.string.home_scene_labels_switch),
                    summary = stringResource(R.string.home_scene_labels_switch_summary),
                    checked = showSceneLabels,
                    onCheckedChange = {
                        setVisualFlag(
                            SceneRailLabelsFlag,
                            it,
                        )
                    },
                )

                // The line under the greeting belongs to this scene alone, so it is offered here
                // rather than in the settings the other families share. The row names the line in
                // use, which is the reader's own words once they have written any, and one of the
                // built-in ones until then.
                var editingSceneQuote by remember { mutableStateOf(false) }
                SceneChoiceRow(
                    title = stringResource(R.string.home_scene_quote_title),
                    value = sceneQuote(),
                    onClick = { editingSceneQuote = true },
                )
                HomeSceneQuoteDialog(
                    show = editingSceneQuote,
                    onDismiss = { editingSceneQuote = false },
                    onApply = { written ->
                        scenePreferences.edit {
                            // Clearing the field is how the scene is put back on the built-in
                            // lines, so it is stored as nothing written rather than as blanks.
                            val lines = written.trim()
                            if (lines.isEmpty()) {
                                remove("aster_scene_quote")
                            } else {
                                putString("aster_scene_quote", lines)
                            }
                        }
                    },
                )
            }


            SceneSwitchRow(
                title = stringResource(R.string.home_wallpaper_color_theme),
                summary = stringResource(
                    if (state.hasImage) {
                        R.string.home_wallpaper_color_theme_summary
                    } else {
                        R.string.settings_wallpaper_color_theme_no_wallpaper
                    }
                ),
                checked = useWallpaperColor,
                onCheckedChange = { WallpaperColorTheme.setEnabled(it) },
                enabled = hasAnyWallpaper,
            )

            // The reading happens on its own, so the only thing worth showing underneath is what it
            // came up with: the colour, or the fact that it could not find one and can try again.
            when {
                wallpaperColorSeed != 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(wallpaperColorSeed))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.home_wallpaper_color_seed) + " · " +
                            String.format(Locale.ROOT, "#%06X", wallpaperColorSeed and 0xFFFFFF),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                wallpaperColorTheme.deriving -> Text(
                    text = stringResource(R.string.home_wallpaper_color_deriving),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                wallpaperColorTheme.failed && hasAnyWallpaper -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_wallpaper_color_failed),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        text = stringResource(R.string.home_wallpaper_color_retry),
                        onClick = onRegenerateColor,
                    )
                }
            }

            // The dark theme can carry a wallpaper of its own. Leaving this off is exactly what the
            // app has always done, so the light picture keeps serving both themes until a second one
            // is chosen.
            SceneSwitchRow(
                title = stringResource(R.string.home_wallpaper_night_switch),
                summary = stringResource(R.string.home_wallpaper_night_switch_summary),
                checked = state.nightEnabled,
                onCheckedChange = onNightEnabledChange,
                enabled = hasAnyWallpaper,
            )

            if (state.nightEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SceneChoiceButton(
                        label = stringResource(R.string.home_wallpaper_slot_light),
                        selected = editingSlot == HomeWallpaperSlot.LIGHT,
                        onClick = { editingSlot = HomeWallpaperSlot.LIGHT },
                        modifier = Modifier.weight(1f),
                    )
                    SceneChoiceButton(
                        label = stringResource(R.string.home_wallpaper_slot_night),
                        selected = editingSlot == HomeWallpaperSlot.NIGHT,
                        onClick = { editingSlot = HomeWallpaperSlot.NIGHT },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when {
                busy -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_loading),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                // A slot that never held a picture is simply empty; only a picture that was recorded
                // and has since gone missing is worth this warning.
                slotState.phase == HomeWallpaperPhase.MISSING && slotState.imagePath != null -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_missing),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                slotState.phase == HomeWallpaperPhase.ERROR -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_error),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                slotState.hasImage -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clipToBounds(),
                        ) {
                            HomeWallpaperSlotImage(
                                slot = slotState.copy(crop = draftCrop),
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.72f to Color.Transparent,
                                            1f to MiuixTheme.colorScheme.background,
                                        )
                                    )
                            )
                        }
                    }

                    WallpaperSlider(
                        label = stringResource(R.string.home_wallpaper_scale),
                        value = zoom,
                        valueRange = HomeWallpaperMinZoom..HomeWallpaperMaxZoom,
                        enabled = !busy,
                        onValueChange = { zoom = it },
                        onValueChangeFinished = { onCropChange(editingSlot, draftCrop) },
                    )
                    WallpaperSlider(
                        label = stringResource(R.string.home_wallpaper_horizontal),
                        value = biasX,
                        valueRange = -1f..1f,
                        enabled = !busy,
                        onValueChange = { biasX = it },
                        onValueChangeFinished = { onCropChange(editingSlot, draftCrop) },
                    )
                    WallpaperSlider(
                        label = stringResource(R.string.home_wallpaper_vertical),
                        value = biasY,
                        valueRange = -1f..1f,
                        enabled = !busy,
                        onValueChange = { biasY = it },
                        onValueChangeFinished = { onCropChange(editingSlot, draftCrop) },
                    )
                }

                else -> {
                    Text(
                        text = stringResource(
                            if (editingSlot == HomeWallpaperSlot.NIGHT) {
                                R.string.home_wallpaper_night_empty
                            } else {
                                R.string.home_wallpaper_empty
                            }
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onChooseImage(editingSlot) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                ) {
                    Text(
                        text = stringResource(
                            when {
                                slotState.hasImage -> R.string.home_wallpaper_change
                                editingSlot == HomeWallpaperSlot.NIGHT ->
                                    R.string.home_wallpaper_night_choose

                                else -> R.string.home_wallpaper_choose
                            }
                        ),
                        style = MiuixTheme.textStyles.button,
                    )
                }
                if (slotState.hasImage) {
                    TextButton(
                        text = stringResource(R.string.home_wallpaper_remove),
                        onClick = { onRemoveImage(editingSlot) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(16.dp))
        IndicatorSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SceneChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                contentColor = MiuixTheme.colorScheme.onSurface,
            )
        },
    ) {
        Text(text = label, style = MiuixTheme.textStyles.button)
    }
}

@Composable
private fun WallpaperSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
fun AsterHomeActionsSheet(
    showMore: Boolean,
    showReboot: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onShowRebootChange: (Boolean) -> Unit,
    canReboot: Boolean,
    supportsUserspaceReboot: Boolean,
    canUninstall: Boolean,
    onUninstallClick: () -> Unit,
    onInstallClick: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    onReboot: (String) -> Unit,
    onDangerousReboot: (String) -> Unit,
) {
    val dismiss = {
        onShowMoreChange(false)
        onShowRebootChange(false)
    }
    WindowBottomSheet(
        show = showMore || showReboot,
        title = stringResource(if (showReboot) R.string.reboot else R.string.home_more),
        onDismissRequest = dismiss,
        startAction = if (showReboot) {
            {
                IconButton(onClick = {
                    onShowMoreChange(true)
                    onShowRebootChange(false)
                }) {
                    Icon(MiuixIcons.Back, stringResource(R.string.home_more))
                }
            }
        } else null,
    ) {
        AnimatedContent(
            targetState = showReboot,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "home_actions",
        ) { rebootPage ->
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (rebootPage) {
                    HomeRebootOptions(onDismiss = dismiss, onReboot = onReboot, onDangerousReboot = onDangerousReboot, supportsUserspaceReboot = supportsUserspaceReboot)
                } else {
                    PopupMenuItem(
                        icon = MiuixIcons.Import,
                        text = stringResource(R.string.mode_select_page_title),
                        onClick = onInstallClick,
                    )
                    if (canReboot) {
                        PopupMenuItem(
                            icon = MiuixIcons.Reset,
                            text = stringResource(R.string.reboot),
                            onClick = { onShowRebootChange(true) },
                        )
                    }
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth().height(1.dp)
                            .background(MiuixTheme.colorScheme.dividerLine)
                    )
                    PopupMenuItem(
                        icon = MiuixIcons.Help,
                        text = stringResource(R.string.home_more_menu_feedback_or_suggestion),
                        onClick = onFeedback,
                    )
                    PopupMenuItem(
                        icon = MiuixIcons.Info,
                        text = stringResource(R.string.home_more_menu_about),
                        onClick = onAbout,
                    )
                    if (canUninstall) {
                        PopupMenuItem(
                            icon = MiuixIcons.Delete,
                            text = stringResource(R.string.home_dialog_uninstall_title),
                            onClick = onUninstallClick,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRebootOptions(
    onDismiss: () -> Unit,
    onReboot: (String) -> Unit,
    onDangerousReboot: (String) -> Unit,
    supportsUserspaceReboot: Boolean,
) {
    PopupMenuItem(
        icon = MiuixIcons.Reset,
        text = stringResource(R.string.reboot),
        onClick = {
            onDismiss()
            onReboot("")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Refresh,
        text = stringResource(R.string.reboot_soft),
        onClick = {
            onDismiss()
            onReboot("soft_reboot")
        },
    )
    if (supportsUserspaceReboot) {
        PopupMenuItem(
            icon = MiuixIcons.Refresh,
            text = stringResource(R.string.reboot_userspace),
            onClick = {
                onDismiss()
                onReboot("userspace")
            },
        )
    }
    PopupMenuItem(
        icon = MiuixIcons.Reset,
        text = stringResource(R.string.reboot_recovery),
        onClick = {
            onDismiss()
            onReboot("recovery")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Reset,
        text = stringResource(R.string.reboot_bootloader),
        onClick = {
            onDismiss()
            onReboot("bootloader")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Download,
        text = stringResource(R.string.reboot_download),
        onClick = {
            onDismiss()
            onDangerousReboot("download")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Import,
        text = stringResource(R.string.reboot_edl),
        onClick = {
            onDismiss()
            onDangerousReboot("edl")
        },
    )
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val contentColor = if (!enabled) {
        MiuixTheme.colorScheme.disabledOnSurface
    } else if (destructive) {
        MiuixTheme.colorScheme.error
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, color = contentColor)
    }
}

@Composable
fun AsterStandardStatusCard(
    title: String,
    subtitle: String?,
    mode: String?,
    isWorking: Boolean,
    icon: ImageVector,
    iconColor: Color,
    cardColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = cardColor),
            onClick = onClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    StatusCardIcon(
                        isWorking = isWorking,
                        icon = icon,
                        iconColor = iconColor,
                        size = 110.dp,
                    )
                }

                if (mode != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            text = mode,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(1.dp))
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCardIcon(
    isWorking: Boolean,
    icon: ImageVector,
    iconColor: Color,
    size: Dp,
) {
    if (isWorking) {
        Icon(
            modifier = Modifier.size(size),
            painter = painterResource(R.drawable.status_check_circle_outline),
            tint = iconColor,
            contentDescription = null,
        )
    } else {
        Icon(
            modifier = Modifier.size(size),
            imageVector = icon,
            tint = iconColor,
            contentDescription = null,
        )
    }
}

@Composable
fun AsterCountCard(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            extra()
        }
    }
}

@Composable
fun AsterDeviceInfoItem(title: String, content: String) {
    Column {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = content,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun AsterUpdateAvailableCard(
    title: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
        ),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Update,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                )
                Text(
                    text = stringResource(R.string.home_update_available_summary),
                    style = MiuixTheme.textStyles.body2,
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                )
            }
        }
    }
}

/** Original DeviceInfoCard container and spacing; values are provided by KSUN. */
@Composable
fun AsterDeviceInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SceneChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun HomeSceneQuoteDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit = {},
) {
    val controlParam = rememberTextFieldState()
    val scenePreferences = LocalContext.current.getSharedPreferences("settings", 0)

    LaunchedEffect(show) {
        if (show) {
            val stored = scenePreferences
                .getString("aster_scene_quote", null)
                .orEmpty()
            controlParam.edit { replace(0, length, stored) }
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.home_scene_quote_title),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = controlParam,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.home_scene_quote_hint),
                lineLimits = TextFieldLineLimits.MultiLine(
                    minHeightInLines = 3,
                    maxHeightInLines = 6,
                ),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val lines = controlParam.text.toString()
                        onDismiss()
                        onApply(lines)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}
