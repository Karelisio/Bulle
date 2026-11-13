package com.karelisio.bulle.modules.capture

import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.capture.memo.MemoContent
import com.karelisio.bulle.modules.capture.qr.QrPayload
import com.karelisio.bulle.modules.capture.qr.QrResultContent
import com.karelisio.bulle.modules.capture.recorder.ScreenRecordContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** État partagé de la capture (en mémoire, propre au processus). */
object CaptureState {
    private val _memo = MutableStateFlow(MemoSession())
    val memo: StateFlow<MemoSession> = _memo.asStateFlow()

    /** Niveau sonore du mémo (0..1), mis à jour ~12 fois par seconde : lu au dessin uniquement. */
    private val _memoLevel = MutableStateFlow(0f)
    val memoLevel: StateFlow<Float> = _memoLevel.asStateFlow()

    private val _screen = MutableStateFlow(RecorderSession())
    val screen: StateFlow<RecorderSession> = _screen.asStateFlow()

    /** Dernier code QR scanné (affiché dans l'île), avec son instant de publication. */
    private val _qr = MutableStateFlow<Pair<QrPayload, Long>?>(null)
    val qr: StateFlow<Pair<QrPayload, Long>?> = _qr.asStateFlow()

    fun memo(event: MemoEvent): MemoSession {
        _memo.update { MemoMachine.reduce(it, event) }
        if (!_memo.value.active || _memo.value.phase == MemoPhase.PAUSED) _memoLevel.value = 0f
        return _memo.value
    }

    fun memoLevel(level: Float) {
        _memoLevel.value = level
    }

    fun screen(event: RecorderEvent): RecorderSession {
        _screen.update { RecorderMachine.reduce(it, event) }
        return _screen.value
    }

    fun showQr(payload: QrPayload) {
        _qr.value = payload to SystemClock.elapsedRealtime()
    }

    fun clearQr() {
        _qr.value = null
    }

    fun resetForTests() {
        _memo.value = MemoSession()
        _memoLevel.value = 0f
        _screen.value = RecorderSession()
        _qr.value = null
    }
}

/**
 * Module « Capture » : mémo vocal en cours, enregistrement d'écran en cours et résultat du
 * dernier code QR scanné. Invisible tant que rien n'est en cours.
 */
object CaptureModule : IslandModule {
    const val ID = "capture"
    override val id = ID
    override val titleRes = R.string.capture_module

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> =
        combine(CaptureState.memo, CaptureState.screen, CaptureState.qr) { memo, screen, qr ->
            listOfNotNull(
                memo.takeIf { it.active }?.let { MemoContent(it) },
                screen.takeIf { it.active }?.let { ScreenRecordContent(it) },
                qr?.let { (payload, at) -> QrResultContent(payload, postedAt = at) },
            )
        }
}
