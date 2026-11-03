package com.karelisio.bulle.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.karelisio.bulle.ui.home.HomeScreen
import com.karelisio.bulle.ui.settings.GesturesSettingsScreen

/** Routes de l'appli de réglages. */
object Routes {
    const val HOME = "home"
    const val GESTURES = "gestures"
}

@Composable
fun BulleNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(380)) { it / 4 } + fadeIn(tween(260)) },
        exitTransition = { scaleOut(tween(380), targetScale = 0.94f) + fadeOut(tween(200)) },
        popEnterTransition = { scaleIn(tween(380), initialScale = 0.94f) + fadeIn(tween(260)) },
        popExitTransition = { slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(200)) },
    ) {
        composable(Routes.HOME) { HomeScreen(onOpen = { nav.navigate(it) }) }
        composable(Routes.GESTURES) { GesturesSettingsScreen(onBack = back) }
    }
}
