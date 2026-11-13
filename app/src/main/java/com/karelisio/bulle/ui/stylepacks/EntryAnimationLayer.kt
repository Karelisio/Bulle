package com.karelisio.bulle.ui.stylepacks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.karelisio.bulle.core.style.EntryAnimation
import kotlin.math.max

/**
 * Joue [animation] une fois, à la première composition de [content] (donc à l'entrée d'un
 * nouveau contenu dans l'AnimatedContent de l'île). [EntryAnimation.INFLATE] : contenu émis
 * tel quel, sans calque ni boîte, pour un rendu strictement identique à l'existant.
 * Les valeurs animées ne sont lues que dans graphicsLayer / draw (aucune recomposition).
 */
@Composable
fun EntryAnimated(
    animation: EntryAnimation,
    accent: Color,
    speed: Float = 1f,
    modifier: Modifier = Modifier,
    replayKey: Any? = Unit,
    /** Aperçu (réglages) : le gonflement est alors rejoué aussi, comme le fait l'île. */
    preview: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (animation == EntryAnimation.INFLATE && !preview) {
        content()
        return
    }
    // Clé de rejeu seulement : changer de réglage pendant l'affichage ne rejoue pas l'entrée.
    val p = remember(replayKey) { Animatable(0f) }
    LaunchedEffect(replayKey) { p.animateTo(1f, EntryMotion.spec(animation, speed)) }
    Box(
        modifier
            .graphicsLayer { EntryMotion.applyLayer(this, animation, p.value) }
            .then(
                if (animation == EntryAnimation.RIPPLE) {
                    Modifier.drawWithContent {
                        drawContent()
                        val v = p.value.coerceIn(0f, 1f)
                        if (v < 1f) {
                            val r = max(size.width, size.height) * (0.15f + 0.75f * v)
                            drawCircle(
                                accent.copy(alpha = 0.7f * (1f - v)),
                                radius = r,
                                center = Offset(size.width / 2, size.height / 2),
                                style = Stroke(width = size.height * 0.12f * (1f - v) + 1f),
                            )
                        }
                    }
                } else Modifier,
            ),
    ) { content() }
}

/**
 * Suivi des entrées dans l'île : une animation d'entrée n'est jouée qu'une fois par arrivée
 * d'un contenu (changement de clé du contenu principal), jamais quand la vue compacte est
 * simplement recomposée (retour depuis la carte étendue, par exemple).
 */
class EntryTracker {
    private var current: String? = null
    private var pending: String? = null

    /** À appeler à chaque composition avec la clé du contenu principal (null = aucun). */
    fun onPrimary(key: String?) {
        if (key != current) {
            current = key
            pending = key
        }
    }

    /** Vrai (une seule fois) si [key] vient d'arriver et doit jouer son entrée. */
    fun consume(key: String): Boolean = (pending == key).also { if (it) pending = null }
}

/** Courbes et calques des animations d'entrée (partagés par l'île et les aperçus). */
object EntryMotion {
    fun spec(animation: EntryAnimation, speed: Float): AnimationSpec<Float> {
        val s = speed.coerceIn(0.5f, 2f)
        fun ms(base: Int) = (base / s).toInt()
        return when (animation) {
            // Gonflement : imitation de la transition historique de l'île (aperçus uniquement).
            EntryAnimation.INFLATE -> tween(ms(280), easing = FastOutSlowInEasing)
            EntryAnimation.FADE -> tween(ms(260), easing = FastOutSlowInEasing)
            EntryAnimation.SLIDE -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow * s)
            EntryAnimation.ELASTIC -> spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMediumLow * s)
            EntryAnimation.DROP -> spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessLow * 1.6f * s)
            EntryAnimation.RIPPLE -> tween(ms(620), easing = FastOutSlowInEasing)
        }
    }

    /** Applique l'état de l'animation à la progression [p] (peut dépasser 1 avec un ressort). */
    fun applyLayer(layer: GraphicsLayerScope, animation: EntryAnimation, p: Float) = with(layer) {
        val q = p.coerceIn(0f, 1f)
        when (animation) {
            EntryAnimation.INFLATE -> {
                val sc = 0.85f + 0.15f * q
                scaleX = sc; scaleY = sc
                alpha = q
            }
            EntryAnimation.FADE -> alpha = q
            EntryAnimation.SLIDE -> {
                // Descend de la caméra (haut de l'île).
                alpha = (q * 1.8f).coerceAtMost(1f)
                translationY = -size.height * 0.9f * (1f - p)
            }
            EntryAnimation.ELASTIC -> {
                val sc = 0.35f + 0.65f * p
                scaleX = sc; scaleY = sc
                alpha = (q * 3f).coerceAtMost(1f)
            }
            EntryAnimation.DROP -> {
                // Tombe en s'étirant, puis s'écrase légèrement au rebond (p > 1).
                transformOrigin = TransformOrigin(0.5f, 0f)
                translationY = -size.height * 1.1f * (1f - p)
                val stretch = (1f - p) * 0.35f
                scaleY = 1f + stretch
                scaleX = 1f - stretch * 0.5f
                alpha = (q * 2.5f).coerceAtMost(1f)
            }
            EntryAnimation.RIPPLE -> {
                val sc = 1.25f - 0.25f * q
                scaleX = sc; scaleY = sc
                alpha = (q * 2f).coerceAtMost(1f)
            }
        }
    }
}
