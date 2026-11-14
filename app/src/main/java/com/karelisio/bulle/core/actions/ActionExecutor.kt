package com.karelisio.bulle.core.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.karelisio.bulle.system.Reliability

/**
 * Exécute une [GestureAction]. Les actions « île » (déplier, échanger, recherche, Deck)
 * sont déléguées au [IslandActions] fourni par l'île.
 */
class ActionExecutor(private val context: Context, private val island: IslandActions) {

    companion object {
        private const val TAG = "ActionExecutor"

        /** Actions qui restent dans l'île (panneaux, réglages instantanés, média) : le Deck reste ouvert. */
        private val STAYS = setOf(
            ActionType.NONE, ActionType.TOGGLE_EXPAND, ActionType.SWAP_BUBBLES, ActionType.APP_SEARCH,
            ActionType.OPEN_DECK, ActionType.TIMER, ActionType.TRANSLATE, ActionType.EXIT_CRAVING,
            ActionType.PLAY_PAUSE, ActionType.NEXT_TRACK, ActionType.PREVIOUS_TRACK,
            ActionType.VOLUME_UP, ActionType.VOLUME_DOWN, ActionType.MUTE,
            ActionType.BRIGHTNESS_UP, ActionType.BRIGHTNESS_DOWN, ActionType.TORCH, ActionType.ROTATION,
            ActionType.BATTERY_SAVER, ActionType.FOCUS_MODE,
        )

        /** L'action ouvre un autre écran (ou montre un résultat dans l'île) : l'île doit se refermer avant. */
        fun leavesIsland(type: ActionType): Boolean = type !in STAYS
    }

    /** Ce que seule l'île sait faire. */
    interface IslandActions {
        fun toggleExpand()
        fun swapBubbles()
        fun openAppSearch()
        fun openDeck()
        fun openTimer()
        fun openCraving()
        fun openTranslate()
    }

    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val a11y get() = context as? AccessibilityService

    fun run(action: GestureAction) {
        try {
            when (action.type) {
                ActionType.NONE -> Unit
                ActionType.TOGGLE_EXPAND -> island.toggleExpand()
                ActionType.SWAP_BUBBLES -> island.swapBubbles()
                ActionType.APP_SEARCH -> island.openAppSearch()
                ActionType.OPEN_DECK -> island.openDeck()
                ActionType.TIMER -> island.openTimer()
                ActionType.ECHO_THOUGHT -> com.karelisio.bulle.modules.ecosystem.Ecosystem.sendThought(context)
                ActionType.EXIT_CRAVING -> island.openCraving()
                ActionType.RESERVE_FUEL -> com.karelisio.bulle.modules.ecosystem.Ecosystem.requestFuel(context)
                ActionType.SCAN_QR -> com.karelisio.bulle.modules.capture.CaptureActions.scanQr(context)
                ActionType.MEMO -> com.karelisio.bulle.modules.capture.CaptureActions.toggleMemo(context)
                ActionType.TRANSLATE -> island.openTranslate()
                ActionType.FOCUS_MODE -> com.karelisio.bulle.modules.focusmode.FocusMode.toggle()
                ActionType.SCREEN_RECORD -> com.karelisio.bulle.modules.capture.CaptureActions.toggleScreenRecord(context)

                ActionType.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
                ActionType.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
                ActionType.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
                ActionType.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                ActionType.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                ActionType.LOCK_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                ActionType.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)

                ActionType.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ActionType.NEXT_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                ActionType.PREVIOUS_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                ActionType.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
                ActionType.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
                ActionType.MUTE -> volume(AudioManager.ADJUST_TOGGLE_MUTE)

                ActionType.BRIGHTNESS_UP -> brightness(+1)
                ActionType.BRIGHTNESS_DOWN -> brightness(-1)
                ActionType.ROTATION -> toggleRotation()
                ActionType.TORCH -> Torch.toggle(context)
                ActionType.WIFI -> openPanel(Settings.Panel.ACTION_WIFI)
                ActionType.BLUETOOTH -> openPanel(Settings.ACTION_BLUETOOTH_SETTINGS)
                ActionType.BATTERY_SAVER -> toggleBatterySaver()

                ActionType.OPEN_APP -> action.packageName?.let { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg)?.let { start(it) }
                }
                ActionType.LAUNCH_SHORTCUT -> action.intentUri?.let { start(Intent.parseUri(it, Intent.URI_INTENT_SCHEME)) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Action ${action.type} impossible", e)
        }
    }

    private fun global(id: Int) {
        a11y?.performGlobalAction(id)
    }

    private fun mediaKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun volume(direction: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun brightness(step: Int) {
        if (!Settings.System.canWrite(context)) return openWriteSettings()
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        // Pas proportionnels à la valeur courante (±25 %, au moins 8 crans) : plus fins dans le sombre.
        val delta = (current * 0.25f).toInt().coerceAtLeast(8)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (current + step * delta).coerceIn(1, 255))
    }

    private fun toggleRotation() {
        if (!Settings.System.canWrite(context)) return openWriteSettings()
        val cr = context.contentResolver
        val auto = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (auto) 0 else 1)
    }

    private fun toggleBatterySaver() {
        // Bascule directe possible seulement avec WRITE_SECURE_SETTINGS (adb), sinon on ouvre la page.
        if (Reliability.canWriteSecureSettings(context)) {
            val cr = context.contentResolver
            val on = Settings.Global.getInt(cr, "low_power", 0) == 1
            if (Settings.Global.putInt(cr, "low_power", if (on) 0 else 1)) return
        }
        start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }

    private fun openPanel(action: String) = start(Intent(action))

    private fun openWriteSettings() = start(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}")),
    )

    private fun start(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

}

/** Lampe torche : bascule en suivant l'état réel (callback du système). */
object Torch {
    // État par caméra : le rappel arrive aussi pour les autres flashs (caméra avant…).
    private val states = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private var registered = false

    /** Vrai si le rappel vient d'être enregistré (état initial pas encore reçu). */
    @Synchronized
    private fun ensureCallback(cm: CameraManager): Boolean {
        if (registered) return false
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                states[cameraId] = enabled
            }
        }, Handler(Looper.getMainLooper()))
        registered = true
        return true
    }

    fun cameraWithFlash(cm: CameraManager): String? = cm.cameraIdList.firstOrNull { id ->
        val c = cm.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

    fun toggle(context: Context) {
        val cm = context.getSystemService(CameraManager::class.java)
        val fresh = ensureCallback(cm)
        val id = cameraWithFlash(cm) ?: return
        val apply = {
            runCatching { cm.setTorchMode(id, states[id] != true) }
                .onFailure { Log.w("Torch", "Lampe indisponible", it) }
        }
        // Au premier usage, l'état initial (lampe déjà allumée depuis le volet système…) est
        // livré de façon asynchrone sur le fil principal : on ne bascule qu'après l'avoir reçu.
        if (fresh) Handler(Looper.getMainLooper()).post { apply() } else apply()
    }
}
