/* Aster shell destination adapter; KernelSU-Next retains its existing root gates and routes. */
package com.rifsxd.ksunext.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import com.rifsxd.ksunext.ui.screen.BottomBarDestination
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Settings

typealias PrimaryDestination = BottomBarDestination

val PrimaryDestination.icon: ImageVector
    get() = when (this) {
        PrimaryDestination.Home -> MiuixIcons.Home
        PrimaryDestination.SuperUser -> MiuixIcons.Lock
        PrimaryDestination.Module -> MiuixIcons.GridView
        PrimaryDestination.Settings -> MiuixIcons.Settings
    }
