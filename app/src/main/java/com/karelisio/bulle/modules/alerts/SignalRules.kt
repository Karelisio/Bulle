package com.karelisio.bulle.modules.alerts

/** État simplifié du réseau mobile. */
enum class CellState { IN_SERVICE, NO_SERVICE, OFF }

/** Événement réseau mobile annoncé dans l'île. */
enum class CellEvent { LOST, BACK }

/** Position de la sonnerie. */
enum class RingerKind { SILENT, VIBRATE, NORMAL }

/** Usage d'un câble USB de données. */
enum class UsbMode { FILES, PHOTOS, TETHERING, MIDI, CHARGE_ONLY }

/**
 * Règles pures (sans Android) des alertes réseau, son et USB, testées unitairement.
 * Les constantes Android sont recopiées en littéraux commentés pour rester testables en JVM.
 */
object SignalRules {

    /** Délai avant d'annoncer une perte de réseau (ignore les coupures d'une seconde en ascenseur). */
    const val CELL_LOSS_CONFIRM_MS = 4_000L

    /** ServiceState.getState() → état simplifié. */
    fun cellState(serviceState: Int): CellState = when (serviceState) {
        0 -> CellState.IN_SERVICE // STATE_IN_SERVICE
        3 -> CellState.OFF // STATE_POWER_OFF (mode avion, radio coupée)
        else -> CellState.NO_SERVICE // OUT_OF_SERVICE, EMERGENCY_ONLY
    }

    /**
     * Transition du réseau mobile entre deux états confirmés, ou null.
     * Pas d'événement pour l'état initial, ni pour l'allumage / l'extinction de la radio
     * (le mode avion a sa propre alerte).
     */
    fun cellTransition(previous: CellState?, current: CellState): CellEvent? = when {
        previous == null || previous == current -> null
        previous == CellState.IN_SERVICE && current == CellState.NO_SERVICE -> CellEvent.LOST
        previous == CellState.NO_SERVICE && current == CellState.IN_SERVICE -> CellEvent.BACK
        else -> null
    }

    /**
     * 5G affichée par la barre d'état : réseau NR autonome, ou LTE « surchargé » en NR
     * (TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA = 3, NR_NSA_MMWAVE = 4, NR_ADVANCED = 5).
     */
    fun is5g(networkType: Int, overrideType: Int): Boolean =
        networkType == NETWORK_TYPE_NR || overrideType in 3..5

    /** Vrai seulement au passage vers la 5G (jamais pour l'état initial). */
    fun became5g(previous: Boolean?, current: Boolean): Boolean = previous == false && current

    /**
     * Point d'accès : nouvel état stable et événement à annoncer.
     * WIFI_AP_STATE_DISABLED = 11, ENABLED = 13 ; les états transitoires (10, 12, 14) sont ignorés.
     * La diffusion collante rejouée à l'inscription ([initial]) fixe l'état sans alerte.
     * Sans diffusion initiale, le point d'accès n'a jamais été allumé depuis le démarrage : éteint.
     */
    fun hotspot(stable: Boolean?, apState: Int, initial: Boolean): Pair<Boolean?, Boolean?> {
        val on = when (apState) {
            13 -> true
            11 -> false
            else -> return stable to null
        }
        if (initial) return on to null
        val event = if (on != (stable ?: false)) on else null
        return on to event
    }

    /** AudioManager.RINGER_MODE_* → position de la sonnerie. */
    fun ringerKind(mode: Int): RingerKind = when (mode) {
        0 -> RingerKind.SILENT // RINGER_MODE_SILENT
        1 -> RingerKind.VIBRATE // RINGER_MODE_VIBRATE
        else -> RingerKind.NORMAL
    }

    /**
     * Usage d'un câble USB de données à partir des extras booléens d'ACTION_USB_STATE
     * (« mtp », « ptp », « rndis », « ncm », « midi »).
     */
    fun usbMode(flag: (String) -> Boolean): UsbMode = when {
        flag("mtp") -> UsbMode.FILES
        flag("ptp") -> UsbMode.PHOTOS
        flag("rndis") || flag("ncm") -> UsbMode.TETHERING
        flag("midi") -> UsbMode.MIDI
        else -> UsbMode.CHARGE_ONLY
    }

