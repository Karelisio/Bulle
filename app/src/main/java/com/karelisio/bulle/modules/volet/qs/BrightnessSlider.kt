package com.karelisio.bulle.modules.volet.qs

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Plage réelle de Settings.System.SCREEN_BRIGHTNESS. AOSP utilise 1 – 255, mais certaines
 * surcouches (MagicOS…) déclarent une autre plage dans les ressources du framework.
 */
data class BrightnessRange(val min: Int, val max: Int) {
    companion object {
        /** Plage AOSP, utilisée en repli. */
        val DEFAULT = BrightnessRange(BrightnessCurve.MIN, BrightnessCurve.MAX)

        /**
         * Valide les valeurs lues dans les ressources (null = absente) : repli sur 1 – 255
         * si le maximum manque ou n'est pas supérieur au minimum.
         */
        fun sanitize(min: Int?, max: Int?): BrightnessRange {
            val hi = max?.takeIf { it > 1 } ?: return DEFAULT
            val lo = (min ?: DEFAULT.min).coerceAtLeast(0)
            return if (lo < hi) BrightnessRange(lo, hi) else BrightnessRange(DEFAULT.min.coerceAtMost(hi - 1), hi)
        }

        @Volatile private var cached: BrightnessRange? = null

        /** Lit config_screenBrightnessSettingMinimum / Maximum du framework (mis en cache). */
        fun system(): BrightnessRange = cached ?: run {
            val res = android.content.res.Resources.getSystem()
            fun read(name: String): Int? = runCatching {
                val id = res.getIdentifier(name, "integer", "android")
                if (id != 0) res.getInteger(id) else null
            }.getOrNull()
            sanitize(read("config_screenBrightnessSettingMinimum"), read("config_screenBrightnessSettingMaximum"))
                .also { cached = it }
        }
    }
}

/**
 * Courbe perceptuelle de la luminosité : l'œil perçoit la luminance de façon non linéaire,
 * la position du curseur suit donc valeur^(1/γ) (comme le curseur du système).
 */
object BrightnessCurve {
    const val GAMMA = 2.2f
    const val MIN = 1
    const val MAX = 255

    /** Nombre de crans (retour haptique à chaque cran franchi). */
    const val STEPS = 20

    fun toPosition(value: Int, range: BrightnessRange = BrightnessRange.DEFAULT): Float {
        val span = (range.max - range.min).coerceAtLeast(1)
        val linear = (value.coerceIn(range.min, range.max) - range.min).toFloat() / span
        return linear.pow(1f / GAMMA)
    }

    fun toValue(position: Float, range: BrightnessRange = BrightnessRange.DEFAULT): Int =
        (range.min + (range.max - range.min) * position.coerceIn(0f, 1f).pow(GAMMA)).roundToInt()
            .coerceIn(range.min, range.max)

    fun step(position: Float): Int = (position.coerceIn(0f, 1f) * STEPS).roundToInt()
}

private fun readBrightness(context: Context, range: BrightnessRange): Int =
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (range.min + range.max) / 2)

/** Curseur de luminosité épais (style Android 16) : piste large arrondie, soleil qui grandit. */
@Composable
internal fun BrightnessSlider(host: VoletHost, modifier: Modifier = Modifier) {
    val context = host.context
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val scheme = MaterialTheme.colorScheme
    val height = if (host.settings.density == VoletDensity.COMPACT) 44.dp else 52.dp

    val range = remember { BrightnessRange.system() }
    val system by remember(context) {
        settingsFlow(context, listOf(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS))) {
            readBrightness(context, range)
        }
    }.collectAsState(initial = remember(context) { readBrightness(context, range) })

    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val target = dragPosition ?: BrightnessCurve.toPosition(system, range)
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = when {
            reduce -> snap()
            dragPosition != null -> spring(stiffness = Spring.StiffnessHigh)
            else -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "brightness",
    )

    val label = stringResource(R.string.volet_qs_brightness)
    val writer = remember(context) { BrightnessWriter(context) }
    val onStep by rememberUpdatedState { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHighest)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f, BrightnessCurve.STEPS - 1)
                setProgress { p ->
                    if (writer.permitted()) {
                        writer.beginManual()
                        writer.write(BrightnessCurve.toValue(p, range))
                    } else {
                        host.startActivity(writer.grantIntent())
                    }
                    true
                }
            }
            .pointerInput(writer) {
                val thumb = size.height.toFloat()
                fun posAt(x: Float) = ((x - thumb / 2f) / (size.width - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Sans WRITE_SETTINGS : le toucher ouvre la page d'autorisation.
                    if (!writer.permitted()) {
                        down.consume()
                        host.startActivity(writer.grantIntent())
                        return@awaitEachGesture
                    }
                    var lastStep = BrightnessCurve.step(posAt(down.position.x))
                    fun update(x: Float) {
                        val p = posAt(x)
                        dragPosition = p
                        writer.write(BrightnessCurve.toValue(p, range))
                        val s = BrightnessCurve.step(p)
                        if (s != lastStep) {
                            lastStep = s
                            onStep()
                        }
                    }
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (drag != null) {
                        writer.beginManual()
                        update(drag.position.x)
                        horizontalDrag(drag.id) { change ->
                            change.consume()
                            update(change.position.x)
                        }
                    } else if (currentEvent.changes.all { it.changedToUp() && !it.isConsumed }) {
                        // Simple toucher : la luminosité saute à la position touchée.
                        writer.beginManual()
                        update(down.position.x)
                    }
                    dragPosition = null
                }
            }
            .drawBehind {
                val h = size.height
                val w = h + (size.width - h) * position
                drawRoundRect(scheme.primary, size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                // Poignée : petit trait vertical au bout de la partie remplie.
                val handleH = h * 0.42f
                drawRoundRect(
                    scheme.onPrimary.copy(alpha = 0.55f),
                    topLeft = Offset(w - h * 0.32f, (h - handleH) / 2f),
                    size = Size(3.dp.toPx(), handleH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            },
    ) {
        Box(Modifier.size(height), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.LightMode,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        // Le soleil grandit et tourne légèrement avec la luminosité.
                        val s = 0.72f + 0.5f * position
                        scaleX = s
                        scaleY = s
                        rotationZ = 90f * position
                    },
            )
        }
    }
}

/** Écriture de Settings.System.SCREEN_BRIGHTNESS (exige WRITE_SETTINGS). */
internal class BrightnessWriter(private val context: Context) {
    private var last = -1

    fun permitted(): Boolean = Settings.System.canWrite(context)

    fun grantIntent() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

    /** Passe en mode manuel (sinon la valeur écrite serait ignorée par la luminosité auto). */
    fun beginManual() {
        last = -1
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
        }
    }

    fun write(value: Int) {
        if (value == last) return
        last = value
        runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value) }
    }
}
