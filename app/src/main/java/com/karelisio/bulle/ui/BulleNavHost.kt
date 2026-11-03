package com.karelisio.bulle.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import com.karelisio.bulle.ui.components.LocalNavAnimatedScope
import com.karelisio.bulle.ui.components.LocalSharedTransitionScope
import com.karelisio.bulle.ui.settings.PositionScreen
import com.karelisio.bulle.ui.settings.StyleScreen
import com.karelisio.bulle.ui.settings.VisibilityScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.karelisio.bulle.modules.deck.ui.DeckSettingsScreen
import com.karelisio.bulle.modules.deck.ui.DeckTabScreen
import com.karelisio.bulle.modules.alerts.ui.AlertsSettingsScreen
import com.karelisio.bulle.modules.call.ui.CallSettingsScreen
import com.karelisio.bulle.modules.live.ui.LiveSettingsScreen
import com.karelisio.bulle.modules.rules.ui.RulesScreen
import com.karelisio.bulle.ui.home.HomeScreen
import com.karelisio.bulle.ui.settings.GesturesSettingsScreen

/** Routes de l'appli de réglages. */
object Routes {
    const val HOME = "home"
    const val GESTURES = "gestures"
    const val STYLE = "style"
    const val POSITION = "position"
    const val VISIBILITY = "visibility"
    const val DECK = "deck"
    const val RULES = "rules"
    const val LIVE = "live"
    const val CALLS = "calls"
    const val ALERTS = "alerts"
    const val DECK_TAB = "deck/{id}"
    fun deckTab(id: String) = "deck/$id"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BulleNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(380)) { it / 4 } + fadeIn(tween(260)) },
        exitTransition = { scaleOut(tween(380), targetScale = 0.94f) + fadeOut(tween(200)) },
        popEnterTransition = { scaleIn(tween(380), initialScale = 0.94f) + fadeIn(tween(260)) },
        popExitTransition = { slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(200)) },
    ) {
        screen(Routes.HOME) { HomeScreen(onOpen = { nav.navigate(it) }) }
        screen(Routes.STYLE) { StyleScreen(onBack = back) }
        screen(Routes.POSITION) { PositionScreen(onBack = back) }
        screen(Routes.VISIBILITY) { VisibilityScreen(onBack = back) }
        composable(Routes.GESTURES) { GesturesSettingsScreen(onBack = back) }
        composable(Routes.RULES) { RulesScreen(onBack = back) }
        composable(Routes.LIVE) { LiveSettingsScreen(onBack = back) }
        composable(Routes.CALLS) { CallSettingsScreen(onBack = back) }
        composable(Routes.ALERTS) { AlertsSettingsScreen(onBack = back) }
        composable(Routes.DECK) { DeckSettingsScreen(onBack = back, onOpenTab = { nav.navigate(Routes.deckTab(it)) }) }
        composable(Routes.DECK_TAB) { entry ->
            DeckTabScreen(tabId = entry.arguments?.getString("id").orEmpty(), onBack = back)
        }
    }
    }
    }
}

/** Destination qui expose sa portée d'animation (éléments partagés). */
private fun NavGraphBuilder.screen(route: String, content: @Composable () -> Unit) {
    composable(route) {
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content() }
    }
}
