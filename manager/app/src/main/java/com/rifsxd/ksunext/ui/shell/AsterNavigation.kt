/*
 * Aster navigation adaptation for KernelSU-Next.
 * Based on the layout and navigation model of LyraVoid/Aster (GPL-3.0).
 * See docs/ASTER_UI.md for the source revision and adaptation notes.
 */
package com.rifsxd.ksunext.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.screen.BottomBarDestination

val LocalAsterPanorama = compositionLocalOf { true }
val LocalNavigationBottomInset = compositionLocalOf { 0.dp }

/** One inset contract for primary and detail screens, independent of navigation layout. */
@Composable
fun navigationBottomPadding() =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalNavigationBottomInset.current

/** Pure UI shell: unavailable entries remain discoverable, without navigating into root actions. */
@Composable
fun AsterNavigation(
    navController: NavHostController,
    fullFeatured: Boolean,
    rail: Boolean,
    modeLabel: String,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val navigator = navController.rememberDestinationsNavigator()
    val entries = BottomBarDestination.entries
    val onSelect: (BottomBarDestination) -> Unit = { destination ->
        if (destination.rootRequired && !fullFeatured) {
            onUnavailable()
        } else if (currentRoute != destination.direction.route) {
            navigator.navigate(destination.direction) {
                // Home is the anchor; restoring its saved stack would restore the old tab too.
                popUpTo(NavGraphs.root.startRoute) { saveState = true }
                launchSingleTop = true
                restoreState = destination != BottomBarDestination.Home
            }
        }
    }
    if (rail) {
        Column(
            modifier = modifier
                .width(80.dp)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .padding(horizontal = 6.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_ksu_next),
                contentDescription = stringResource(R.string.app_name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp).size(32.dp),
            )
            entries.forEach { destination ->
                NavigationEntry(
                    destination = destination,
                    selected = currentRoute == destination.direction.route,
                    available = fullFeatured || !destination.rootRequired,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    } else {
        Surface(
            modifier = modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                Modifier.padding(6.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEach { destination ->
                    NavigationEntry(
                        destination = destination,
                        selected = currentRoute == destination.direction.route,
                        available = fullFeatured || !destination.rootRequired,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationEntry(
    destination: BottomBarDestination,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.label)
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 10.dp)
            .alpha(if (available) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (selected) destination.iconSelected else destination.iconNotSelected,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
