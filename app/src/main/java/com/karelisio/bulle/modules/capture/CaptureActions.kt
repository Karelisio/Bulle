package com.karelisio.bulle.modules.capture

import android.content.Context
import com.karelisio.bulle.modules.capture.memo.MemoService
import com.karelisio.bulle.modules.capture.qr.QrScan
import com.karelisio.bulle.modules.capture.recorder.ScreenRecordActivity
import com.karelisio.bulle.modules.capture.recorder.ScreenRecordService

/**
 * Points d'entrée des actions de geste de la capture. Le [Context] peut être le service
 * d'accessibilité de l'île (cas normal) ou n'importe quel contexte de l'appli.
 */
object CaptureActions {

    /** Ouvre le scanner QR plein écran ; le résultat s'affiche dans l'île. */
    fun scanQr(context: Context) = QrScan.launch(context, requestTag = null)

    /** Démarre un mémo vocal, ou l'arrête s'il est en cours. */
    fun toggleMemo(context: Context) {
        if (CaptureState.memo.value.active) MemoService.stop(context) else MemoService.start(context)
    }

    /** Démarre un enregistrement d'écran (consentement à chaque fois), ou l'arrête s'il est en cours. */
    fun toggleScreenRecord(context: Context) {
        when (RecorderMachine.toggle(CaptureState.screen.value)) {
            RecorderToggle.REQUEST_CONSENT -> ScreenRecordActivity.launch(context)
            RecorderToggle.STOP -> ScreenRecordService.stop(context)
            RecorderToggle.NOTHING -> Unit
        }
    }
}
