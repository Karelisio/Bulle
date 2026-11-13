package com.karelisio.bulle.core.style

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Matrice carrée d'un QR code : [get] vrai = module sombre. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

/** Génération de QR code (zxing core, encodeur seul : aucune caméra, aucun réseau). */
object QrCode {
    /** Matrice du QR de [text], sans marge (la marge blanche est dessinée par l'interface). Null si trop long. */
    fun encode(text: String): QrMatrix? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val n = m.width
        QrMatrix(n, BooleanArray(n * n) { m.get(it % n, it / n) })
    }.getOrNull()
}
