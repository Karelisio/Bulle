package com.karelisio.bulle.modules.alerts.sources

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.PrivacyDot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/* Indicateurs de confidentialité : pastille verte (caméra) / orange (micro), persistante tant que
 * l'accès dure. Android ne donne le nom de l'appli qu'aux applis système (onCameraOpened exige
 * CAMERA_OPEN_CLOSE_LISTENER, getClientPackageName est masqué) : libellé générique.
 * L'enregistrement d'écran n'est pas couvert : Android 15 ne signale l'enregistrement qu'à
 * l'appli enregistrée elle-même (WindowManager.addScreenRecordingCallback), pas aux autres. */

enum class PrivacyKind(val alertId: String) { CAMERA("cameraInUse"), MIC("micInUse") }

/** Délai avant d'afficher la pastille : ignore les ouvertures éclair (déverrouillage facial…). */
private const val PRIVACY_CONFIRM_MS = 700L

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<Boolean>.asPrivacy(kind: PrivacyKind): Flow<List<IslandContent>> = distinctUntilChanged()
    .transformLatest { used ->
        if (used) delay(PRIVACY_CONFIRM_MS)
        emit(used)
    }
    .distinctUntilChanged()
    .map { used -> if (used) listOf(PrivacyContent(kind)) else emptyList() }

/** Caméra utilisée : une caméra devient indisponible = ouverte par une autre appli. */
object CameraUseSource : AlertSource {
    override val id = "cameraInUse"

    override fun isEnabled(settings: AlertSettings) = settings.cameraInUse

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow {
        val ctx = env.context.applicationContext
        val cm = ctx.getSystemService(CameraManager::class.java)
        val busy = HashSet<String>()
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                busy.remove(cameraId)
                trySend(busy.isNotEmpty())
            }

            override fun onCameraUnavailable(cameraId: String) {
                busy.add(cameraId)
                trySend(true)
            }
        }
        cm.registerAvailabilityCallback(ContextCompat.getMainExecutor(ctx), callback)
        awaitClose { runCatching { cm.unregisterAvailabilityCallback(callback) } }
    }.asPrivacy(PrivacyKind.CAMERA)
}

/** Micro utilisé : enregistrements actifs (AudioRecordingCallback), hors mot-clé et clients rendus muets. */
object MicUseSource : AlertSource {
    override val id = "micInUse"

    /** MediaRecorder.AudioSource.HOTWORD (masqué) : écoute permanente du mot-clé de l'assistant. */
    private const val SOURCE_HOTWORD = 1999

    override fun isEnabled(settings: AlertSettings) = settings.micInUse

    private fun active(configs: List<AudioRecordingConfiguration>): Boolean =
        configs.any { !it.isClientSilenced && it.clientAudioSource != SOURCE_HOTWORD }

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow {
        val am = env.context.applicationContext.getSystemService(AudioManager::class.java)
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                trySend(active(configs))
            }
        }
        trySend(active(am.activeRecordingConfigurations))
        am.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { runCatching { am.unregisterAudioRecordingCallback(callback) } }
    }.asPrivacy(PrivacyKind.MIC)
}

/** Caméra ou micro en cours d'utilisation : pastille qui respire, sans limite de durée. */
data class PrivacyContent(val kind: PrivacyKind) : AlertContent() {
    override val key = "alerts:privacy:${kind.name.lowercase()}"
    override val alertId = kind.alertId
    override val priority = Priority.LIVE
    override val baseTimeoutMs: Long? = null
    override val canBeSecondary = true
    override val compactSlotWidth = 72.dp
    override val accent: Color = if (kind == PrivacyKind.CAMERA) CameraGreen else MicOrange

    @Composable
    private fun short() = stringResource(if (kind == PrivacyKind.CAMERA) R.string.alerts_camera_short else R.string.alerts_mic_short)

    @Composable
    override fun Leading() = PrivacyDot(accent)

    @Composable
    override fun Minimal() = PrivacyDot(accent, size = 12.dp)

    @Composable
    override fun Trailing() = AlertLabel(short(), color = accent)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(
            accent = accent,
            title = stringResource(if (kind == PrivacyKind.CAMERA) R.string.alerts_camera_in_use else R.string.alerts_mic_in_use),
            subtitle = stringResource(R.string.alerts_privacy_generic),
            glyph = { PrivacyDot(accent, size = 22.dp) },
            actions = {
                FilledTonalButton(onClick = {
                    runCatching {
                        host.context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    host.collapse()
                }) { Text(stringResource(R.string.alerts_privacy_settings)) }
            },
        )
    }

    private companion object {
        val CameraGreen = Color(0xFF3DDC84)
        val MicOrange = Color(0xFFFF9F43)
    }
}
