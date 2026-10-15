package com.rifsxd.ksunext.ui.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

val LocalAsterPanorama = compositionLocalOf { true }
val LocalNavigationBottomInset = compositionLocalOf { 0.dp }

@Composable
fun navigationBottomPadding() =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalNavigationBottomInset.current
