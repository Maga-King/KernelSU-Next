// Scene layout and navigation extracted from lyravoid/Aster @ 84a9116606b01e436e9b58852acee9e8b9b9e627.
// GPL-3.0. Routes and availability are supplied by KernelSU Next.
package com.rifsxd.ksunext.ui.shell

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.rifsxd.ksunext.ui.component.FloatingBottomBar
import com.rifsxd.ksunext.ui.component.FloatingBottomBarItem
import com.rifsxd.ksunext.ui.home.HomeWallpaperViewModel
import com.rifsxd.ksunext.ui.home.LocalHomeWallpaperViewModel
import com.rifsxd.ksunext.ui.theme.LocalThemeModeState
import com.rifsxd.ksunext.ui.theme.WallpaperColorTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AsterAppShell(
    navController: NavHostController,
    fullFeatured: Boolean,
    navigationEnabled: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    content: @Composable (Modifier) -> Unit,
) {
    val globalLayout by rememberGlobalLayout()
    val panorama = globalLayout == GlobalLayout.Panorama
    val wallpaperViewModel: HomeWallpaperViewModel = viewModel()
    val wallpaperState by wallpaperViewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = LocalThemeModeState.current.isDark
    LaunchedEffect(darkTheme) { wallpaperViewModel.setDarkTheme(darkTheme) }
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val currentDestination = PrimaryDestination.entries.firstOrNull { it.direction.route == currentRoute }
        ?: if (currentRoute == null) PrimaryDestination.Home else null
    val onHome = currentDestination == PrimaryDestination.Home
    val primaryPage = currentDestination != null
    val destinations = remember(fullFeatured) { PrimaryDestination.entries.filter { fullFeatured || !it.rootRequired } }
    val navigator = navController.rememberDestinationsNavigator()
    val select: (PrimaryDestination) -> Unit = { destination ->
        if (destination != currentDestination) navigator.navigate(destination.direction) {
            popUpTo(NavGraphs.root.startRoute) { saveState = true }
            launchSingleTop = true
            restoreState = destination != PrimaryDestination.Home
        }
    }
    val sceneExpanded by rememberVisualFlag("scene_sidebar_expanded", true)
    val floatingPreferred by rememberVisualFlag("floating_navigation", false)
    val floatingShell = panorama || floatingPreferred
    val floatingNavigation = navigationEnabled && primaryPage && floatingShell && !(panorama && onHome)
    val bottomBarVisible = navigationEnabled && primaryPage && !floatingShell
    val blurEnabled by rememberVisualFlag("floating_blur", true)
    val glassEnabled by rememberVisualFlag("floating_glass", true)
    val backdrop = rememberLayerBackdrop()
    val homeSceneHost = remember { HomeSceneHostState() }
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sceneActive = panorama && onHome
    val activity = LocalActivity.current
    // Derivation progress belongs to the appearance sheet, not the whole navigation shell.
    val initialBackdropIsLight = remember { WallpaperColorTheme.state.value.backdropIsLight }
    val backdropIsLight by remember {
        WallpaperColorTheme.state.map { it.backdropIsLight }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = initialBackdropIsLight)
    val darkIcons = if (sceneActive && sceneExpanded && navigationEnabled) backdropIsLight else !darkTheme
    DisposableEffect(activity, darkIcons, darkTheme) {
        val controller = activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.isAppearanceLightStatusBars = darkIcons
        controller?.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
            val windowWidth = maxWidth
            val sceneRailWidth = (maxWidth * 0.25f - 28.dp).coerceIn(56.dp, 80.dp)
            val sceneProgress by animateFloatAsState(
                if (panorama && sceneExpanded && navigationEnabled) 1f else 0f,
                tween(360, easing = FastOutSlowInEasing), label = "scene_sidebar",
            )
            val pageTransition = updateTransition(currentDestination, label = "primary_scene")
            val sceneVisibility = pageTransition.animateFloat(
                transitionSpec = {
                    tween(
                        primaryPageDuration(destinations.indexOf(initialState), destinations.indexOf(targetState)),
                        easing = EaseInOut,
                    )
                },
                label = "scene_navigation_visibility",
            ) { destination -> if (panorama && destination == PrimaryDestination.Home) 1f else 0f }
            val sceneStillVisible by remember(sceneVisibility) {
                derivedStateOf { sceneVisibility.value > 0f }
            }
            val floatingStillVisible by remember(sceneVisibility) {
                derivedStateOf { sceneVisibility.value < 1f }
            }
            val renderFloatingNavigation = navigationEnabled && primaryPage && floatingShell &&
                (floatingNavigation || (panorama && floatingStillVisible))
            // Keep the outgoing scene composed until its fade ends without recomposing this
            // BoxWithConstraints for every alpha sample. The layers below read the full value.
            if (panorama && sceneProgress > 0.01f && (sceneActive || sceneStillVisible)) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = sceneVisibility.value }) {
                    HomeSceneBackdrop(state = wallpaperState, railWidth = sceneRailWidth, windowWidth = windowWidth)
                }
                HomeSceneRail(
                    destinations = destinations, currentDestination = currentDestination,
                    onSelectDestination = select, onAppearance = { homeSceneHost.openAppearance?.invoke() },
                    modifier = Modifier.width(sceneRailWidth).fillMaxHeight().zIndex(1f).graphicsLayer {
                        alpha = sceneProgress * sceneVisibility.value
                        translationX = -size.width * (1f - sceneProgress * sceneVisibility.value)
                    },
                )
            }
            CompositionLocalProvider(
                LocalHomeWallpaperViewModel provides wallpaperViewModel,
                LocalAsterPanorama provides panorama,
                LocalSceneProgress provides sceneProgress,
                LocalSceneRailWidth provides sceneRailWidth,
                LocalSceneActive provides (sceneActive && sceneProgress > 0.01f),
                LocalHomeSceneHostState provides homeSceneHost,
                LocalFloatingNavigationInset provides if (floatingNavigation) 76.dp else 0.dp,
                LocalNavigationBottomInset provides when {
                    floatingNavigation -> 76.dp
                    bottomBarVisible -> (bottomBarHeight - systemBottom).coerceAtLeast(0.dp)
                    else -> 0.dp
                },
            ) {
                // Primary pages slide at a fixed width. Only Home reserves its rail;
                // resizing the NavHost here used to squeeze both pages mid-transition.
                content(Modifier.fillMaxSize().then(
                    if (renderFloatingNavigation) Modifier.layerBackdrop(backdrop) else Modifier
                ))
            }
            if (bottomBarVisible) {
                Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }) {
                    AsterBottomNavigation(destinations, currentDestination, select)
                }
            }
            if (renderFloatingNavigation) {
                // As in Aster, a changed destination list invalidates saved drag coordinates.
                key(destinations) {
                AsterFloatingNavigation(
                    destinations = destinations, currentDestination = currentDestination,
                    backdrop = backdrop, blurEnabled = blurEnabled && Build.VERSION.SDK_INT >= 31,
                    glassEnabled = blurEnabled && glassEnabled && Build.VERSION.SDK_INT >= 33,
                    onSelectDestination = select,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .graphicsLayer {
                            val homeVisibility = if (panorama) sceneVisibility.value else 0f
                            alpha = 1f - homeVisibility
                            translationY = size.height * homeVisibility
                        }
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
                }
            }
            // Other KSUN pages own their host; never attach two timers to the same state.
            if (currentDestination == PrimaryDestination.Home || currentDestination == PrimaryDestination.SuperUser) {
              androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(
                    bottom = systemBottom + when {
                        floatingNavigation -> 76.dp
                        bottomBarVisible -> (bottomBarHeight - systemBottom).coerceAtLeast(0.dp)
                        else -> 0.dp
                    },
                ),
              )
            }
        }
    }
}

@Composable
private fun AsterBottomNavigation(
    destinations: List<PrimaryDestination>,
    currentDestination: PrimaryDestination?,
    onSelectDestination: (PrimaryDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination == destination
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectDestination(destination) },
                icon = destination.icon,
                label = stringResource(destination.label),
            )
        }
    }
}

@Composable
private fun AsterFloatingNavigation(
    destinations: List<PrimaryDestination>,
    currentDestination: PrimaryDestination?,
    backdrop: Backdrop,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    onSelectDestination: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = destinations.indexOf(currentDestination).coerceAtLeast(0)
    FloatingBottomBar(
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth(),
        selectedIndex = { selected },
        onSelected = { index -> onSelectDestination(destinations[index]) },
        backdrop = backdrop, tabsCount = destinations.size,
        isBackdropBlurEnabled = blurEnabled, isLiquidGlassEnabled = glassEnabled,
    ) {
        destinations.forEachIndexed { index, destination ->
            FloatingBottomBarItem(onClick = { onSelectDestination(destination) }) {
                Icon(destination.icon, stringResource(destination.label), modifier = Modifier.size(24.dp))
                Text(stringResource(destination.label), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
