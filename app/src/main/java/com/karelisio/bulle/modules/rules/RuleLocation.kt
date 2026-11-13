package com.karelisio.bulle.modules.rules

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Position actuelle pour créer une règle de lieu (une seule mesure, pas de suivi). */
object RuleLocation {
    @SuppressLint("MissingPermission") // Vérifiée par RulePermissions.location.
    suspend fun current(context: Context): GeoFix? {
        if (!RulePermissions.location(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.hasProvider(it) && lm.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = provider?.let { p -> withTimeoutOrNull(15_000L) { currentFrom(lm, p) } }
        val loc = fresh ?: listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
        return loc?.let { GeoFix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else 0f) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFrom(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        val ok = runCatching {
            lm.getCurrentLocation(provider, signal, Runnable::run) { loc -> if (cont.isActive) cont.resume(loc) }
        }.isSuccess
        if (!ok && cont.isActive) cont.resume(null)
    }
}
