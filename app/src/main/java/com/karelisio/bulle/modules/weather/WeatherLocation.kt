package com.karelisio.bulle.modules.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Position approximative de l'appareil (réseau), jamais la précise. */
object WeatherLocation {
    private const val MAX_AGE_MS = 60 * 60_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Dernière position connue de moins d'une heure, sinon une position réseau fraîche (20 s max). */
    @SuppressLint("MissingPermission") // Vérifiée juste au-dessus par hasPermission().
    suspend fun current(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.FUSED_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && now - last.time < MAX_AGE_MS) return last.latitude to last.longitude
        val fresh = withTimeoutOrNull(20_000L) { currentNetwork(lm) }
        return (fresh ?: last)?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentNetwork(lm: LocationManager): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        val ok = runCatching {
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, Runnable::run) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }.isSuccess
        if (!ok && cont.isActive) cont.resume(null)
    }
}
