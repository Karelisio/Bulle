package com.karelisio.bulle.core.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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

    /** Ce que seule l'île sait faire. */
    interface IslandActions {
        fun toggleExpand()
        fun swapBubbles()
        fun openAppSearch()
        fun openDeck()
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
        // Pas perceptuellement réguliers : ±12 % de la plage, au moins 8 crans.
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

    private companion object {
        const val TAG = "ActionExecutor"
    }
}

/** Lampe torche : bascule en suivant l'état réel (callback du système). */
object Torch {
    @Volatile
    private var on = false
    private var registered = false

    @Synchronized
    private fun ensureCallback(cm: CameraManager) {
        if (registered) return
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                on = enabled
            }
        }, null)
        registered = true
    }

    fun cameraWithFlash(cm: CameraManager): String? = cm.cameraIdList.firstOrNull { id ->
        val c = cm.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

    fun toggle(context: Context) {
        val cm = context.getSystemService(CameraManager::class.java)
        ensureCallback(cm)
        val id = cameraWithFlash(cm) ?: return
        cm.setTorchMode(id, !on)
    }
}
