package com.karelisio.bulle.ui.stylepacks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.karelisio.bulle.modules.capture.qr.QrScan

/** Écran des packs de styles branché sur le scanner QR (import d'un style partagé). */
@Composable
fun StylePacksRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        QrScan.results.collect { (tag, raw) -> if (tag == QR_TAG) pending = raw }
    }
    StylePacksScreen(
        onBack = onBack,
        onScanQr = { QrScan.launch(context, QR_TAG) },
        importRequest = pending,
        onImportRequestConsumed = { pending = null },
    )
}

private const val QR_TAG = "style"
