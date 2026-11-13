package com.karelisio.bulle.modules.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.karelisio.bulle.modules.alerts.AlertRules

/** État de charge simplifié. */
enum class ChargeStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING }

/** Source d'alimentation. */
enum class PowerSource { NONE, AC, USB, WIRELESS, DOCK }

/** Santé de la batterie. */
enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, FAILURE, UNKNOWN }

/** Instantané d'ACTION_BATTERY_CHANGED, normalisé. */
data class BatteryInfo(
    val level: Int,
    val status: ChargeStatus,
    val source: PowerSource,
    val temperatureC: Double?,
    val voltageMv: Int?,
    val health: BatteryHealth,
) {
    /** Branché (y compris plein ou charge en pause). */
    val plugged: Boolean get() = source != PowerSource.NONE
    val charging: Boolean get() = status == ChargeStatus.CHARGING
}

/** Mesures lues à la demande (courant, temps de charge système). */
data class LiveBattery(val currentMicroA: Long?, val chargeTimeMs: Long?)

/** Lecture Android de la batterie, sans permission. */
object BatteryReader {

    /** Valeur « collante » actuelle, sans abonnement. */
    fun current(context: Context): BatteryInfo? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::parse)

    fun parse(i: Intent): BatteryInfo? {
        val level = AlertRules.batteryPercent(
            i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            i.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        ) ?: return null
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            else -> ChargeStatus.DISCHARGING
        }
        val source = sourceOf(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            else -> BatteryHealth.UNKNOWN
        }
        return BatteryInfo(
            level = level,
            status = status,
            source = source,
            temperatureC = BatteryMath.temperatureCelsius(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)),
            voltageMv = BatteryMath.normalizeVoltageMillivolts(i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)),
            health = health,
        )
    }

    /** EXTRA_PLUGGED → source (DOCK = 8, constante de l'API 33 écrite en dur pour minSdk 31). */
    fun sourceOf(plugged: Int): PowerSource = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
        PLUGGED_DOCK -> PowerSource.DOCK
        0 -> PowerSource.NONE
        else -> PowerSource.AC
    }

    /** Courant instantané et temps de charge restant donné par le système (si connus). */
    fun live(context: Context, charging: Boolean): LiveBattery {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return LiveBattery(null, null)
        val raw = runCatching { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
        val current = raw?.let { BatteryMath.normalizeCurrentMicroAmps(it, charging) }
        val chargeTime = runCatching { bm.computeChargeTimeRemaining() }.getOrNull()?.takeIf { it > 0 }
        return LiveBattery(current, chargeTime)
    }

    private const val PLUGGED_DOCK = 8
}
