package com.rifsxd.ksunext.ui.screen

// Visual adaptation of Aster (https://github.com/LyraVoid/Aster), GPL-3.0.
// Navigation targets, device state and manager actions remain KernelSU Next's.
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import com.rifsxd.ksunext.ui.shell.LocalAsterPanorama
import com.rifsxd.ksunext.ui.shell.LocalHomeSceneHostState
import com.rifsxd.ksunext.ui.home.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.rifsxd.ksunext.*
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.component.rememberConfirmDialog
import com.rifsxd.ksunext.ui.util.*
import com.rifsxd.ksunext.ui.webui.WebUIActivity
import com.rifsxd.ksunext.ui.util.restartActivity
import com.rifsxd.ksunext.ui.util.module.LatestVersionInfo
import com.rifsxd.ksunext.ui.viewmodel.ModuleViewModel
import com.rifsxd.ksunext.ui.LocalNavBarEnabled
import com.rifsxd.ksunext.ui.LocalScrollState 
import com.rifsxd.ksunext.ui.screen.BottomBarDestination
import com.rifsxd.ksunext.ui.trackScroll 
import com.rifsxd.ksunext.ui.rememberScrollConnection
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    var kernelVersion by remember { mutableStateOf(getKernelVersion()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val refreshModuleViewModel: ModuleViewModel = viewModel()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val isManager = remember(refreshToken) { Natives.isManager }
    val fullFeatured = remember(refreshToken) { Natives.isFullFeatured() }
    val ksuVersion = if (isManager) Natives.version else null
    val ksuVersionTag = if (isManager) Natives.getVersionTag() else null
    val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
    val managerUAPIVersion = Natives.managerUAPIVersion

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val developerOptionsEnabled = prefs.getBoolean("enable_developer_options", false)
    val refreshFailedText = stringResource(R.string.home_refresh_failed)
    val refreshHome: () -> Unit = {
        if (!refreshing) {
            // Gate immediately, before dispatching work, so repeated pulls cannot overlap.
            refreshing = true
            refreshScope.launch {
                try {
                    val refreshedKernel = withContext(Dispatchers.IO) { getKernelVersion() }
                    if (Natives.isFullFeatured()) {
                        refreshModuleViewModel.fetchModuleList().join()
                    }
                    currentCoroutineContext().ensureActive()
                    kernelVersion = refreshedKernel
                    // Re-read the existing native/root status and restart only data effects.
                    // Card state, scroll, wallpaper and the navigation shell keep their identity.
                    refreshToken++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("Home", "Unable to refresh Home data", error)
                    Toast.makeText(context, refreshFailedText, Toast.LENGTH_SHORT).show()
                } finally {
                    refreshing = false
                }
            }
        }
    }
    
    // Get scroll state for bottom bar tracking
    val bottomBarScrollState = LocalScrollState.current

    val navBarEnabled = LocalNavBarEnabled.current

    val navBarPadding = com.rifsxd.ksunext.ui.shell.navigationBottomPadding()
    
    // Create scroll connection for bottom bar
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null

    val lkmMode = ksuVersion?.let { Natives.isLkmMode }
    val panorama = LocalAsterPanorama.current
    val wallpaperViewModel: HomeWallpaperViewModel = LocalHomeWallpaperViewModel.current ?: viewModel()
    val wallpaperState by wallpaperViewModel.uiState.collectAsStateWithLifecycle()
    var showWallpaperSheet by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var showSceneReboot by rememberSaveable { mutableStateOf(false) }
    var pendingDangerousReboot by rememberSaveable { mutableStateOf<String?>(null) }
    val dangerousRebootDialog = rememberConfirmDialog(
        onConfirm = {
            pendingDangerousReboot?.let { reboot(it) }
            pendingDangerousReboot = null
        },
        onDismiss = { pendingDangerousReboot = null },
    )
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
    @Suppress("DEPRECATION")
    val supportsUserspaceReboot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        powerManager?.isRebootingUserspaceSupported == true
    var wallpaperPickerSlot by remember { mutableStateOf(HomeWallpaperSlot.LIGHT) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) wallpaperViewModel.importImage(uri, wallpaperPickerSlot)
    }
    val sceneHost = LocalHomeSceneHostState.current
    DisposableEffect(sceneHost) {
        sceneHost?.openAppearance = { showWallpaperSheet = true }
        onDispose { sceneHost?.openAppearance = null }
    }
    LaunchedEffect(panorama, wallpaperState.phase) {
        if (panorama && wallpaperState.hasImage && wallpaperState.phase == HomeWallpaperPhase.DISABLED) {
            wallpaperViewModel.setEnabled(true)
        }
    }
    LaunchedEffect(wallpaperViewModel) {
        wallpaperViewModel.events.collect {
            Toast.makeText(context, R.string.aster_home_wallpaper_error, Toast.LENGTH_LONG).show()
        }
    }
    val installed = ksuVersion != null
    val hasRoot = installed && rootAvailable()
    val sceneState = AsterHomeState(
        title = stringResource(when {
            installed && !hasRoot -> R.string.grant_root_failed
            installed -> R.string.home_working
            kernelVersion.isGKI() -> R.string.home_not_installed
            else -> R.string.home_failure
        }),
        summary = if (installed && hasRoot) "" else stringResource(
            if (installed) R.string.grant_root_failed
            else if (kernelVersion.isGKI()) R.string.home_click_to_install else R.string.home_failure_tip
        ),
        installed = installed,
        needsAccess = installed && !hasRoot,
        firstLabel = "KSU NEXT",
        firstValue = stringResource(when {
            installed && hasRoot -> R.string.home_activated
            installed -> R.string.grant_root_failed
            else -> R.string.home_not_installed
        }),
        secondLabel = stringResource(R.string.aster_scene_mode),
        secondValue = when {
            !installed -> stringResource(R.string.unavailable)
            Natives.isLateLoadMode -> stringResource(R.string.jailbreak_mode)
            Natives.isSafeMode -> stringResource(R.string.safe_mode)
            lkmMode == true -> "LKM"
            else -> "Built-in"
        },
        deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.titlecase() }} ${Build.MODEL}",
        checking = refreshing,
    )
    val homeContent: @Composable () -> Unit = {
            if (installed && isSuCompatDisabled()) {
                WarningCard(stringResource(R.string.sucompat_disabled), MaterialTheme.colorScheme.tertiary)
            }
            if (installed && lkmMode == true && !Natives.isLkmBundled) {
                AsterStatusLabel(
                    stringResource(R.string.home_lkm_custom), Icons.Filled.Extension,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            val currentVersionCode = remember(context) { getManagerVersion(context).second }
            val requiresNewKernel = isManager && kernelUAPIVersion != null && managerUAPIVersion > kernelUAPIVersion
            val requiresNewManager = isManager && kernelUAPIVersion != null && managerUAPIVersion < kernelUAPIVersion

            // Jailbreak mode runs on locked bootloaders, so flashing a boot image would brick the device.
            val canInstallKernelUpdate = lkmMode == true && !Natives.isLateLoadMode

            if (requiresNewKernel) {
                WarningCard(
                    stringResource(
                        id = if (canInstallKernelUpdate) R.string.require_kernel_version else R.string.require_kernel_version_gki,
                        kernelUAPIVersion!!,
                        managerUAPIVersion
                    ),
                    onClick = if (canInstallKernelUpdate) {
                        { navigator.navigate(InstallScreenDestination) }
                    } else null
                )
            }

            if (requiresNewManager) {
                WarningCard(
                    stringResource(
                        id = R.string.require_manager_version,
                        managerUAPIVersion,
                        kernelUAPIVersion!!
                    )
                )
            }

            val showLkmUpdate = isManager && lkmMode == true && Natives.isLkmBundled && ksuVersion?.toLong() != currentVersionCode && !requiresNewKernel && !requiresNewManager

            if (showLkmUpdate) {
                WarningCard(
                    message = stringResource(R.string.home_lkm_update_available),
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = { navigator.navigate(InstallScreenDestination) }
                )
            }

            if (ksuVersion != null && !rootAvailable()) {
                WarningCard(
                    stringResource(id = R.string.grant_root_failed),
                    onClick = {
                        restartActivity(context)
                    }
                )
            }

            val checkUpdate =
                LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean("check_update", true)
            if (checkUpdate) {
                UpdateCard(refreshToken = refreshToken)
            }

            if (fullFeatured) {
                AsterResponsivePair(
                    first = {
                        SuperuserCard(refreshToken = refreshToken, onClick = {
                            navigator.navigate(SuperUserScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        })
                    },
                    second = {
                        ModuleCard(refreshToken = refreshToken, onClick = {
                            navigator.navigate(ModuleScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        })
                    }
                )
            }

            InfoCard(autoExpand = developerOptionsEnabled, refreshToken = refreshToken)
            IssueReportCard()
            ContributorsCard()
            Spacer(Modifier)
    }
    AsterHomeWallpaperSheet(
        show = showWallpaperSheet,
        state = wallpaperState,
        onDismissRequest = { showWallpaperSheet = false },
        onNightEnabledChange = wallpaperViewModel::setNightEnabled,
        onChooseImage = { slot ->
            wallpaperPickerSlot = slot
            wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemoveImage = wallpaperViewModel::removeImage,
        onCropChange = { slot, crop -> wallpaperViewModel.saveCrop(crop, slot) },
        onRegenerateColor = wallpaperViewModel::regenerateColors,
    )
    if (panorama) {
        val feedbackUrl = stringResource(R.string.issue_report_github_link)
        val downloadTitle = stringResource(R.string.reboot_download)
        val edlTitle = stringResource(R.string.reboot_edl)
        val downloadConfirmation = stringResource(R.string.reboot_download_confirm)
        val edlConfirmation = stringResource(R.string.reboot_edl_confirm)
        val rebootText = stringResource(R.string.reboot)
        AsterHomeScene(
            state = sceneState,
            wallpaperState = wallpaperState,
            onTools = { showTools = true },
            onAppearance = { showWallpaperSheet = true },
            onRefresh = refreshHome,
        ) {
            if (!installed) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { navigator.navigate(InstallScreenDestination) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                ) { top.yukonga.miuix.kmp.basic.Text(stringResource(R.string.install)) }
            }
            homeContent()
        }
        AsterHomeActionsSheet(
            showMore = showTools,
            showReboot = showSceneReboot,
            onShowMoreChange = { showTools = it },
            onShowRebootChange = { showSceneReboot = it },
            canReboot = installed,
            supportsUserspaceReboot = supportsUserspaceReboot,
            canUninstall = false,
            onUninstallClick = {},
            onInstallClick = { showTools = false; navigator.navigate(InstallScreenDestination) },
            onFeedback = { context.startActivity(Intent(Intent.ACTION_VIEW, feedbackUrl.toUri())) },
            onAbout = { showTools = false; navigator.navigate(SettingScreenDestination) },
            onReboot = { reboot(if (it == "soft_reboot") "soft-reboot" else it) },
            onDangerousReboot = { reason ->
                pendingDangerousReboot = reason
                val download = reason == "download"
                dangerousRebootDialog.showConfirm(
                    title = if (download) downloadTitle else edlTitle,
                    content = if (download) downloadConfirmation else edlConfirmation,
                    confirm = rebootText,
                )
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopBar(
                kernelVersion,
                ksuVersion,
                onInstallClick = {
                    navigator.navigate(InstallScreenDestination)
                },
                onSettingsClick = {
                    navigator.navigate(SettingScreenDestination) {
                        popUpTo(NavGraphs.root.startRoute) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                navBarEnabled = LocalNavBarEnabled.current?.value ?: true,
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                // Chain scroll connections - bottom bar tracking first, then topbar behavior
                .let { modifier ->
                    if (bottomBarScrollConnection != null) {
                        modifier
                            .nestedScroll(bottomBarScrollConnection)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
                .padding(bottom = navBarPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsterStandardStatusCard(
                title = sceneState.title,
                subtitle = sceneState.summary.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.home_working_version, ksuVersionTag ?: "", "${ksuVersion ?: 0}-${kernelUAPIVersion ?: 0}"),
                mode = sceneState.secondValue,
                isWorking = sceneState.installed && !sceneState.needsAccess,
                icon = Icons.Filled.Lock,
                iconColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary.copy(alpha = 0.22f),
                cardColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primaryContainer,
                onClick = { if (!installed) navigator.navigate(InstallScreenDestination) },
            )

            homeContent()
        }
    }
}

/** Cards stay side by side when each has enough readable space, including font scaling. */
@Composable
private fun AsterResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth / fontScale < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun SuperuserCard(refreshToken: Int = 0, onClick: (() -> Unit)? = null) {
    val count = getSuperuserCount()
    AsterCountCard(
        label = stringResource(if (count <= 1) R.string.home_superuser_count_singular else R.string.home_superuser_count_plural),
        count = count,
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleCard(refreshToken: Int = 0, onClick: (() -> Unit)? = null) {
    val moduleViewModel: ModuleViewModel = viewModel()
    val modules = moduleViewModel.moduleList
    val lifecycleOwner = LocalLifecycleOwner.current
    // Shell-backed reads must not run during composition. Refresh when Home resumes
    // and when the shared module list changes after an install/remove operation.
    val count by produceState(initialValue = 0, key1 = modules, key2 = lifecycleOwner, key3 = refreshToken) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = readHomeData(value) { getModuleCount() }
        }
    }

    val moduleUpdateCount by produceState(initialValue = 0, key1 = modules, key2 = refreshToken) {
        value = readHomeData(value) {
            modules.count { module ->
                currentCoroutineContext().ensureActive()
                module.enabled && moduleViewModel.checkUpdate(module).first.isNotEmpty()
            }
        }
    }

    // State machine: 0 = nothing, 1 = show "+ Update!", 2 = show "+ X"
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(moduleUpdateCount) {
        if (moduleUpdateCount > 0) {
            step = 1
            delay(1200) // show "+ Update!" for a moment
            step = 2
        } else {
            step = 0
        }
    }

    AsterCountCard(
        label = stringResource(if (count <= 1) R.string.home_module_count_singular else R.string.home_module_count_plural),
        count = count,
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (moduleUpdateCount > 0) {
            AnimatedContent(targetState = step, label = "module-updates") { target ->
                top.yukonga.miuix.kmp.basic.Text(
                    text = if (target == 1) stringResource(R.string.home_module_update_available) else "$moduleUpdateCount*",
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote1,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Failed background reads retain displayed data; leaving Home still cancels the work. */
private suspend fun <T> readHomeData(previous: T, read: suspend () -> T): T = try {
    withContext(Dispatchers.IO) { read() }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    android.util.Log.w("Home", "Unable to reload Home card", error)
    previous
}

@Composable
fun UpdateCard(refreshToken: Int = 0) {
    val context = LocalContext.current
    val latestVersionInfo = LatestVersionInfo()
    
    var preferSpoofed by remember { mutableStateOf(false) }
    
    val newVersion by produceState(initialValue = latestVersionInfo, key1 = preferSpoofed, key2 = refreshToken) {
        value = readHomeData(value) {
            checkNewVersion(preferSpoofed)
        }
    }

    val currentVersionCode = remember(context) { getManagerVersion(context).second }
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog
    val newVersionTag = newVersion.versionTag

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsterUpdateAvailableCard(
                title = stringResource(R.string.new_version_available, newVersionTag ?: "", newVersionCode),
                onClick = {
                    if (changelog.isEmpty()) uriHandler.openUri(newVersionUrl)
                    else updateDialog.showConfirm(title = title, content = changelog, markdown = true, confirm = updateText)
                },
            )
            top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        stringResource(R.string.select_build_type),
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                    )
                    top.yukonga.miuix.kmp.preference.RadioButtonPreference(
                        title = stringResource(R.string.main), selected = !preferSpoofed,
                        onClick = { preferSpoofed = false },
                    )
                    top.yukonga.miuix.kmp.preference.RadioButtonPreference(
                        title = stringResource(R.string.spoofed), selected = preferSpoofed,
                        onClick = { preferSpoofed = true },
                    )
                }
            }
        }
    }
}

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(text = {
        Text(stringResource(id))
    }, onClick = {
        reboot(reason)
    })
}

// @Composable
// fun getSeasonalIcon(): ImageVector {
//     val month = Calendar.getInstance().get(Calendar.MONTH) // 0-11 for January-December
//     return when (month) {
//         Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Icons.Filled.AcUnit // Winter
//         Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Icons.Filled.Spa // Spring
//         Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Icons.Filled.WbSunny // Summer
//         Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> Icons.Filled.Forest // Fall
//         else -> Icons.Filled.Whatshot // Fallback icon
//     }
// }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    kernelVersion: KernelVersion,
    ksuVersion: Int?,
    onInstallClick: () -> Unit,
    onSettingsClick: () -> Unit,
    navBarEnabled: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    var isSpinning by remember { mutableStateOf(false) }
    var rotationTarget by remember { mutableStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = tween(
            durationMillis = 1400,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        finishedListener = {
            isSpinning = false
        }
    )

    val moduleViewModel: ModuleViewModel = viewModel()
    
    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val context = LocalContext.current
    MediumTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!isSpinning) {
                        isSpinning = true
                        rotationTarget += 360f * 6
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ksu_next),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                        }
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            if (!navBarEnabled) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(id = R.string.settings)
                    )
                }
            }

            if (ksuVersion != null) {
                IconButton(onClick = onInstallClick) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = stringResource(id = R.string.install)
                    )
                }
            }

            if (ksuVersion != null) {
                var showDropdown by remember { mutableStateOf(false) }
                IconButton(onClick = {
                    showDropdown = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = stringResource(id = R.string.reboot)
                    )

                    DropdownMenu(expanded = showDropdown, onDismissRequest = {
                        showDropdown = false
                    }) {
                        RebootDropdownItem(id = R.string.reboot)
                        RebootDropdownItem(id = R.string.reboot_userspace, reason = "soft-reboot")

                        val pm =
                            LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                            RebootDropdownItem(id = R.string.reboot_userspace, reason = "userspace")
                        }
                        RebootDropdownItem(id = R.string.reboot_recovery, reason = "recovery")
                        RebootDropdownItem(id = R.string.reboot_bootloader, reason = "bootloader")
                        RebootDropdownItem(id = R.string.reboot_download, reason = "download")
                        RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
                    }
                }
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(
    kernelVersionParam: KernelVersion,
    ksuVersionParam: Int?,
    uapiVerParam: Int? = null,
    lkmModeParam: Boolean?,
    moduleUpdateCount: Int = 0,
    ksuVersionTagParam: String? = null,
    onClickInstall: () -> Unit = {}
) {
    val installed = ksuVersionParam != null
    val background = when {
        installed -> MaterialTheme.colorScheme.primaryContainer
        kernelVersionParam.isGKI() -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!installed) Modifier.clickable(onClick = onClickInstall) else Modifier)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        installed -> Icons.Filled.CheckCircle
                        kernelVersionParam.isGKI() -> Icons.Filled.AutoFixHigh
                        else -> Icons.Filled.MoodBad
                    },
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(when {
                        installed -> R.string.home_working
                        kernelVersionParam.isGKI() -> R.string.home_not_installed
                        else -> R.string.home_failure
                    }),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            if (installed) {
                val workingMode = if (lkmModeParam != null) {
                    val mode = if (lkmModeParam) "LKM" else "BUILT-IN"
                    "$mode (${kernelVersionParam.getKernelType()})"
                } else kernelVersionParam.getKernelType()
                // A FlowRow keeps mode and warning labels readable on a narrow sidebar layout.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AsterStatusLabel(
                        workingMode,
                        if (Natives.isSafeMode) Icons.Filled.Security else Icons.Filled.VerifiedUser,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (isSuCompatDisabled()) {
                        AsterStatusLabel(
                            stringResource(R.string.sucompat_disabled), Icons.Filled.Warning,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (Natives.isLateLoadMode) {
                        AsterStatusLabel(
                            stringResource(R.string.jailbreak_mode), Icons.Filled.Warning,
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (lkmModeParam == true && !Natives.isLkmBundled) {
                        AsterStatusLabel(
                            stringResource(R.string.home_lkm_custom), Icons.Filled.Extension,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.home_working_version,
                        ksuVersionTagParam?.takeIf { it.isNotEmpty() } ?: "v0.0.0",
                        "$ksuVersionParam-${uapiVerParam ?: 0}"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(if (kernelVersionParam.isGKI())
                        R.string.home_click_to_install else R.string.home_failure_tip),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AsterStatusLabel(text: String, icon: ImageVector, background: Color, foreground: Color) {
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun WarningCard(
    message: String, color: Color = MaterialTheme.colorScheme.error, onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = color
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SentimentDissatisfied,
                contentDescription = null,
                modifier = Modifier.padding(end = 20.dp)
            )
            Text(
                text = message, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private data class HomeInfoSnapshot(
    val managerVersion: Pair<String, Long> = "" to 0L,
    val managerUAPIVersion: Int = 0,
    val managerAppId: Int = 0,
    val hookMode: String? = null,
    val metaModule: String? = null,
    val metaInfo: ModuleViewModel.ModuleInfo? = null,
    val suSFS: String? = null,
    val suSFSVersion: String? = null,
    val suSFSVariant: String? = null,
    val zygiskEnabled: Boolean = false,
    val zygiskInfo: ModuleViewModel.ModuleInfo? = null,
    val unameRelease: String = "",
    val unameMachine: String = "",
    val seccompStatus: String = "Unavailable",
    val selinuxStatusRes: Int = R.string.selinux_status_unknown,
)

private fun buildHomeInfoSnapshot(
    context: Context,
    ksuVersion: Int?,
    moduleList: List<ModuleViewModel.ModuleInfo>,
): HomeInfoSnapshot {
    val managerVersion = getManagerVersion(context)
    val managerUAPIVersion = Natives.managerUAPIVersion
    val managerAppId = Natives.getManagerAppid()
    val hookMode = if (ksuVersion == null) null else {
        Natives.getHookMode().takeUnless { it.isNullOrBlank() } ?: "Unavailable"
    }
    val metaModule = if (ksuVersion == null) null else getMetaModule()
    val metaInfo = if (metaModule == null) null else moduleList.firstOrNull { it.isMetaModule }
    val suSFS = if (ksuVersion == null) null else getSuSFS()
    val suSFSVersion = if (suSFS == "Supported" && ksuVersion != null) getSuSFSVersion() else null
    val suSFSVariant = if (suSFS == "Supported" && ksuVersion != null) getSuSFSVariant() else null
    val zygiskEnabled = if (ksuVersion == null) false else Natives.isZygiskEnabled()
    val zygiskInfo = if (!zygiskEnabled) null else moduleList.firstOrNull { it.isZygisk && it.enabled }
    val uname = kotlin.runCatching { Os.uname() }.getOrNull()
    val statusInt = kotlin.runCatching { Os.prctl(21, 0, 0, 0, 0) }.getOrDefault(-1)
    val seccompStatus = when (statusInt) {
        -1 -> "Unavailable"
        0 -> "Disabled"
        1 -> "Strict"
        2 -> "Filter"
        else -> "Unknown"
    }

    return HomeInfoSnapshot(
        managerVersion = managerVersion,
        managerUAPIVersion = managerUAPIVersion,
        managerAppId = managerAppId,
        hookMode = hookMode,
        metaModule = metaModule,
        metaInfo = metaInfo,
        suSFS = suSFS,
        suSFSVersion = suSFSVersion,
        suSFSVariant = suSFSVariant,
        zygiskEnabled = zygiskEnabled,
        zygiskInfo = zygiskInfo,
        unameRelease = uname?.release.orEmpty(),
        unameMachine = uname?.machine.orEmpty(),
        seccompStatus = seccompStatus,
        selinuxStatusRes = getSELinuxStatus(),
    )
}

@Composable
private fun InfoCard(autoExpand: Boolean = false, refreshToken: Int = 0) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val isManager = remember(refreshToken) { Natives.isManager }
    val ksuVersion = remember(isManager, refreshToken) { if (isManager) Natives.version else null }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val developerOptionsEnabled = remember(prefs) { prefs.getBoolean("enable_developer_options", false) }

    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            expanded = true
        }
    }

    val moduleViewModel: ModuleViewModel = viewModel()
    var homeInfo by remember { mutableStateOf(HomeInfoSnapshot()) }

    LaunchedEffect(ksuVersion, moduleViewModel.moduleList, refreshToken) {
        homeInfo = readHomeData(homeInfo) {
            buildHomeInfoSnapshot(context, ksuVersion, moduleViewModel.moduleList)
        }
    }

    top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            @Composable
            fun InfoCardItem(label: String, content: String, icon: Any? = null) {
                AsterDeviceInfoItem(title = label, content = content)
            }

            Column {
                InfoCardItem(
                    label = stringResource(R.string.home_manager_version),
                    content = if (developerOptionsEnabled) {
                        "${homeInfo.managerVersion.first} (${homeInfo.managerVersion.second}-${homeInfo.managerUAPIVersion}) | UID: ${homeInfo.managerAppId}"
                    } else {
                        "${homeInfo.managerVersion.first} (${homeInfo.managerVersion.second}-${homeInfo.managerUAPIVersion})"
                    },
                    icon = Icons.Filled.AutoAwesomeMotion,
                )

                if (ksuVersion != null) {
                    Spacer(Modifier.height(16.dp))
                    InfoCardItem(
                        label = stringResource(R.string.hook_mode),
                        content = homeInfo.hookMode ?: stringResource(R.string.unavailable),
                        icon = Icons.Filled.Phishing,
                    )
                }

                if (ksuVersion != null) {
                    val metaInfo = homeInfo.metaInfo
                    val metaDetail = if (metaInfo != null) " | ${metaInfo.name} | ${metaInfo.version}" else ""
                    Spacer(Modifier.height(16.dp))
                    InfoCardItem(
                        label = stringResource(R.string.home_metamodule_status),
                        content = when {
                            homeInfo.metaModule == "Installed" && metaInfo != null && !metaInfo.enabled ->
                                stringResource(R.string.disabled) + metaDetail
                            homeInfo.metaModule == "Installed" ->
                                stringResource(R.string.installed) + metaDetail
                            else ->
                                stringResource(R.string.home_not_installed)
                        },
                        icon = Icons.Filled.SettingsSuggest
                    )

                    if (homeInfo.suSFS == "Supported") {
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_susfs_version),
                            content = "${stringResource(R.string.supported)} | ${homeInfo.suSFSVersion ?: stringResource(R.string.unavailable)} (${homeInfo.suSFSVariant ?: stringResource(R.string.unavailable)})",
                            icon = painterResource(R.drawable.ic_sus),
                        )
                    }

                    if (homeInfo.zygiskEnabled) {
                        Spacer(Modifier.height(16.dp))
                        val zygiskInfo = homeInfo.zygiskInfo
                        val zygiskDetail = if (zygiskInfo != null) " | ${zygiskInfo.name} | ${zygiskInfo.version}" else ""
                        InfoCardItem(
                            label = stringResource(R.string.zygisk_status),
                            content = stringResource(R.string.enabled) + zygiskDetail,
                            icon = Icons.Filled.Vaccines
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_kernel),
                            content = "${homeInfo.unameRelease} (${homeInfo.unameMachine})",
                            icon = painterResource(R.drawable.ic_linux),
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_android),
                            content = "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
                            icon = Icons.Filled.Android,
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_abi),
                            content = Build.SUPPORTED_ABIS.joinToString(", "),
                            icon = Icons.Filled.Memory,
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_selinux_status),
                            content = stringResource(homeInfo.selinuxStatusRes),
                            icon = Icons.Filled.Security,
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_seccomp_status),
                            content = homeInfo.seccompStatus,
                            icon = Icons.Filled.LocalPolice
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 300)
                    )

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) null else null,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = rotationAngle
                            }
                        )
                    }
                }
            }
        }
    }
}

