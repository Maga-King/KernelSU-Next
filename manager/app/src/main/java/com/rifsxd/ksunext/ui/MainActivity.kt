package com.rifsxd.ksunext.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.screen.BottomBarDestination
import com.rifsxd.ksunext.ui.screen.FlashIt
import com.rifsxd.ksunext.ui.theme.KernelSUTheme
import com.rifsxd.ksunext.ui.shell.AsterAppShell
import com.rifsxd.ksunext.ui.shell.AsterNavigationTransitions
import com.rifsxd.ksunext.ui.shell.LocalAsterPanorama
import com.rifsxd.ksunext.ui.shell.LocalNavigationBottomInset
import com.rifsxd.ksunext.ui.util.*
import com.rifsxd.ksunext.ui.viewmodel.ModuleViewModel
import com.rifsxd.ksunext.ui.viewmodel.SuperUserViewModel

data class ScrollState(
    val isScrollingDown: MutableState<Boolean>,
    val scrollOffset: MutableState<Float>,
    val previousScrollOffset: MutableState<Float>,
)

val LocalScrollState = compositionLocalOf<ScrollState?> { null }
val LocalNavBarEnabled = compositionLocalOf<State<Boolean>?> { null }

@Composable
fun rememberScrollConnection(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                
                // Update scroll offset
                val newOffset = scrollOffset.value + delta
                scrollOffset.value = newOffset
                
                // Calculate the scroll delta from previous offset
                val scrollDelta = previousScrollOffset.value - newOffset
                
                // Only update direction if scroll delta exceeds threshold
                if (abs(scrollDelta) > threshold) {
                    isScrollingDown.value = scrollDelta > 0
                    previousScrollOffset.value = newOffset
                }
                
                return Offset.Zero
            }
            
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Reset offset tracking after fling
                previousScrollOffset.value = scrollOffset.value
                return super.onPostFling(consumed, available)
            }
        }
    }
}

fun Modifier.horizontalSwipeNavigator(
    currentRoute: String?,
    destinations: List<BottomBarDestination>,
    isRtl: Boolean,
    onNavigate: (Int) -> Unit
): Modifier = pointerInput(currentRoute, isRtl) {

    var totalDrag = 0f

    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        },
        onDragEnd = {
            val threshold = 150f

            if (kotlin.math.abs(totalDrag) > threshold) {
                val currentIndex = destinations.indexOfFirst {
                    it.direction.route == currentRoute
                }

                if (currentIndex == -1) return@detectHorizontalDragGestures

                if (if (isRtl) totalDrag > 0 else totalDrag < 0) {  
                    val next = (currentIndex + 1)
                        .coerceAtMost(destinations.lastIndex)
                    if (next != currentIndex) onNavigate(next)
                } else {
                    val prev = (currentIndex - 1)
                        .coerceAtLeast(0)
                    if (prev != currentIndex) onNavigate(prev)
                }
            }
        }
    )
}

fun Modifier.trackScroll(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): Modifier {
    val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            
            // Update scroll offset
            val newOffset = scrollOffset.value + delta
            scrollOffset.value = newOffset
            
            // Calculate the scroll delta from previous offset
            val scrollDelta = previousScrollOffset.value - newOffset
            
            // Only update direction if scroll delta exceeds threshold
            if (abs(scrollDelta) > threshold) {
                isScrollingDown.value = scrollDelta > 0
                previousScrollOffset.value = newOffset
            }
            
            return Offset.Zero
        }
        
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Reset offset tracking after fling
            previousScrollOffset.value = scrollOffset.value
            return super.onPostFling(consumed, available)
        }
    }
    
    return this.nestedScroll(scrollConnection)
}

class MainActivity : FragmentActivity() {

    private val appLockState = mutableStateOf(false)

    private var pendingIntent: Intent? = null
    var zipUri by mutableStateOf<ArrayList<Uri>?>(null)
    enum class NavigateLocation { SUPERUSER, MODULES, SETTINGS }
    var navigateLoc by mutableStateOf<NavigateLocation?>(null)
    var moduleActionId by mutableStateOf<String?>(null)
    var amoledModeState = mutableStateOf(false)
    var navBarEnabled = mutableStateOf(true)
    var panoramaEnabled = mutableStateOf(true)
    private val handler = Handler(Looper.getMainLooper())

