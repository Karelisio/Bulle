package com.karelisio.bulle.modules.capture.qr

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * API publique du scanner QR.
 *
 * Usage depuis un autre module (ex. import d'un style partagé) :
 * ```
 * scope.launch { QrScan.results.filter { it.first == "style-import" }.collect { (_, text) -> … } }
 * QrScan.launch(context, "style-import")
 * ```
 * Avec un `requestTag`, le texte brut est renvoyé au demandeur (et n'est pas affiché dans l'île).
 * Sans tag, le résultat est interprété et affiché dans l'île.
 */
object QrScan {
    internal const val EXTRA_TAG = "com.karelisio.bulle.capture.QR_TAG"

    private val _results = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)

    /** Résultats des scans demandés avec un tag : (tag, texte brut). */
    val results: SharedFlow<Pair<String, String>> = _results.asSharedFlow()

    /** Ouvre le scanner plein écran. */
    fun launch(context: Context, requestTag: String?) {
        val intent = Intent(context, QrScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        requestTag?.let { intent.putExtra(EXTRA_TAG, it) }
        context.startActivity(intent)
    }

    internal fun deliver(tag: String, raw: String) {
        _results.tryEmit(tag to raw)
    }
}
