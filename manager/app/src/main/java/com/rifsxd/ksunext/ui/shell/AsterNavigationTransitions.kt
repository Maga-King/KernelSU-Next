// Adapted from lyravoid/Aster @ 84a9116606b01e436e9b58852acee9e8b9b9e627, GPL-3.0.
package com.rifsxd.ksunext.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle

/** Aster's independently timed slide, fade and scale, with KSUN's primary routes retained. */
internal class AsterNavigationTransitions(
    private val primaryRoutes: Set<String>,
    private val visiblePrimaryRoutes: List<String>,
    private val layoutDirection: Int,
) : NavHostAnimatedDestinationStyle() {
    private val fade = tween<Float>(300, easing = FastOutSlowInEasing)
    private val offset = tween<IntOffset>(400, easing = FastOutSlowInEasing)
    private val scale = tween<Float>(500, easing = FastOutSlowInEasing)
    private val tabFade = tween<Float>(340)

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.bothPrimary(): Boolean =
        initialState.destination.route in primaryRoutes && targetState.destination.route in primaryRoutes

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryDirection(): Int =
        (if (visiblePrimaryRoutes.indexOf(targetState.destination.route) >
            visiblePrimaryRoutes.indexOf(initialState.destination.route)) 1 else -1) * layoutDirection

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryOffset() = tween<IntOffset>(
        primaryPageDuration(
            visiblePrimaryRoutes.indexOf(initialState.destination.route),
            visiblePrimaryRoutes.indexOf(targetState.destination.route),
        ),
        easing = EaseInOut,
    )

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            bothPrimary() -> {
                val direction = primaryDirection()
                slideInHorizontally(initialOffsetX = { it * direction }, animationSpec = primaryOffset())
            }
            targetState.destination.route !in primaryRoutes ->
                slideInHorizontally(initialOffsetX = { it * layoutDirection }, animationSpec = offset) +
                    fadeIn(animationSpec = fade) + scaleIn(initialScale = 1.15f, animationSpec = scale)
            else -> fadeIn(animationSpec = tabFade)
        }
    }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (bothPrimary()) {
            val direction = primaryDirection()
            slideOutHorizontally(targetOffsetX = { -it * direction }, animationSpec = primaryOffset())
        } else {
            slideOutHorizontally(targetOffsetX = { -it * layoutDirection }, animationSpec = offset) +
                fadeOut(animationSpec = fade) + scaleOut(targetScale = 0.95f, animationSpec = scale)
        }
    }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (bothPrimary()) {
            val direction = primaryDirection()
            slideInHorizontally(initialOffsetX = { it * direction }, animationSpec = primaryOffset())
        } else {
            slideInHorizontally(initialOffsetX = { -it * layoutDirection }, animationSpec = offset) +
                fadeIn(animationSpec = fade) + scaleIn(initialScale = 0.95f, animationSpec = scale)
        }
    }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (bothPrimary()) {
            val direction = primaryDirection()
            slideOutHorizontally(targetOffsetX = { -it * direction }, animationSpec = primaryOffset())
        } else {
            slideOutHorizontally(targetOffsetX = { it * layoutDirection }, animationSpec = offset) +
                fadeOut(animationSpec = fade) + scaleOut(targetScale = 1.15f, animationSpec = scale)
        }
    }
}