data class Contributor(
    val login: String,
    val name: String? = null,
    val githubUrl: String,
    val role: String,
    val donationUrl: String
)

@Composable
fun ContributorsCard() {
    val uriHandler = LocalUriHandler.current

    val contributors = listOf(
        Contributor(
            login = "rifsxd",
            name = "Rifat Azad",
            githubUrl = "https://github.com/rifsxd",
            role = stringResource(R.string.contributor_lead_developer),
            donationUrl = "https://github.com/KernelSU-Next/KernelSU-Next/tree/dev?tab=readme-ov-file#-donations"
        ),
        Contributor(
            login = "tiann",
            name = "Weishu",
            githubUrl = "https://github.com/tiann",
            role = stringResource(R.string.contributor_kernel_author),
            donationUrl = "https://www.patreon.com/weishu"
        ),
        Contributor(
            login = "fatalcoder524",
            githubUrl = "https://github.com/fatalcoder524",
            role = stringResource(R.string.contributor_frontend_maintainer),
            donationUrl = "https://github.com/sponsors/fatalcoder524"
        ),
        Contributor(
            login = "pershoot",
            githubUrl = "https://github.com/pershoot",
            role = stringResource(R.string.contributor_backend_maintainer),
            donationUrl = "https://github.com/sponsors/pershoot"
        ),
        Contributor(
            login = "maxsteeel",
            name = "Max",
            githubUrl = "https://github.com/maxsteeel",
            role = stringResource(R.string.contributor_legacy_maintainer),
            donationUrl = "https://github.com/sponsors/maxsteeel"
        )
    )

    top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.contributors),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            contributors.forEach { contributor ->
                ContributorRow(
                    contributor = contributor,
                    onProfileClick = { uriHandler.openUri(contributor.githubUrl) },
                    onDonateClick = { uriHandler.openUri(contributor.donationUrl) }
                )
            }
        }
    }
}

