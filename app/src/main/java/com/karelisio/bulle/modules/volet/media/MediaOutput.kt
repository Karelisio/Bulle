package com.karelisio.bulle.modules.volet.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.media.session.MediaController
import android.media.session.MediaController.PlaybackInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Famille d'appareil de sortie audio (choisit l'icône du bouton). */
enum class OutputKind { PHONE, HEADPHONES, BLUETOOTH, USB, TV, CAST }

/** Sortie audio courante : famille et nom lisible (null = nom générique selon la famille). */
data class AudioOutput(val kind: OutputKind, val name: String?)

/** Appareil de sortie vu par AudioManager, réduit à ce qui sert au choix (testable sans Android). */
data class OutputCandidate(val type: Int, val name: String?)

/** Détection de la sortie audio du lecteur et ouverture du sélecteur de sortie système. */
object MediaOutput {

    private const val TAG = "BulleVolet"

    /** Sélecteur de sortie de Paramètres (Android 11+ chez Google ; présent ou non selon le constructeur). */
    const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
    const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"

    /**
     * Sortie retenue parmi les appareils de sortie connectés, par ordre de priorité du routage
     * média d'Android : Bluetooth > casque filaire > USB > HDMI > haut-parleur du téléphone.
     */
    // Constantes copiées à la compilation : simplement jamais rencontrées sur une version plus ancienne.
    @SuppressLint("InlinedApi")
    fun pick(devices: List<OutputCandidate>): AudioOutput {
        fun first(vararg types: Int) = devices.firstOrNull { it.type in types }
        first(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )?.let { return AudioOutput(OutputKind.BLUETOOTH, it.name.clean()) }
        first(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
            ?.let { return AudioOutput(OutputKind.HEADPHONES, null) }
        first(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY)
            ?.let { return AudioOutput(OutputKind.USB, it.name.clean()) }
        first(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC)
            ?.let { return AudioOutput(OutputKind.TV, it.name.clean()) }
        return AudioOutput(OutputKind.PHONE, null)
    }

    /** Lecture à distance (Cast, enceinte connectée) : la sortie est l'appareil distant. */
    fun remoteOutput(controller: MediaController?): AudioOutput? {
        val info = runCatching { controller?.playbackInfo }.getOrNull() ?: return null
        if (info.playbackType != PlaybackInfo.PLAYBACK_TYPE_REMOTE) return null
        // Le nom de l'appareil distant n'est pas exposé publiquement : nom générique.
        return AudioOutput(OutputKind.CAST, null)
    }

    /** Sortie locale courante (appareils de sortie connectés). */
    fun current(context: Context): AudioOutput {
        val am = context.getSystemService(AudioManager::class.java) ?: return AudioOutput(OutputKind.PHONE, null)
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
        return pick(devices.filter { it.isSink }.map { OutputCandidate(it.type, it.productName?.toString()) })
    }

    /** Sortie locale, mise à jour à chaque branchement / débranchement d'appareil. */
    fun observe(context: Context): Flow<AudioOutput> = callbackFlow {
        val am = context.getSystemService(AudioManager::class.java)
        if (am == null) {
            trySend(AudioOutput(OutputKind.PHONE, null))
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(current(context))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(current(context))
            }
        }
        trySend(current(context))
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { am.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    /**
     * Intent du sélecteur de sortie système pour le lecteur [packageName], s'il existe sur l'appareil
     * (Android 14+ ; MagicOS ne l'expose pas toujours). Null sinon.
     */
    fun switcherIntent(context: Context, packageName: String): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val intent = Intent(ACTION_MEDIA_OUTPUT).putExtra(EXTRA_PACKAGE_NAME, packageName)
        return intent.takeIf { runCatching { it.resolveActivity(context.packageManager) }.getOrNull() != null }
    }

    /**
     * Ouvre le choix de la sortie audio : sélecteur de Paramètres, sinon sélecteur de MediaRouter2,
     * sinon réglages Bluetooth. [startActivity] lance une activité en fermant le volet.
     */
    fun openSwitcher(context: Context, packageName: String, close: () -> Unit, startActivity: (Intent) -> Unit) {
        switcherIntent(context, packageName)?.let { startActivity(it); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val shown = runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }
                .onFailure { Log.d(TAG, "Sélecteur de sortie indisponible", it) }
                .getOrDefault(false)
            if (shown) {
                close()
                return
            }
        }
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals(Build.MODEL, ignoreCase = true) }
}
