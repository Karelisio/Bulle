package com.karelisio.bulle.core.a11y

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * État des réglages d'accessibilité du système, lus sans permission particulière :
 * « Texte à contraste élevé », exploration tactile (TalkBack) et « Supprimer les animations ».
 */
object SystemAccessibility {

    /** Clé Settings.Secure (masquée dans le SDK) du réglage « Texte à contraste élevé ». */
    private const val HIGH_TEXT_CONTRAST = "high_text_contrast_enabled"

    fun highContrastText(context: Context): Boolean =
        runCatching { Settings.Secure.getInt(context.contentResolver, HIGH_TEXT_CONTRAST, 0) == 1 }.getOrDefault(false)

    fun reduceMotion(context: Context): Boolean =
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)

    /** Suit « Texte à contraste élevé ». */
    fun highContrastTextFlow(context: Context): Flow<Boolean> =
        observe(context, Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST)) { highContrastText(it) }

    /** Suit « Supprimer les animations » (échelle de durée des animations à 0). */
    fun reduceMotionFlow(context: Context): Flow<Boolean> =
        observe(context, Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)) { reduceMotion(it) }

    private fun observe(context: Context, uri: Uri, read: (Context) -> Boolean): Flow<Boolean> {
        val app = context.applicationContext
        return callbackFlow {
            trySend(read(app))
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(read(app))
                }
            }
            runCatching { app.contentResolver.registerContentObserver(uri, false, observer) }
            awaitClose { runCatching { app.contentResolver.unregisterContentObserver(observer) } }
        }.distinctUntilChanged()
    }

    private val touchExploration = MutableStateFlow(false)
    @Volatile
    private var touchListening = false

    /**
     * Exploration tactile active (TalkBack ou équivalent). L'écouteur est enregistré une fois,
     * sur le contexte d'application, pour toute la durée du processus.
     */
    fun touchExploration(context: Context): StateFlow<Boolean> {
        if (!touchListening) {
            synchronized(this) {
                if (!touchListening) {
                    val am = context.applicationContext.getSystemService(AccessibilityManager::class.java)
                    if (am != null) {
                        touchExploration.value = am.isTouchExplorationEnabled
                        am.addTouchExplorationStateChangeListener { touchExploration.value = it }
                    }
                    touchListening = true
                }
            }
        }
        return touchExploration
    }

    fun isTouchExplorationEnabled(context: Context): Boolean = touchExploration(context).value
}

/** « Texte à contraste élevé » du système, suivi en direct. */
@Composable
fun rememberSystemHighContrast(): State<Boolean> {
    val context = LocalContext.current
    val flow = remember(context) { SystemAccessibility.highContrastTextFlow(context) }
    return flow.collectAsState(initial = remember(context) { SystemAccessibility.highContrastText(context) })
}
