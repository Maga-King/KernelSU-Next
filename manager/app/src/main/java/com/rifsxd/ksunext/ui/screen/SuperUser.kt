package com.rifsxd.ksunext.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.rifsxd.ksunext.ui.LocalScrollState
import com.rifsxd.ksunext.ui.rememberScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.ListItemDefaults
import com.dergoogler.mmrl.ui.component.LabelItem
import com.dergoogler.mmrl.ui.component.LabelItemDefaults
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppProfileScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SulogScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.rifsxd.ksunext.ksuApp
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.component.AsterListTopBar
import top.yukonga.miuix.kmp.basic.Card as AsterCard
import top.yukonga.miuix.kmp.basic.Text as AsterText
import top.yukonga.miuix.kmp.basic.Scaffold as AsterScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.rifsxd.ksunext.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SuperUserScreen(navigator: DestinationsNavigator) {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    // Bottom bar scroll tracking
    val bottomBarScrollState = LocalScrollState.current
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null

    LaunchedEffect(navigator) {
        viewModel.search = ""
        if (viewModel.appList.isEmpty()) {
            viewModel.fetchAppList()
        }
    }

    AsterScaffold(
        topBar = {
            AsterListTopBar(
                title = stringResource(R.string.superuser),
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = "" },
                actionsContent = {
                    IconButton(onClick = { navigator.navigate(SulogScreenDestination) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = stringResource(R.string.settings_sulog)
                        )
                    }
                },
                dropdownContent = {
                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenu(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            DropdownMenuItem(text = {
                                Text(stringResource(R.string.refresh))
                            }, onClick = {
                                scope.launch {
                                    viewModel.fetchAppList()
                                }
                                showDropdown = false
                            })
                            DropdownMenuItem(text = {
                                Text(
                                    if (viewModel.showSystemApps) {
                                        stringResource(R.string.hide_system_apps)
                                    } else {
                                        stringResource(R.string.show_system_apps)
                                    }
                                )
                            }, onClick = {
                                viewModel.updateShowSystemApps(!viewModel.showSystemApps)
                                showDropdown = false
                            })
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        PullToRefreshBox(
            modifier = Modifier.padding(innerPadding),
            onRefresh = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                scope.launch { viewModel.fetchAppList() }
            },
            isRefreshing = viewModel.isRefreshing
        ) {


            val navBarPadding = com.rifsxd.ksunext.ui.shell.navigationBottomPadding()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .let { modifier ->
                        if (bottomBarScrollConnection != null) {
                            modifier
                                .nestedScroll(bottomBarScrollConnection)
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
                        } else {
                            modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 16.dp + navBarPadding
                )
            ) {
                items(
                    viewModel.appList.filter { it.displayIdentifier != ksuApp.packageName },
                    key = { it.displayIdentifier + it.uid }
                ) { app ->
                    AppItem(app) {
                        navigator.navigate(AppProfileScreenDestination(app))
                    }
                }
            }
        }
    }
}

// Adapted directly from Aster SuperUserAppItem; profile editing stays with KernelSU.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppItem(app: SuperUserViewModel.AppInfo, onClickListener: () -> Unit) {
    val context = LocalContext.current
    val developerOptionsEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("enable_developer_options", false)
    AsterCard(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = false,
        onClick = onClickListener,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(app.packageInfo).crossfade(true).build(),
                    contentDescription = app.label,
                    modifier = Modifier.padding(4.dp).size(48.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    AsterText(text = app.label, style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    AsterText(text = app.displayIdentifier, style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
                    FlowRow(Modifier.padding(top = 2.dp)) {
                        if (app.allowSu) {
                            AsterStatusBadge(if (developerOptionsEnabled) "ROOT | UID: ${app.uid}" else "ROOT")
                        } else if (Natives.uidShouldUmount(app.uid)) {
                            AsterStatusBadge("UMOUNT")
                        }
                        if (app.hasCustomProfile) AsterStatusBadge("CUSTOM")
                        else if (!app.allowSu && !Natives.uidShouldUmount(app.uid)) AsterStatusBadge("DEFAULT")
                    }
                }
                // The existing app-profile page owns authorization writes and confirmation.
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// Aster's StatusBadge, with KernelSU's original status vocabulary.
@Composable
private fun AsterStatusBadge(label: String) {
    Box(Modifier.padding(top = 4.dp, end = 4.dp)
        .background(MiuixTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))) {
        AsterText(text = label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            color = MiuixTheme.colorScheme.onTertiaryContainer, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
fun LabelText(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .background(
                Color.Black,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 5.dp),
            style = TextStyle(
                fontSize = 8.sp,
                color = Color.White,
            )
        )
    }
}
