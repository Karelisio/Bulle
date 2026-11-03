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
import com.karelisio.bulle.ui.home.HomeScreen
import com.karelisio.bulle.system.Reliability
import com.karelisio.bulle.ui.theme.BulleTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reliability.ensureRunning(this)

        // La bulle du splash « éclate » : elle gonfle et se dissout en révélant l'appli.
        splash.setOnExitAnimationListener { provider ->
            val icon: View = provider.iconView
            val root: View = provider.view
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.8f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.8f),
                    ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f),
                )
                interpolator = AnticipateInterpolator(1.4f)
                duration = 380L
                doOnEnd { provider.remove() }
                start()
            }
        }

        setContent {
            BulleTheme {
                HomeScreen()
            }
        }
    }
}
