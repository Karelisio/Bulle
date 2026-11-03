package com.karelisio.bulle.modules.alerts

/** Type de charge détecté au branchement. */
enum class ChargeKind { NORMAL, FAST, WIRELESS }

/**
 * Règles pures (sans Android) des alertes, isolées pour être testées unitairement.
 */
object AlertRules {

    /** Puissance au-delà de laquelle on parle de « charge rapide » (en watts). */
    const val FAST_CHARGE_WATTS = 15.0

    /**
     * Seuil de batterie faible franchi vers le bas entre deux mesures, ou null.
     * Aucune alerte sans mesure précédente (état initial), en charge, ou si le niveau monte.
     * Si plusieurs seuils sont franchis d'un coup, on retient le plus bas.
     */
    fun crossedLowThreshold(previous: Int?, current: Int, thresholds: Collection<Int>, charging: Boolean): Int? {
        if (previous == null || charging || current >= previous) return null
        return thresholds.filter { current <= it && previous > it }.minOrNull()
    }

    /** Le téléphone vient-il d'être branché ? (aucune alerte pour l'état initial) */
    fun justPlugged(previousPlugged: Int?, currentPlugged: Int): Boolean =
        previousPlugged != null && previousPlugged == 0 && currentPlugged != 0

    /**
     * Type de charge à partir des extras (en partie non documentés) d'ACTION_BATTERY_CHANGED :
     * « max_charging_current » (µA) et « max_charging_voltage » (µV, 5 V si absent).
     */
    fun chargeKind(wireless: Boolean, maxCurrentMicroA: Int, maxVoltageMicroV: Int): ChargeKind {
        if (wireless) return ChargeKind.WIRELESS
        if (maxCurrentMicroA <= 0) return ChargeKind.NORMAL
        val volts = if (maxVoltageMicroV > 0) maxVoltageMicroV / 1_000_000.0 else 5.0
        val watts = maxCurrentMicroA / 1_000_000.0 * volts
        return if (watts >= FAST_CHARGE_WATTS) ChargeKind.FAST else ChargeKind.NORMAL
    }

    /** Niveau de batterie en % à partir de EXTRA_LEVEL / EXTRA_SCALE, ou null si inconnu. */
    fun batteryPercent(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).toInt().coerceIn(0, 100)
    }

    /**
     * Nom de réseau Wi-Fi lisible : retire les guillemets ajoutés par le système,
     * null si masqué (« <unknown ssid> », sans permission de localisation) ou vide.
     */
    fun cleanSsid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().removeSurrounding("\"")
        if (s.isBlank() || s == UNKNOWN_SSID || s == "0x") return null
        return s
    }

    /** Niveau de batterie Bluetooth valide (0..100), sinon null (le système envoie -1 si inconnu). */
    fun bluetoothBattery(level: Int): Int? = level.takeIf { it in 0..100 }

    /** Dossier de captures d'écran (RELATIVE_PATH de MediaStore), toutes marques confondues. */
    fun isScreenshotPath(relativePath: String?, displayName: String?): Boolean {
        val p = relativePath.orEmpty().lowercase()
        val n = displayName.orEmpty().lowercase()
        return "screenshot" in p || "capture" in p || n.startsWith("screenshot") || n.startsWith("capture")
    }

    private const val UNKNOWN_SSID = "<unknown ssid>"
}
