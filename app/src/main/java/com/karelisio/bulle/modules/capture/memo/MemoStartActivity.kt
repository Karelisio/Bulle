package com.karelisio.bulle.modules.capture.memo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.karelisio.bulle.R

/**
 * Activité transparente, sans interface : demande RECORD_AUDIO si besoin, puis démarre
 * [MemoService] depuis le premier plan (aucune restriction de démarrage) et se ferme aussitôt.
 */
class MemoStartActivity : ComponentActivity() {

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startAndFinish() else {
            Toast.makeText(this, R.string.capture_memo_permission_denied, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (MemoService.hasPermission(this)) startAndFinish() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startAndFinish() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                MemoService.intent(this, MemoService.ACTION_START).putExtra(MemoService.EXTRA_FROM_ACTIVITY, true),
            )
        }
        finish()
    }

    companion object {
        private const val EXTRA_FALLBACK = "fallback"

        fun launch(context: Context, fallback: Boolean) {
            runCatching {
                context.startActivity(
                    Intent(context, MemoStartActivity::class.java)
                        .putExtra(EXTRA_FALLBACK, fallback)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
                )
            }
        }
    }
}