@Composable
private fun ContributorRow(
    contributor: Contributor,
    onProfileClick: () -> Unit,
    onDonateClick: () -> Unit
) {
    var imageLoadFailed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Avatar + name/role
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onProfileClick() }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!imageLoadFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://avatars.githubusercontent.com/${contributor.login}?s=80")
                            .crossfade(true)
                            .build(),
                        contentDescription = contributor.login,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { imageLoadFailed = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Name + role
            Column(Modifier.weight(1f)) {
                Text(
                    text = contributor.name?.takeIf { it.isNotBlank() }
                        ?.let { "${contributor.login} ($it)" }
                        ?: contributor.login,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = contributor.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onDonateClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.support),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun IssueReportCard() {
    val uriHandler = LocalUriHandler.current
    val githubIssueUrl = stringResource(R.string.issue_report_github_link)
    val telegramUrl = stringResource(R.string.issue_report_telegram_link)

    top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.issue_report_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.issue_report_body),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.issue_report_body_2),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { uriHandler.openUri(githubIssueUrl) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = stringResource(R.string.issue_report_github),
                    )
                }
                IconButton(onClick = { uriHandler.openUri(telegramUrl) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_telegram),
                        contentDescription = stringResource(R.string.issue_report_telegram),
                    )
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
fun handleDynamicShortcuts(context: Context, moduleConfigs: List <Pair<ModuleViewModel.ModuleInfo, Int>>) {
    ShortcutManagerCompat.removeAllDynamicShortcuts(context)

    moduleConfigs.forEach { (module, iconRes) ->
        val shortcut = ShortcutInfoCompat.Builder(context, module.id)
            .setShortLabel(module.name)
            .setLongLabel(module.name)
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(
                Intent(context, WebUIActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = "kernelsu://webui/${module.id}".toUri()
                    putExtra("id", module.id)
                    putExtra("name", module.name)
                }
            )
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}

fun getManagerVersion(context: Context): Pair<String, Long> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return Pair(packageInfo.versionName!!, versionCode)
}

@Preview
@Composable
private fun StatusCardPreview() {
    Column {
        StatusCard(KernelVersion(5, 10, 101), 1, 1, null)
        StatusCard(KernelVersion(5, 10, 101), 20000, 1, true)
        StatusCard(KernelVersion(5, 10, 101), null, null, true)
        StatusCard(KernelVersion(4, 10, 101), null, null, false)
    }
}

@Preview
@Composable
private fun WarningCardPreview() {
    Column {
        WarningCard(message = "Warning message")
        WarningCard(
            message = "Warning message",
            MaterialTheme.colorScheme.outlineVariant,
            onClick = {})
    }
}
