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
import com.karelisio.bulle.core.backup.BackupScreen
import com.karelisio.bulle.modules.alerts.ui.AlertsSettingsScreen
import com.karelisio.bulle.ui.about.AboutScreen
import com.karelisio.bulle.modules.volet.history.NotificationHistoryScreen
import com.karelisio.bulle.modules.volet.settings.VoletSettingsScreen
import com.karelisio.bulle.ui.onboarding.PermissionsScreen
import com.karelisio.bulle.ui.onboarding.ReliabilityScreen
import com.karelisio.bulle.modules.call.ui.CallSettingsScreen
import com.karelisio.bulle.modules.music.ui.MusicSettingsScreen
import com.karelisio.bulle.modules.live.ui.LiveSettingsScreen
import com.karelisio.bulle.modules.rules.ui.RulesScreen
import com.karelisio.bulle.ui.shell.BulleShell
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
    const val MUSIC = "music"
    const val PERMISSIONS = "permissions"
    const val RELIABILITY = "reliability"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val VOLET = "volet"
    const val CALENDAR = "calendar"
    const val WEATHER = "weather"
    const val FOCUS = "focus"
    const val VOLUME = "volume"
    const val INTEGRATIONS = "integrations"
    const val MESSAGES = "messages"
    const val NETWORK = "network"
    const val BATTERY = "battery"
    const val COUNTDOWN = "countdown"
    const val SCREENTIME = "screentime"
    const val CAPTURE = "capture"
    const val VOLET_HISTORY = "volet/history"
    const val DECK_TAB = "deck/{id}"
    fun deckTab(id: String) = "deck/$id"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BulleNavHost() {
    val nav = rememberNavController()
    // Double appui sur « retour » pendant l'animation : sans garde, le second popBackStack()
    // retirait l'accueil lui-même et laissait un écran vide.
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
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
        // Racine : la coque à onglets (Accueil, Style, Contenus, Réglages) et sa barre flottante.
        // Chaque entrée ouvre un écran existant en plein écran, par-dessus la coque.
        screen(Routes.HOME) { BulleShell(onOpen = { nav.navigate(it) { launchSingleTop = true } }) }
        screen(Routes.STYLE) { StyleScreen(onBack = back) }
        screen(Routes.POSITION) { PositionScreen(onBack = back) }
        screen(Routes.VISIBILITY) { VisibilityScreen(onBack = back) }
        composable(Routes.GESTURES) { GesturesSettingsScreen(onBack = back) }
        composable(Routes.RULES) { RulesScreen(onBack = back) }
        composable(Routes.LIVE) { LiveSettingsScreen(onBack = back) }
        composable(Routes.CALLS) { CallSettingsScreen(onBack = back) }
        composable(Routes.ALERTS) { AlertsSettingsScreen(onBack = back) }
        composable(Routes.MUSIC) { MusicSettingsScreen(onBack = back) }
        composable(Routes.CALENDAR) { com.karelisio.bulle.modules.calendar.ui.CalendarSettingsScreen(onBack = back) }
        composable(Routes.FOCUS) { com.karelisio.bulle.modules.focus.ui.FocusSettingsScreen(onBack = back) }
        composable(Routes.VOLUME) { com.karelisio.bulle.modules.volume.ui.VolumeSettingsScreen(onBack = back) }
        composable(Routes.NETWORK) { com.karelisio.bulle.modules.network.ui.NetworkSettingsScreen(onBack = back) }
        composable(Routes.BATTERY) { com.karelisio.bulle.modules.battery.ui.BatterySettingsScreen(onBack = back) }
        composable(Routes.COUNTDOWN) { com.karelisio.bulle.modules.countdown.ui.CountdownSettingsScreen(onBack = back) }
        composable(Routes.SCREENTIME) { com.karelisio.bulle.modules.screentime.ui.ScreenTimeSettingsScreen(onBack = back) }
        composable(Routes.CAPTURE) { com.karelisio.bulle.modules.capture.ui.CaptureSettingsScreen(onBack = back) }
        composable(Routes.MESSAGES) { com.karelisio.bulle.modules.messages.ui.MessagesSettingsScreen(onBack = back) }
        composable(Routes.INTEGRATIONS) { com.karelisio.bulle.modules.external.ui.IntegrationsSettingsScreen(onBack = back) }
        composable(Routes.WEATHER) { com.karelisio.bulle.modules.weather.ui.WeatherSettingsScreen(onBack = back) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = back) }
        composable(Routes.RELIABILITY) { ReliabilityScreen(onBack = back) }
        composable(Routes.BACKUP) { BackupScreen(onBack = back) }
        composable(Routes.VOLET) { VoletSettingsScreen(onBack = back, onOpenHistory = { nav.navigate(Routes.VOLET_HISTORY) }) }
        composable(Routes.VOLET_HISTORY) { NotificationHistoryScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back, onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) }) }
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
