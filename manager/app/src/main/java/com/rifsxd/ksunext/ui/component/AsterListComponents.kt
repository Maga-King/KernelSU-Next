// Ported from lyravoid/Aster APModuleComponents.kt and SuperUser.kt (GPL-3.0).
// KernelSU adapters preserve its actions, module metadata, shortcuts, and confirmations.
package com.rifsxd.ksunext.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.viewmodel.ModuleViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Aster's SuperUserTopBar search layout, with KernelSU actions supplied by the caller. */
@Composable
internal fun AsterListTopBar(
    title: String,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearClick: () -> Unit,
    scrollBehavior: ScrollBehavior,
    actionsContent: @Composable RowScope.() -> Unit = {},
    dropdownContent: @Composable () -> Unit = {},
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    fun changeExpanded(expanded: Boolean) {
        searchExpanded = expanded
        if (!expanded) onClearClick()
    }
    TopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        actions = { actionsContent(); dropdownContent() },
        bottomContent = {
            SearchBar(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                inputField = {
                    InputField(
                        query = searchText,
                        onQueryChange = onSearchTextChange,
                        onSearch = { searchExpanded = false },
                        expanded = searchExpanded,
                        onExpandedChange = ::changeExpanded,
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = ::changeExpanded,
                content = {},
            )
        },
    )
}

@Composable
internal fun AsterModuleCard(
    module: ModuleViewModel.ModuleInfo,
    checked: Boolean,
    updateAvailable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onAction: () -> Unit,
    onUninstall: () -> Unit,
    onUndoUninstall: () -> Unit,
    onExpand: () -> Unit,
    onLongPress: () -> Unit,
    onActionShortcut: () -> Unit,
    onWebUiShortcut: () -> Unit,
    onDonate: (() -> Unit)?,
    actionsEnabled: Boolean,
    background: @Composable BoxScope.() -> Unit = {},
    details: @Composable () -> Unit = {},
    badges: @Composable () -> Unit = {},
) {
    val decoration = if (module.remove) {
        TextDecoration.LineThrough
    } else {
        TextDecoration.None
    }
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onExpand,
        onLongPress = onLongPress,
    ) {
        Box {
            background()
            Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (module.remove) 0.62f else 1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = module.name,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = decoration,
                    )

                    if (module.isMetaModule) {
                        Spacer(Modifier.width(8.dp))
                        AsterModuleBadge(text = "META")
                    }
                }

                Text(
                    text = "${module.version} · ${module.author}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = decoration,
                )

                if (module.update || module.remove) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (module.update) {
                            AsterModuleBadge(
                                text = stringResource(R.string.module_update),
                                containerColor = MiuixTheme.colorScheme.primaryContainer,
                                contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (module.remove) {
                            AsterModuleBadge(
                                text = stringResource(R.string.uninstall),
                                containerColor = MiuixTheme.colorScheme.errorContainer,
                                contentColor = MiuixTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            IndicatorSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = !module.update && !module.remove,
            )
        }

        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(if (module.remove) 0.62f else 1f),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textDecoration = decoration,
            )
        }

        details()
        badges()

        HorizontalDivider(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            color = MiuixTheme.colorScheme.dividerLine,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (updateAvailable) {
                AsterModuleIconAction(
                    icon = MiuixIcons.Download,
                    contentDescription = stringResource(R.string.module_update),
                    containerColor = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                    onClick = onUpdate,
                )
            }

            if (module.hasActionScript) {
                AsterModuleIconAction(
                    icon = MiuixIcons.Play,
                    contentDescription = stringResource(R.string.action),
                    onClick = onAction,
                    enabled = actionsEnabled,
                    onLongClick = if (!module.remove) onActionShortcut else null,
                )
            }

            if (module.hasWebUi) {
                AsterModuleIconAction(
                    icon = MiuixIcons.Link,
                    contentDescription = stringResource(R.string.open),
                    onClick = onOpen,
                    enabled = actionsEnabled,
                    onLongClick = if (!module.remove) onWebUiShortcut else null,
                )
            }

            if (onDonate != null) {
                AsterModuleIconAction(
                    icon = Icons.Outlined.AttachMoney,
                    contentDescription = stringResource(R.string.donate),
                    enabled = actionsEnabled,
                    onClick = onDonate,
                )
            }
            Spacer(Modifier.weight(1f))

            if (module.remove) {
                AsterModuleIconAction(
                    icon = MiuixIcons.Undo,
                    contentDescription = stringResource(R.string.restore),
                    onClick = onUndoUninstall,
                )
            } else {
                AsterModuleIconAction(
                    icon = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.uninstall),
                    containerColor = MiuixTheme.colorScheme.errorContainer,
                    contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    onClick = onUninstall,
                )
            }
        }
            }
        }
    }
}

@Composable
internal fun AsterModuleBadge(
    text: String,
    containerColor: Color = MiuixTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun AsterModuleIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MiuixTheme.colorScheme.secondaryVariant,
    contentColor: Color = MiuixTheme.colorScheme.onSecondaryVariant,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Miuix's IconButton has no long press and the card's own long press belongs to another
    // feature, so this is that button drawn the way Miuix draws it (same surface, same 40dp),
    // with the gesture the shortcut needs.
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .then(
                if (containerColor == Color.Unspecified) {
                    Modifier
                } else {
                    Modifier.squircleBackground(color = containerColor, cornerRadius = 40.dp)
                },
            )
            .combinedClickable(
                role = Role.Button,
                enabled = enabled,
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = contentColor,
        )
    }
}
