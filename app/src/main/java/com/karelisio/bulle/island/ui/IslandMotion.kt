package com.karelisio.bulle.island.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.MotionStyle

/**
 * Ressorts de l'île selon le style choisi.
 *  - grow : gonflement (léger dépassement élastique) ;
 *  - shrink : rétractation (petit rebond).
 * Style « Accentué » (PLAYFUL) : amortissement faible, dépassement bien visible.
 */
class IslandMotion(private val settings: MotionSettings, private val reduceMotion: Boolean) {

    private val speed = settings.speed.coerceIn(0.4f, 2.5f)
    private fun k(stiffness: Float) = stiffness * speed * speed

    fun grow(): AnimationSpec<Float> = if (reduceMotion) snap() else when (settings.style) {
        MotionStyle.SOFT -> spring(dampingRatio = 0.70f, stiffness = k(260f), visibilityThreshold = 0.5f)
        MotionStyle.SNAPPY -> spring(dampingRatio = 0.62f, stiffness = k(620f), visibilityThreshold = 0.5f)
        MotionStyle.SOBER -> spring(dampingRatio = 1f, stiffness = k(480f), visibilityThreshold = 0.5f)
        MotionStyle.PLAYFUL -> spring(dampingRatio = 0.46f, stiffness = k(340f), visibilityThreshold = 0.5f)
    }

    fun shrink(): AnimationSpec<Float> = if (reduceMotion) snap() else when (settings.style) {
        MotionStyle.SOFT -> spring(dampingRatio = 0.58f, stiffness = k(360f), visibilityThreshold = 0.5f)
        MotionStyle.SNAPPY -> spring(dampingRatio = 0.55f, stiffness = k(800f), visibilityThreshold = 0.5f)
        MotionStyle.SOBER -> spring(dampingRatio = 1f, stiffness = k(700f), visibilityThreshold = 0.5f)
        MotionStyle.PLAYFUL -> spring(dampingRatio = 0.42f, stiffness = k(460f), visibilityThreshold = 0.5f)
    }

    /** La goutte secondaire s'éloigne : ressort étiré, un peu plus lent que le gonflement. */
    fun detach(): AnimationSpec<Float> = if (reduceMotion) snap() else when (settings.style) {
        MotionStyle.SOFT -> spring(dampingRatio = 0.62f, stiffness = k(180f), visibilityThreshold = 0.5f)
        MotionStyle.SNAPPY -> spring(dampingRatio = 0.6f, stiffness = k(420f), visibilityThreshold = 0.5f)
        MotionStyle.SOBER -> spring(dampingRatio = 1f, stiffness = k(380f), visibilityThreshold = 0.5f)
        MotionStyle.PLAYFUL -> spring(dampingRatio = 0.44f, stiffness = k(240f), visibilityThreshold = 0.5f)
    }

    /** La goutte revient se fondre dans la principale. */
    fun merge(): AnimationSpec<Float> = if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = k(300f), visibilityThreshold = 0.5f)

    /** Dépassement maximal attendu, en fraction de l'écart (pour dimensionner la fenêtre). */
    val overshoot: Float = when (settings.style) {
        MotionStyle.SOFT -> 0.18f
        MotionStyle.SNAPPY -> 0.22f
        MotionStyle.SOBER -> 0.02f
        // ζ ≈ 0.42–0.46 : environ 20–23 % de dépassement théorique, marge doublée comme les autres styles.
        MotionStyle.PLAYFUL -> 0.42f
    }

    /** Apparition du contenu, décalée pour attendre que la bulle soit gonflée. */
    fun contentIn(): AnimationSpec<Float> =
        if (reduceMotion) snap() else tween(durationMillis = (170 / speed).toInt(), delayMillis = (50 / speed).toInt())

    fun contentOut(): AnimationSpec<Float> = if (reduceMotion) snap() else tween(durationMillis = (90 / speed).toInt())

    fun fade(): AnimationSpec<Float> = if (reduceMotion) snap() else tween(durationMillis = (200 / speed).toInt())
}
