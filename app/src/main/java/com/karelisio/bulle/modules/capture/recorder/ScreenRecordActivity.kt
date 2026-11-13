package com.karelisio.bulle.modules.capture.recorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.karelisio.bulle.modules.capture.CapturePrefs
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.modules.capture.RecorderEvent
import com.karelisio.bulle.modules.capture.memo.MemoService

/**
 * Activité transparente de consentement : affiche la boîte système MediaProjection (à chaque
 * enregistrement : Android 14+ n'autorise qu'une capture par jeton), puis confie le jeton au
 * [ScreenRecordService] depuis le premier plan.
 */
class ScreenRecordActivity : ComponentActivity() {

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { askConsent() }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecordService.start(this, result.resultCode, data)
        } else {
            CaptureState.screen(RecorderEvent.Denied)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        CaptureState.screen(RecorderEvent.Request)
        // Audio micro demandé dans les réglages mais pas encore autorisé : on le demande d'abord.
        if (CapturePrefs.current(this).screenMicAudio && !MemoService.hasPermission(this)) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            askConsent()
        }
    }

    private fun askConsent() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        if (mpm == null) {
            CaptureState.screen(RecorderEvent.Denied)
            finish()
            return
        }
        runCatching { consent.launch(mpm.createScreenCaptureIntent()) }.onFailure {
            CaptureState.screen(RecorderEvent.Denied)
            finish()
        }
    }

    override fun onDestroy() {
        // Fermée sans réponse (retour arrière, rotation…) : on ne reste pas bloqué en « demande ».
        if (isFinishing && !isChangingConfigurations) {
            ContextCompat.getMainExecutor(this).execute { CaptureState.screen(RecorderEvent.Denied) }
        }
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, ScreenRecordActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
                )
            }
        }
    }
}
