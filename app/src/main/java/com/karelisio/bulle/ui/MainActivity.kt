package com.karelisio.bulle.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.update.UpdateHost
import com.karelisio.bulle.system.Reliability
import com.karelisio.bulle.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.launch
import com.karelisio.bulle.ui.theme.BulleTheme

class MainActivity : AppCompatActivity() {

    /** null tant que les réglages ne sont pas lus (le splash reste affiché). */
    private var onboardingDone by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reliability.ensureRunning(this)
        lifecycleScope.launch { onboardingDone = SettingsRepository.current(this@MainActivity).onboardingDone }
        splash.setKeepOnScreenCondition { onboardingDone == null }

        // La bulle du splash « éclate » : elle gonfle et se dissout en révélant l'appli.
        splash.setOnExitAnimationListener { provider ->
            // iconView lève une NPE quand le splash système n'a pas d'icône (certains lanceurs / ROM
            // OEM, relance depuis les récents) : on se contente alors du fondu de la racine.
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val root: View = provider.view
            AnimatorSet().apply {
                val fade = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f)
                if (icon != null) {
                    playTogether(
                        ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.8f),
                        ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.8f),
                        ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f),
                        fade,
                    )
                } else {
                    play(fade)
                }
                interpolator = AnticipateInterpolator(1.4f)
                duration = 380L
                doOnEnd { provider.remove() }
                start()
            }
        }

        setContent {
            BulleTheme(highContrast = com.karelisio.bulle.ui.theme.rememberHighContrast()) {
                val done = onboardingDone ?: return@BulleTheme
                // Premier lancement : onboarding, puis fondu vers l'appli.
                AnimatedContent(
                    targetState = done,
                    transitionSpec = { (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.96f)).togetherWith(fadeOut(tween(250))) },
                    label = "onboarding",
                ) { finished ->
                    if (finished) {
                        BulleNavHost()
                        UpdateHost()
                    } else {
                        OnboardingScreen(onFinished = { onboardingDone = true })
                    }
                }
            }
        }
    }
}
