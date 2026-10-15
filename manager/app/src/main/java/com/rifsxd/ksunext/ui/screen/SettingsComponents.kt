/* Ported from LyraVoid/Aster (GPL-3.0); see docs/ASTER_UI.md. */
package com.rifsxd.ksunext.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.rifsxd.ksunext.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
@Composable
internal fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        SettingsCard(content = content)
    }
}

/**
 * A card of preference rows with no name above it. A page that is cut by subject is already named
 * by its own bar, so naming the card again would only say what the bar just said.
 */
@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
    ) {
        content()
    }
}

/**
 * The bar every settings page wears: the subject it holds, the way back to the page that lists the
 * subjects, and the scroll that bar answers to.
 *
 * The name of the subject is written large and belongs to the page rather than to the bar: it
 * leaves with the content when the page is scrolled, and the bar keeps only the small name once the
 * large one is gone. Passing no navigator leaves the bar without a way back, which is what the page
 * that lists the subjects wants.
 */
@Composable
internal fun SettingsTopBar(
    @StringRes title: Int,
    scrollBehavior: ScrollBehavior,
    navigator: DestinationsNavigator? = null,
) {
    TopAppBar(
        title = stringResource(title),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (navigator != null) {
                IconButton(onClick = { navigator.popBackStack() }) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(R.string.aster_back),
                    )
                }
            }
        },
    )
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.width(12.dp))
}

/**
 * The manager version, worn as a pill on the end of the row that leads to the page about it. It
 * says the same thing the page does before it is opened, so the row does not have to spell the
 * version out in its summary.
 */
@Composable
internal fun VersionBadge(version: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = VersionBadgeAlpha))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = version,
            style = MiuixTheme.textStyles.footnote1,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

private const val VersionBadgeAlpha = 0.12f
