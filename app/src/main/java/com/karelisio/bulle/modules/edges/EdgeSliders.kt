package com.karelisio.bulle.modules.edges

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.pow
import kotlin.math.roundToInt

/** Conversion valeur système ↔ position du curseur (logique pure, testée). */
object EdgeSliderMath {
    /** Gamma de perception de la luminosité : les crans sombres sont plus fins. */
    const val GAMMA = 2.2

    fun brightnessToFraction(value: Int, max: Int = 255): Float =
        (value.coerceIn(0, max) / max.toDouble()).pow(1 / GAMMA).toFloat()

    fun fractionToBrightness(f: Float, max: Int = 255): Int =
        (f.coerceIn(0f, 1f).toDouble().pow(GAMMA) * max).roundToInt().coerceIn(1, max)

    fun volumeToFraction(value: Int, max: Int): Float = if (max <= 0) 0f else value.coerceIn(0, max) / max.toFloat()

    fun fractionToVolume(f: Float, max: Int): Int = (f.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)

    /** Cran haptique : un cran tous les [step] (5 % par défaut) franchis entre [before] et [after]. */
    fun crossedNotch(before: Float, after: Float, step: Float = 0.05f): Boolean =
        (before / step).toInt() != (after / step).toInt() || (after >= 1f) != (before >= 1f) || (after <= 0f) != (before <= 0f)
}

/** Lecture / écriture de la luminosité et du volume média. */
object EdgeSliders {

    fun canWrite(context: Context, kind: EdgeSlider): Boolean =
        kind != EdgeSlider.BRIGHTNESS || Settings.System.canWrite(context)

    fun read(context: Context, kind: EdgeSlider): Float = runCatching {
        when (kind) {
            EdgeSlider.BRIGHTNESS -> EdgeSliderMath.brightnessToFraction(
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128),
            )
            EdgeSlider.VOLUME -> {
                val am = context.getSystemService(AudioManager::class.java)
                EdgeSliderMath.volumeToFraction(am.getStreamVolume(AudioManager.STREAM_MUSIC), am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            }
            EdgeSlider.OFF -> 0f
        }
    }.getOrDefault(0f)

    /** Applique [f] (0..1) ; retourne la valeur réellement appliquée (crans du volume). */
    fun write(context: Context, kind: EdgeSlider, f: Float): Float = runCatching {
        when (kind) {
            EdgeSlider.BRIGHTNESS -> {
                if (!Settings.System.canWrite(context)) return@runCatching f
                val cr = context.contentResolver
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, EdgeSliderMath.fractionToBrightness(f))
                f
            }
            EdgeSlider.VOLUME -> {
                val am = context.getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val v = EdgeSliderMath.fractionToVolume(f, max)
                // Sans l'interface du système : la jauge des Bords la remplace.
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                EdgeSliderMath.volumeToFraction(v, max)
            }
            EdgeSlider.OFF -> f
        }
    }.getOrDefault(f)
}
