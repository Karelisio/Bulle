package com.karelisio.bulle.modules.network

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Type de connexion active (pour l'icône et l'attribution de la consommation). */
@Serializable
enum class NetKind { WIFI, MOBILE, ETHERNET, NONE }

/** Connexion par défaut : type physique et VPN éventuel. */
data class ConnectionInfo(val kind: NetKind = NetKind.NONE, val vpn: Boolean = false) {
    companion object {
        /** À partir des transports de NetworkCapabilities (un VPN expose souvent aussi le transport sous-jacent). */
        fun fromTransports(wifi: Boolean, cellular: Boolean, ethernet: Boolean, vpn: Boolean): ConnectionInfo {
            val kind = when {
                wifi -> NetKind.WIFI
                ethernet -> NetKind.ETHERNET
                cellular -> NetKind.MOBILE
                else -> NetKind.NONE
            }
            return ConnectionInfo(kind, vpn)
        }
    }
}

/**
 * Relevé des compteurs système (TrafficStats) à un instant donné.
 * @param totalBytes octets reçus + envoyés, toutes interfaces, depuis le démarrage (-1 si indisponible)
 * @param mobileBytes idem pour les interfaces mobiles (-1 si indisponible)
 * @param bootCount Settings.Global.BOOT_COUNT (-1 si inconnu) : détecte un redémarrage
 */
data class TrafficReading(
    val totalBytes: Long,
    val mobileBytes: Long,
    val bootCount: Int,
    val atMillis: Long,
    val kind: NetKind,
)

/**
 * Consommation du jour, cumulée sans permission spéciale à partir des deltas des compteurs
 * TrafficStats. Persistée en JSON (DataStore du module).
 */
@Serializable
data class UsageState(
    /** Jour civil (epochDay) du cumul ; -1 = jamais initialisé. */
    val day: Long = -1,
    val wifiBytes: Long = 0,
    val mobileBytes: Long = 0,
    /** Le cumul a démarré en cours de journée (activation, remise à zéro, trou de suivi). */
    val partial: Boolean = true,
    /** Instant (ms) où le cumul du jour a démarré ; utile si [partial]. */
    val since: Long = 0,
    // Dernier relevé (référence des deltas).
    val lastTotal: Long = -1,
    val lastMobile: Long = -1,
    val lastBoot: Int = -1,
    val lastAt: Long = 0,
    /** Connexion active depuis le dernier relevé : reçoit le delta quand les compteurs mobiles manquent. */
    val lastKind: NetKind = NetKind.NONE,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

/** Règles pures du cumul journalier. */
object DailyUsage {

    fun epochDay(atMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()

    private fun startOfDay(epochDay: Long, zone: ZoneId): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Intègre un nouveau relevé :
     *  - delta négatif (compteurs repartis de zéro après redémarrage) ou numéro de démarrage
     *    différent → delta ignoré, nouvelle référence ;
     *  - octets mobiles = delta des compteurs mobiles, le reste est compté en Wi-Fi (ou autre) ;
     *    sans compteur mobile, tout le delta va au type de connexion de l'intervalle ;
     *  - minuit franchi → remise à zéro ; la part du delta postérieure à minuit (au prorata du
     *    temps) est gardée pour la nouvelle journée.
     */
    fun accumulate(state: UsageState, r: TrafficReading, zone: ZoneId): UsageState {
        val today = epochDay(r.atMillis, zone)
        if (state.day < 0) return fresh(today, r)

        val sameBoot = state.lastBoot < 0 || r.bootCount < 0 || state.lastBoot == r.bootCount
        val baseline = state.lastTotal >= 0 && r.totalBytes >= 0 && sameBoot
        val hasMobile = state.lastMobile >= 0 && r.mobileBytes >= 0
        // Un compteur qui recule = compteurs repartis de zéro : delta ignoré.
        val reset = r.totalBytes < state.lastTotal || (hasMobile && r.mobileBytes < state.lastMobile)
        val dTotal = if (baseline && !reset) r.totalBytes - state.lastTotal else 0L
        var dMobile: Long
        var dWifi: Long
        if (baseline && !reset && hasMobile) {
            dMobile = (r.mobileBytes - state.lastMobile).coerceAtMost(dTotal)
            dWifi = dTotal - dMobile
        } else if (state.lastKind == NetKind.MOBILE) {
            dMobile = dTotal
            dWifi = 0L
        } else {
            dMobile = 0L
            dWifi = dTotal
        }

        var base = state
        if (today != state.day) {
            val midnight = startOfDay(today, zone)
            val span = r.atMillis - state.lastAt
            val fraction = if (state.lastAt <= 0 || span <= 0 || state.lastAt >= midnight) 1.0
            else ((r.atMillis - midnight).toDouble() / span).coerceIn(0.0, 1.0)
            dMobile = (dMobile * fraction).toLong()
            dWifi = (dWifi * fraction).toLong()
            // Suivi continu depuis la veille (même démarrage) : journée complète.
            val continuous = state.day == today - 1 && baseline && !reset
            base = state.copy(
                day = today, wifiBytes = 0, mobileBytes = 0,
                partial = !continuous, since = if (continuous) midnight else r.atMillis,
            )
        }
        return base.copy(
            wifiBytes = base.wifiBytes + dWifi,
            mobileBytes = base.mobileBytes + dMobile,
            lastTotal = r.totalBytes,
            lastMobile = r.mobileBytes,
            lastBoot = r.bootCount,
            lastAt = r.atMillis,
            lastKind = r.kind,
        )
    }

    /** Remise à zéro (manuelle ou premier relevé) : le cumul repart de ce relevé. */
    fun fresh(today: Long, r: TrafficReading): UsageState = UsageState(
        day = today, partial = true, since = r.atMillis,
        lastTotal = r.totalBytes, lastMobile = r.mobileBytes, lastBoot = r.bootCount,
        lastAt = r.atMillis, lastKind = r.kind,
    )

    /** Cumul affichable aujourd'hui : zéro si le dernier cumul date d'un autre jour. */
    fun today(state: UsageState, nowMillis: Long, zone: ZoneId): UsageState =
        if (state.day == epochDay(nowMillis, zone)) state else state.copy(wifiBytes = 0, mobileBytes = 0, partial = true, since = nowMillis)
}
