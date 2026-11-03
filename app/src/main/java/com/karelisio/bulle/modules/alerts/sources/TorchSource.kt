package com.karelisio.bulle.modules.alerts.sources

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.island.Priority
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.TorchGlyph
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val TorchYellow = Color(0xFFFFE07A)

/**
 * Lampe torche allumée : contenu persistant tant qu'elle reste allumée, avec variateur
 * d'intensité sur Android 13+ si le flash le permet. Aucune permission nécessaire.
 */
object TorchSource : AlertSource {
    override val id = "torch"

    override fun isEnabled(settings: AlertSettings) = settings.torch

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow<TorchContent?> {
        val ctx = env.context.applicationContext
        val cm = ctx.getSystemService(CameraManager::class.java)
        val on = LinkedHashMap<String, Int>() // caméra allumée → niveau courant
        fun publish() {
            val (id, level) = on.entries.firstOrNull()?.toPair() ?: run { trySend(null); return }
            trySend(TorchContent(id, level, maxLevel(cm, id)))
        }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (enabled) on[cameraId] = currentLevel(cm, cameraId) else on.remove(cameraId)
                publish()
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (on.remove(cameraId) != null) publish()
            }

            override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                if (cameraId in on) { on[cameraId] = newStrengthLevel; publish() }
            }
        }
        cm.registerTorchCallback(ContextCompat.getMainExecutor(ctx), callback)
        awaitClose { cm.unregisterTorchCallback(callback) }
    }
        .distinctUntilChanged()
        .map { listOfNotNull(it) }

    /** Niveau maximal d'intensité (1 = non réglable). */
    fun maxLevel(cm: CameraManager, cameraId: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return runCatching {
            cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
        }.getOrNull() ?: 1
    }

    private fun currentLevel(cm: CameraManager, cameraId: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return runCatching { cm.getTorchStrengthLevel(cameraId) }.getOrDefault(1)
    }

    fun setLevel(context: Context, cameraId: String, level: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val cm = context.getSystemService(CameraManager::class.java)
        runCatching { cm.turnOnTorchWithStrengthLevel(cameraId, level.coerceIn(1, maxLevel(cm, cameraId))) }
    }

    fun turnOff(context: Context, cameraId: String) {
        runCatching { context.getSystemService(CameraManager::class.java).setTorchMode(cameraId, false) }
    }
}

/** Lampe allumée : icône qui rayonne ; étendu = variateur + bouton éteindre. */
data class TorchContent(
    val cameraId: String,
    val level: Int,
    val maxLevel: Int,
) : AlertContent() {
    override val key = "alerts:torch"
    // État durable : passe sous les alertes ponctuelles, comme une activité en cours.
    override val priority = Priority.LIVE
    override val accent = TorchYellow
    override val timeoutMs: Long? = null
    override val canBeSecondary = true
    override val compactSlotWidth = 48.dp

    private val adjustable get() = maxLevel > 1
    private val fraction get() = if (adjustable) level / maxLevel.toFloat() else 1f

    @Composable
    override fun Leading() = TorchGlyph(TorchYellow, intensity = fraction)

    @Composable
    override fun Minimal() = TorchGlyph(TorchYellow, size = 18.dp, intensity = fraction)

    @Composable
    override fun Trailing() {
        if (adjustable) AlertLabel(stringResource(R.string.alerts_percent, (fraction * 100).roundToInt()), color = TorchYellow)
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(
            accent = TorchYellow,
            title = stringResource(R.string.alerts_torch),
            subtitle = stringResource(R.string.alerts_torch_on),
            glyph = { TorchGlyph(TorchYellow, size = 28.dp, intensity = fraction) },
            extra = if (!adjustable) null else {
                {
                    // État local pendant le glisser, resynchronisé quand le système confirme.
                    var value by remember { mutableFloatStateOf(level.toFloat()) }
                    LaunchedEffect(level) { value = level.toFloat() }
                    Slider(
                        value = value,
                        onValueChange = { v ->
                            val r = v.roundToInt().coerceIn(1, maxLevel)
                            if (r != value.roundToInt()) TorchSource.setLevel(host.context, cameraId, r)
                            value = v
                        },
                        valueRange = 1f..maxLevel.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = TorchYellow,
                            activeTrackColor = TorchYellow,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        ),
                    )
                }
            },
            actions = {
                Button(
                    onClick = { TorchSource.turnOff(host.context, cameraId) },
                    colors = ButtonDefaults.buttonColors(containerColor = TorchYellow, contentColor = Color.Black),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.alerts_torch_off))
                }
            },
        )
    }
}
