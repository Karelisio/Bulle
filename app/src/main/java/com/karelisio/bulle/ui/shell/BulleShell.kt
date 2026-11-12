package com.karelisio.bulle.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.ui.components.LocalNavAnimatedScope
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.ui.home.HomeScreen

/** Marge entre la pilule flottante et la barre de navigation système. */
private val BarMargin = 12.dp

/**
 * Racine de l'appli de réglages : onglets + barre flottante.
 * L'onglet courant survit à la rotation et au retour depuis un sous-écran (rememberSaveable).
 */
@Composable
fun BulleShell(onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(ShellNavigation.DEFAULT) }
    ShellScreen(selected = tab, onSelect = { tab = it }, onOpen = onOpen)
}

/** Coque sans état (testable) : contenu de l'onglet [selected] et barre de navigation. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ShellScreen(
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Retour depuis un onglet secondaire : revenir à l'accueil avant de quitter l'appli.
    BackHandler(enabled = ShellNavigation.interceptsBack(selected)) {
        ShellNavigation.onBack(selected)?.let(onSelect)
    }
    val reduce = rememberReduceMotion()
    val holder = rememberSaveableStateHolder()

    // Edge-to-edge : le contenu défile sous les barres, mais son début et sa fin restent visibles.
    val dir = LocalLayoutDirection.current
    val system = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val barSpace = FloatingTabBarHeight + BarMargin * 2
    val contentPadding = PaddingValues(
        start = system.calculateStartPadding(dir),
        top = system.calculateTopPadding(),
        end = system.calculateEndPadding(dir),
        bottom = system.calculateBottomPadding() + barSpace,
    )
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // La barre glisse vers le bas quand un sous-écran s'ouvre, et remonte au retour.
    val navScope = LocalNavAnimatedScope.current
    val barMotion = if (navScope == null || reduce) Modifier else with(navScope) {
        Modifier.animateEnterExit(
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it * 2 } +
                fadeIn(tween(220)),
            exit = slideOutVertically(tween(260)) { it * 2 } + fadeOut(tween(180)),
        )
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (reduce) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        // Fondu enchaîné avec un léger glissé dans le sens de l'onglet choisi.
                        val sign = ShellNavigation.direction(initialState, targetState)
                        val slide = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                        (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(slide) { sign * it / 12 })
                            .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(slide) { -sign * it / 12 })
                    }
                },
                label = "shellTab",
            ) { tab ->
                // Chaque onglet garde son défilement quand on revient dessus.
                holder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        ShellTab.HOME -> HomeScreen(onOpen = onOpen, contentPadding = contentPadding)
                        else -> EntriesTab(tab = tab, contentPadding = contentPadding, onOpen = onOpen)
                    }
                }
            }

            // Voile en dégradé sous la pilule : le contenu s'efface au lieu d'entrer en collision.
            val bg = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBottom + barSpace)
                    .then(barMotion)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.92f)))),
            )

            FloatingTabBar(
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(barMotion)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = BarMargin),
            )
        }
    }
}
