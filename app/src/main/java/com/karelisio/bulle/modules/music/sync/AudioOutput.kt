package com.karelisio.bulle.modules.music.sync

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Famille de sortie audio, pour le libellé, l'icône et la priorité. */
enum class OutputKind { BLUETOOTH, HEARING_AID, WIRED, USB, HDMI, SPEAKER, OTHER }

/**
 * Sortie audio identifiée : [key] sert de clé au décalage mémorisé, [name] est le nom du produit
 * (vide pour le haut-parleur du téléphone, libellé traduit à l'affichage).
 */
data class AudioOutput(val key: String, val kind: OutputKind, val name: String) {
    companion object {
        /** Haut-parleur du téléphone : sortie par défaut, décalage nul tant qu'on ne le règle pas. */
        const val SPEAKER_KEY = "speaker"
        val Speaker = AudioOutput(SPEAKER_KEY, OutputKind.SPEAKER, "")

        /** Famille d'une clé (le préfixe avant « : »), pour l'affichage des sorties mémorisées. */
        fun kindOfKey(key: String): OutputKind = when (key.substringBefore(':')) {
            "bt", "ble", "sco" -> OutputKind.BLUETOOTH
            "hearing" -> OutputKind.HEARING_AID
            "wired" -> OutputKind.WIRED
            "usb" -> OutputKind.USB
            "hdmi" -> OutputKind.HDMI
            SPEAKER_KEY -> OutputKind.SPEAKER
            else -> OutputKind.OTHER
        }
    }
}

/** Sortie candidate, extraite d'un [AudioDeviceInfo] (testable sans Android). */
data class OutputCandidate(val type: Int, val productName: String?, val address: String?)

/** Préfixe de clé et priorité (plus grand = préféré) d'un type de sortie ; null = jamais pour la musique. */
@SuppressLint("InlinedApi")
private fun rank(type: Int): Pair<String, Int>? = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt" to 100
    // Constantes entières recopiées à la compilation : sans risque sous l'API 33 (BLE_BROADCAST).
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> "ble" to 100
    AudioDeviceInfo.TYPE_HEARING_AID -> "hearing" to 90
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG -> "wired" to 80
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb" to 70
    // Le SCO (mains libres) ne sert qu'aux appels : en dessous des sorties « média ».
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "sco" to 60
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi" to 50
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutput.SPEAKER_KEY to 0
    else -> null
}

/** Famille d'un type de sortie Android. */
fun kindOf(type: Int): OutputKind = rank(type)?.first?.let { AudioOutput.kindOfKey(it) } ?: OutputKind.OTHER

/** Nom ou adresse utilisable (Android renvoie parfois le modèle du téléphone ou une adresse vide). */
private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "00:00:00:00:00:00" }

/**
 * Clé stable d'une sortie : famille + nom du produit (ou adresse à défaut).
 * Le haut-parleur intégré a une clé fixe (son « nom de produit » est le modèle du téléphone).
 */
fun outputKey(c: OutputCandidate): String {
    val prefix = rank(c.type)?.first ?: "other${c.type}"
    if (prefix == AudioOutput.SPEAKER_KEY) return prefix
    val id = c.productName.clean()?.lowercase() ?: c.address.clean()?.lowercase()
    return if (id == null) prefix else "$prefix:$id"
}

/** Sortie à laquelle la musique est vraisemblablement envoyée : Bluetooth > filaire > USB > haut-parleur. */
fun pickOutput(candidates: List<OutputCandidate>): AudioOutput {
    val best = candidates
        .mapNotNull { c -> rank(c.type)?.let { c to it.second } }
        .maxByOrNull { it.second }
        ?.first
        ?: return AudioOutput.Speaker
    val key = outputKey(best)
    if (key == AudioOutput.SPEAKER_KEY) return AudioOutput.Speaker
    return AudioOutput(key, kindOf(best.type), best.productName.clean() ?: best.address.clean().orEmpty())
}

/** Suivi en direct de la sortie audio courante (branchement/débranchement d'un casque…). */
object AudioOutputTracker {
    private fun candidates(am: AudioManager): List<OutputCandidate> =
        runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList()).map { d ->
            // L'adresse d'un appareil Bluetooth peut être masquée sans autorisation : le nom suffit.
            OutputCandidate(d.type, d.productName?.toString(), runCatching { d.address }.getOrNull())
        }

    /** Sortie courante, lue immédiatement. */
    fun current(context: Context): AudioOutput {
        val am = context.getSystemService(AudioManager::class.java) ?: return AudioOutput.Speaker
        return pickOutput(candidates(am))
    }

    /** Sortie courante, puis à chaque changement de périphérique (AudioDeviceCallback). */
    fun observe(context: Context): Flow<AudioOutput> = callbackFlow {
        val app = context.applicationContext
        val am = app.getSystemService(AudioManager::class.java)
        if (am == null) {
            trySend(AudioOutput.Speaker)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(pickOutput(candidates(am)))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(pickOutput(candidates(am)))
            }
        }
        trySend(pickOutput(candidates(am)))
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { am.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()
}
