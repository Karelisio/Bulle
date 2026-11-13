package com.karelisio.bulle.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.karelisio.bulle.core.a11y.SystemAccessibility

/** Vrai si l'utilisateur a coupé les animations (réglage système « Supprimer les animations »). */
fun isReduceMotion(context: Context): Boolean = SystemAccessibility.reduceMotion(context)

/**
 * « Supprimer les animations », suivi en direct : l'île vit dans un service de longue durée,
 * un simple remember ne verrait jamais le changement de réglage.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val flow = remember(context) { SystemAccessibility.reduceMotionFlow(context) }
    val reduce by flow.collectAsState(initial = remember(context) { isReduceMotion(context) })
    return reduce
}
