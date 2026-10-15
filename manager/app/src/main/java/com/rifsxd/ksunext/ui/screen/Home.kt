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
    val kernelVersion = getKernelVersion()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val isManager = Natives.isManager
    val fullFeatured = Natives.isFullFeatured()
    val ksuVersion = if (isManager) Natives.version else null
    val ksuVersionTag = if (isManager) Natives.getVersionTag() else null
    val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
    val managerUAPIVersion = Natives.managerUAPIVersion

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val developerOptionsEnabled = prefs.getBoolean("enable_developer_options", false)
    
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
            val lkmMode = ksuVersion?.let {
                Natives.isLkmMode
            }

            if (LocalAsterPanorama.current) AsterHomeScene()

            StatusCard(
                kernelVersion,
                ksuVersion,
                kernelUAPIVersion,
                lkmMode,
                ksuVersionTagParam = ksuVersionTag
            ) {
                navigator.navigate(InstallScreenDestination)
            }

            val homeDestination = BottomBarDestination.entries.firstOrNull()
            val startRoute = homeDestination?.direction?.route

            if (fullFeatured) {
                AsterResponsivePair(
                    first = {
                        SuperuserCard(onClick = {
                            navigator.navigate(SuperUserScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        })
                    },
                    second = {
                        ModuleCard(onClick = {
                            navigator.navigate(ModuleScreenDestination) {
                                popUpTo(NavGraphs.root.startRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        })
                    }
                )
            }

            val currentVersionCode = getManagerVersion(context).second
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
                UpdateCard()
            }

            InfoCard(autoExpand = developerOptionsEnabled)
            IssueReportCard()
            ContributorsCard()
            Spacer(Modifier)
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

private val homeWallpaperMutex = Mutex()

/** Aster's panoramic hero, with local artwork chosen by the device owner. */
@Composable
private fun AsterHomeScene() {
    val context = LocalContext.current
    val wallpaper = remember(context) { File(context.filesDir, "aster_home_wallpaper.jpg") }
    var wallpaperVersion by remember { mutableLongStateOf(wallpaper.lastModified()) }
    var hasWallpaper by remember { mutableStateOf(wallpaper.isFile) }
    var menuOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                try {
                    withContext(Dispatchers.IO) {
                        homeWallpaperMutex.withLock {
                            saveHomeWallpaper(context.applicationContext, uri, wallpaper)
                        }
                    }
                    wallpaperVersion = System.currentTimeMillis()
                    hasWallpaper = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.aster_home_wallpaper_error, Toast.LENGTH_LONG).show()
                } finally {
                    importing = false
                }
            }
        }
    }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.aster_home_morning
        in 12..17 -> R.string.aster_home_afternoon
        else -> R.string.aster_home_evening
    }
    val foreground = if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer
            )))
    ) {
        if (hasWallpaper) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(wallpaper)
                    .memoryCacheKey("home-wallpaper-$wallpaperVersion")
                    .diskCacheKey("home-wallpaper-$wallpaperVersion")
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.82f)
            ))))
        } else {
            Icon(
                painterResource(R.drawable.ic_ksu_next), contentDescription = null,
                tint = foreground.copy(alpha = 0.08f),
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(128.dp)
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 94.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(greeting), color = foreground,
                style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.app_name), color = foreground,
                style = MaterialTheme.typography.headlineLarge)
            Text("${Build.MANUFACTURER.replaceFirstChar { it.titlecase() }} ${Build.MODEL}",
                color = foreground.copy(alpha = 0.84f), style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            FilledTonalIconButton(onClick = { menuOpen = true }, enabled = !importing) {
                if (importing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Wallpaper, stringResource(R.string.aster_home_appearance))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.aster_home_choose_wallpaper)) },
                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                if (hasWallpaper) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.aster_home_clear_wallpaper)) },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                importing = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        homeWallpaperMutex.withLock {
                                            currentCoroutineContext().ensureActive()
                                            AtomicFile(wallpaper).delete()
                                            check(!wallpaper.exists())
                                        }
                                    }
                                    hasWallpaper = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast.makeText(context, R.string.aster_home_wallpaper_error, Toast.LENGTH_LONG).show()
                                } finally {
                                    importing = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun saveHomeWallpaper(context: Context, uri: Uri, destination: File) {
    // Bound both the encoded input and decoded dimensions. The picker grant is used only
    // while importing; reopening Home needs no external-storage access or URI permission.
    val limit = 24 * 1024 * 1024
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count: Int
        while (input.read(buffer).also { count = it } != -1) {
            currentCoroutineContext().ensureActive()
            require(output.size() + count <= limit) { "Wallpaper is too large" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: error("Cannot read wallpaper")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder applies EXIF orientation, so camera portraits stay upright.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val ratio = (2048f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize(
                (info.size.width * ratio).toInt().coerceAtLeast(1),
                (info.size.height * ratio).toInt().coerceAtLeast(1)
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Cannot decode image")
    }
    val atomic = AtomicFile(destination)
    try {
        currentCoroutineContext().ensureActive()
        val stream = atomic.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            currentCoroutineContext().ensureActive()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    } finally {
        bitmap.recycle()
    }
}

@Composable
private fun SuperuserCard(onClick: (() -> Unit)? = null) {
    val count = getSuperuserCount()
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (count <= 1) {
                        stringResource(R.string.home_superuser_count_singular)
                    } else {
                        stringResource(R.string.home_superuser_count_plural)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleCard(onClick: (() -> Unit)? = null) {
    val count = getModuleCount()
    val moduleViewModel: ModuleViewModel = viewModel()

    val moduleUpdateCount = remember(moduleViewModel.moduleList) {
        moduleViewModel.moduleList.count { module ->
            module.enabled && (moduleViewModel.checkUpdate(module).first.isNotEmpty())
        }
    }

    // State machine: 0 = nothing, 1 = show "+ Update!", 2 = show "+ X"
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(moduleUpdateCount) {
        if (moduleUpdateCount > 0) {
            step = 1
            delay(1200) // show "+ Update!" for a moment
            step = 2
        } else {
            step = 0
        }
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (count <= 1) {
                        stringResource(R.string.home_module_count_singular)
                    } else {
                        stringResource(R.string.home_module_count_plural)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (moduleUpdateCount > 0) {
                        Spacer(Modifier.width(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Keep the "|" static
                            Text(
                                text = "|",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.width(4.dp))

                            // Animate only the right-side text
                            AnimatedContent(
                                targetState = step,
                                transitionSpec = {
                                    slideInHorizontally { -it } + fadeIn() togetherWith
                                            slideOutHorizontally { it } + fadeOut()
                                }
                            ) { target ->
                                when (target) {
                                    1 -> Text(
                                        text = stringResource(id = R.string.home_module_update_available),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    2 -> Text(
                                        text = buildAnnotatedString {
                                            append(moduleUpdateCount.toString())
                                            append("*")
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val latestVersionInfo = LatestVersionInfo()
    
    var preferSpoofed by remember { mutableStateOf(false) }
    
    val newVersion by produceState(initialValue = latestVersionInfo, key1 = preferSpoofed) {
        value = withContext(Dispatchers.IO) {
            checkNewVersion(preferSpoofed)
        }
    }

    val currentVersionCode = getManagerVersion(context).second
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
        ElevatedCard(
            modifier = Modifier.clickable {
                if (changelog.isEmpty()) {
                    uriHandler.openUri(newVersionUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(end = 20.dp)
                    )
                    Text(
                        text = if (!newVersionTag.isNullOrEmpty()) {
                            stringResource(id = R.string.new_version_available, newVersionTag, newVersionCode)
                        } else {
                            stringResource(id = R.string.new_version_available, "", newVersionCode)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.select_build_type),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!preferSpoofed) {
                            FilledTonalButton(
                                onClick = { preferSpoofed = false },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.main), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { preferSpoofed = false },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.69f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.main), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        
                        if (preferSpoofed) {
                            FilledTonalButton(
                                onClick = { preferSpoofed = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.spoofed), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { preferSpoofed = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.69f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.spoofed), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
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
private fun InfoCard(autoExpand: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val isManager = remember { Natives.isManager }
    val ksuVersion = remember(isManager) { if (isManager) Natives.version else null }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val developerOptionsEnabled = remember(prefs) { prefs.getBoolean("enable_developer_options", false) }

    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            expanded = true
        }
    }

    val moduleViewModel: ModuleViewModel = viewModel()
    var homeInfo by remember { mutableStateOf(HomeInfoSnapshot()) }

    LaunchedEffect(ksuVersion, moduleViewModel.moduleList) {
        homeInfo = withContext(Dispatchers.IO) {
            buildHomeInfoSnapshot(context, ksuVersion, moduleViewModel.moduleList)
        }
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            @Composable
            fun InfoCardItem(label: String, content: String, icon: Any? = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        when (icon) {
                            is ImageVector -> Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 20.dp)
                            )
                            is Painter -> Icon(
                                painter = icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
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

    Card {
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

    Card {
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