    /**
     * Câble de données branché (true) / débranché (false), ou null.
     * « connected » d'ACTION_USB_STATE n'est vrai qu'avec une liaison de données vers un hôte
     * (ordinateur) : un simple chargeur ne le déclenche pas, ce qui distingue la charge seule.
     */
    fun usbTransition(previous: Boolean?, connected: Boolean): Boolean? =
        if (previous == null || previous == connected) null else connected

    /** BatteryManager.BATTERY_PLUGGED_USB. */
    const val PLUGGED_USB = 2

    /**
     * Branchement à un ordinateur : l'alerte USB (activée) absorbe l'alerte de charge et montre
     * une seule alerte combinée. Sinon (chargeur secteur, alerte USB coupée, câble sans données),
     * l'alerte de charge reste seule.
     */
    fun mergeUsbCharge(usbAlertOn: Boolean, plugged: Int, dataCable: Boolean): Boolean =
        usbAlertOn && plugged == PLUGGED_USB && dataCable

    /** Pourcentage de charge à afficher dans l'alerte USB, ou null si le téléphone ne charge pas par USB. */
    fun usbChargePercent(plugged: Int, percent: Int?): Int? = percent.takeIf { plugged == PLUGGED_USB }

    private const val NETWORK_TYPE_NR = 20 // TelephonyManager.NETWORK_TYPE_NR
}

/**
 * Lien entre les sources USB et batterie : état du câble de données connu de [UsbSource],
 * lu par la batterie pour ne pas doubler l'alerte USB d'une alerte de charge.
 */
internal object UsbChargeLink {
    /** Dernier extra « connected » reçu de USB_STATE (faux si la source USB ne tourne pas). */
    @Volatile
    var dataConnected: Boolean = false

    /** Attente maximale de l'état USB après un branchement USB (ordre des diffusions non garanti). */
    const val WAIT_MS = 1_500L
}

/** Une rafale de changements de volume, affichée comme UNE alerte qui se met à jour. */
data class VolumeBurst(
    val level: Int,
    val max: Int,
    /** Instant du premier changement : identité (clé) de l'alerte. */
    val startedAt: Long,
    /** Instant du dernier changement : base de la durée d'affichage. */
    val lastAt: Long,
) {
    val percent: Int get() = if (max <= 0) 0 else (level * 100f / max).toInt().coerceIn(0, 100)

    companion object {
        /**
         * Ajoute un changement à la rafale en cours si l'alerte précédente est encore visible
         * (moins de [windowMs] depuis le dernier changement), sinon ouvre une nouvelle rafale.
         */
        fun next(previous: VolumeBurst?, level: Int, max: Int, now: Long, windowMs: Long): VolumeBurst =
            if (previous != null && now - previous.lastAt in 0..windowMs) {
                previous.copy(level = level, max = max, lastAt = now)
            } else {
                VolumeBurst(level, max, startedAt = now, lastAt = now)
            }
    }
}

/**
 * Suivi des réseaux VPN (ConnectivityManager, TRANSPORT_VPN). Les réseaux annoncés pendant
 * [graceMs] après l'abonnement sont l'état initial : mémorisés sans alerte.
 * Une seule alerte « connecté » au premier VPN, « déconnecté » quand le dernier disparaît.
 */
class VpnTracker(private val graceMs: Long = 1_500L) {
    private val networks = HashSet<String>()

    /** true = annoncer « VPN connecté ». */
    @Synchronized
    fun onAvailable(network: String, sinceRegisterMs: Long): Boolean {
        val wasEmpty = networks.isEmpty()
        if (!networks.add(network)) return false
        return wasEmpty && sinceRegisterMs >= graceMs
    }

    /** true = annoncer « VPN déconnecté ». */
    @Synchronized
    fun onLost(network: String): Boolean = networks.remove(network) && networks.isEmpty()
}