    val moduleViewModel: ModuleViewModel by viewModels()
    val superUserViewModel: SuperUserViewModel by viewModels()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLanguage(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (superUserViewModel.appList.isEmpty()) {
                superUserViewModel.fetchAppList()
            }
            if (moduleViewModel.moduleList.isEmpty()) {
                moduleViewModel.fetchModuleList()
            }
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        try {
            val prefsInit = getSharedPreferences("settings", MODE_PRIVATE)
            amoledModeState.value = prefsInit.getBoolean("enable_amoled", false)
            navBarEnabled.value = prefsInit.getBoolean("enable_navbar", true)
            panoramaEnabled.value = prefsInit.getBoolean("aster_panorama", true)
        } catch (_: Exception) {}

        val isManager = Natives.isManager
        if (isManager) install()

        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            intent.extras?.clear()
            intent = null
        }

        val prefsInit = getSharedPreferences("settings", MODE_PRIVATE)
        val requireBiometric = prefsInit.getBoolean("enable_biometric_lock", false)

        if (savedInstanceState != null) {
            appLockState.value = savedInstanceState.getBoolean("appLockState", requireBiometric)
        } else {
            appLockState.value = requireBiometric
        }

        if(intent != null)
            handleIntent(intent)

        setContent { Box(modifier = Modifier.fillMaxSize()) {
            KernelSUTheme(amoledMode = amoledModeState.value) {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }

                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                val isManager = Natives.isManager
                val fullFeatured = Natives.isFullFeatured()

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val homeDestination = BottomBarDestination.entries.firstOrNull()
                val startRoute = homeDestination?.direction?.route

                if (homeDestination != null && startRoute != null) {
                    BackHandler(enabled = currentRoute != startRoute && currentRoute in bottomBarRoutes) {
                        navigator.navigate(homeDestination.direction) {
                            popUpTo(NavGraphs.root.startRoute) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                }

                // Scroll state for bottom bar visibility
                val isScrollingDown = remember { mutableStateOf(false) }
                val scrollOffset = remember { mutableStateOf(0f) }
                val previousScrollOffset = remember { mutableStateOf(0f) }

                LaunchedEffect(zipUri, navigateLoc, moduleActionId, appLockState.value) {
                    // Removing the decorative splash must not release pending actions
                    // before the existing biometric lock has been satisfied.
                    if (appLockState.value) return@LaunchedEffect
                    if (moduleActionId != null) {
                        navigator.navigate(ExecuteModuleActionScreenDestination(moduleActionId!!))
                        moduleActionId = null
                    }

                    if (!zipUri.isNullOrEmpty()) {
                        val uris = zipUri!!
                        val component = intent?.component?.className
                        val flashIt = when {
                            // explicit legacy entry point
                            component?.endsWith("FlashAnyKernel") == true -> FlashIt.FlashAnyKernel(uris.first())
                            // auto-detect: single AnyKernel3 zip flashes as kernel, anything else as module(s)
                            uris.size == 1 && ZipUtils.isAnyKernel3Zip(applicationContext, uris.first()) ->
                                FlashIt.FlashAnyKernel(uris.first())
                            else -> FlashIt.FlashModules(uris)
                        }

                        navigator.navigate(
                            FlashScreenDestination(flashIt = flashIt)
                        )
                        zipUri = null
                    }

                    if (zipUri.isNullOrEmpty() && navigateLoc != null) {
                        when (navigateLoc) {
                            NavigateLocation.SUPERUSER -> navigator.navigate(SuperUserScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            NavigateLocation.MODULES -> navigator.navigate(ModuleScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            NavigateLocation.SETTINGS -> navigator.navigate(SettingScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            else -> { /* no-op for exhaustiveness */ }
                        }
                        navigateLoc = null
                    }
                }

                val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                val animationDirection = if (isRtl) -1 else 1
                val visiblePrimaryRoutes = remember(fullFeatured) {
                    BottomBarDestination.entries.filter { fullFeatured || !it.rootRequired }
                        .map { it.direction.route }
                }
                val navigationTransitions = remember(bottomBarRoutes, visiblePrimaryRoutes, animationDirection) {
                    AsterNavigationTransitions(bottomBarRoutes, visiblePrimaryRoutes, animationDirection)
                }
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackBarHostState,
                    LocalScrollState provides ScrollState(isScrollingDown, scrollOffset, previousScrollOffset),
                    LocalNavBarEnabled provides navBarEnabled,
                ) {
                    AsterAppShell(
                        navController = navController,
                        fullFeatured = fullFeatured,
                        navigationEnabled = navBarEnabled.value,
                        snackbarHostState = snackBarHostState,
                    ) { shellModifier ->
                            DestinationsNavHost(
                                modifier = shellModifier,
                                navGraph = NavGraphs.root,
                                navController = navController,
                                defaultTransitions = navigationTransitions,
                            )
                    }
                }
            }

            AppLockOverlay(amoledModeState.value)

        } } // Box & setContent
    } // onCreate

    @Composable
    private fun AppLockOverlay(amoledMode: Boolean) {
        if (appLockState.value) {
            KernelSUTheme(amoledMode = amoledMode) {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    var isPromptShowing by remember { mutableStateOf(false) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                                val timeout = prefs.getLong("app_lock_timeout", 60000L)
                                if (!com.rifsxd.ksunext.ui.util.AppLockManager.shouldPrompt(timeout)) {
                                    appLockState.value = false
                                    pendingIntent?.let {
                                        handleIntent(it)
                                        pendingIntent = null
                                    }
                                    return@LifecycleEventObserver
                                }

                                if (!isPromptShowing) {
                                    isPromptShowing = true
                                    com.rifsxd.ksunext.ui.util.BiometricAuthenticator(this@MainActivity)
                                        .authenticate(
                                            title = getString(com.rifsxd.ksunext.R.string.biometric_prompt_subtitle),
                                            subtitle = null,
                                            onSuccess = {
                                                isPromptShowing = false
                                                appLockState.value = false
                                                com.rifsxd.ksunext.ui.util.AppLockManager.unlock()
                                                pendingIntent?.let {
                                                    handleIntent(it)
                                                    pendingIntent = null
                                                }
                                            },
                                            onError = {
                                                isPromptShowing = false
                                                Toast.makeText(this@MainActivity, getString(R.string.auth_failed, it), Toast.LENGTH_SHORT).show()
                                                finish()
                                            }
                                        )
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                }
            }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("enable_amoled", enabled).apply()
        } catch (_: Exception) {}
        amoledModeState.value = enabled
    }

    fun setNavBarEnabled(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("enable_navbar", enabled).apply()
        } catch (_: Exception) {}
        navBarEnabled.value = enabled
    }

    fun setPanoramaEnabled(enabled: Boolean) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putBoolean("aster_panorama", enabled).apply()
        panoramaEnabled.value = enabled
    }

    override fun onStart() {
        super.onStart()
        com.rifsxd.ksunext.ui.util.AppLockManager.onActivityStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        com.rifsxd.ksunext.ui.util.AppLockManager.onActivityStop(this)
        val prefsInit = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefsInit.getBoolean("enable_biometric_lock", false)) {
            appLockState.value = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("appLockState", appLockState.value)

    }

    private fun handleIntent(intent: Intent) {
        if (appLockState.value) {
            pendingIntent = intent
            return
        }
        val shortcutType = intent.getStringExtra("shortcut_type")
        if (shortcutType == "module_action") {
            moduleActionId = intent.getStringExtra("module_id")
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                zipUri =
                    intent.data?.let { arrayListOf(it) }
                        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra("uris", Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra("uris")
                        }
            }

            "ACTION_SETTINGS" -> navigateLoc = NavigateLocation.SETTINGS
            "ACTION_SUPERUSER" -> navigateLoc = NavigateLocation.SUPERUSER
            "ACTION_MODULES" -> navigateLoc = NavigateLocation.MODULES
            else -> { /* ignore other actions */ }
        }
    }
}
