package com.karelisio.bulle.modules.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.broadcasts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Module « Batterie détaillée » : contenu discret pendant la charge (et, en option, en
 * permanence) qui ouvre une vue détaillée : niveau, temps de charge, autonomie estimée,
 * température, tension, santé, puissance, source et batteries des accessoires Bluetooth.
 * Désactivé par défaut. Les alertes ponctuelles « chargeur branché / batterie faible » restent
 * celles du module Alertes : ce contenu, de basse priorité, prend le relais après elles.
 *
 * Tout est événementiel (ACTION_BATTERY_CHANGED) ; le courant n'est lu que vue détaillée ouverte.
 */
object BatteryModule : IslandModule {
    const val ID = "battery"
    override val id = ID
    override val titleRes = R.string.battery_module_title
    override val enabledByDefault = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = env.settings
        .map { BatterySettingsBinding.read(it) }
        .distinctUntilChanged()
        .transformLatest { emitAll(run(env, it)) }

    private fun run(env: ModuleEnv, settings: BatterySettings): Flow<List<IslandContent>> = channelFlow {
        val ctx = env.context.applicationContext
        send(emptyList())
        var history = runCatching { BatteryHistoryStore.flow(ctx).first() }.getOrDefault(emptyList())
        var postedAt = SystemClock.elapsedRealtime()
        var lastPlugged: Boolean? = null
        broadcasts(ctx, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).collect { i ->
            val info = BatteryReader.parse(i) ?: return@collect
            val now = System.currentTimeMillis()
            // Relevé seulement si le niveau ou le branchement change (la diffusion est fréquente).
            val sample = LevelSample(now, info.level, info.plugged)
            val recorded = BatteryMath.record(history, sample)
            if (recorded != history) {
                history = recorded
                BatteryHistoryStore.record(ctx, sample)
            }
            if (lastPlugged != info.plugged) postedAt = SystemClock.elapsedRealtime()
            lastPlugged = info.plugged
            val show = (info.plugged && settings.showWhileCharging) || settings.showAlways
            if (!show) {
                send(emptyList())
                return@collect
            }
            val subtitle = subtitle(ctx, info, history, now)
            send(listOf(BatteryDetailContent(info, subtitle, settings.accessories, postedAt)))
        }
    }

    /** Seconde ligne compacte : « Plein dans 1 h 05 » en charge, « ≈ 6 h 20 » sur batterie. */
    internal fun subtitle(ctx: Context, info: BatteryInfo, history: List<LevelSample>, now: Long): String? = when {
        info.status == ChargeStatus.FULL || (info.plugged && info.level >= 100) -> ctx.getString(R.string.battery_status_full)
        info.plugged -> BatteryMath.chargeRemainingMs(BatteryReader.live(ctx, true).chargeTimeMs, history, info.level, now)
            ?.let { ctx.getString(R.string.battery_full_in_short, formatDuration(ctx, it)) }
        else -> BatteryMath.dischargeRemainingMs(history, info.level, now)
            ?.let { ctx.getString(R.string.battery_left_short, formatDuration(ctx, it)) }
    }
}

/** « 1 h 05 » ou « 45 min ». */
fun formatDuration(ctx: Context, ms: Long): String {
    val (h, m) = BatteryMath.hoursMinutes(ms)
    return if (h > 0) ctx.getString(R.string.battery_duration_hm, h, m) else ctx.getString(R.string.battery_duration_m, m)
}
