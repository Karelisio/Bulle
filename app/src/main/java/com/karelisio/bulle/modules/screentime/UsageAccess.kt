package com.karelisio.bulle.modules.screentime

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.karelisio.bulle.modules.alerts.broadcasts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.time.ZoneId

/** Utilisation d'un jour et de la veille à la même heure (pour la comparaison). */
data class ScreenTimeReport(val today: DayUsage, val yesterdaySameTime: DayUsage?, val yesterdayTotal: DayUsage?)

/**
 * Accès Android du module : permission « Accès aux données d'utilisation » (spéciale, accordée
 * dans les paramètres), lecture des UsageEvents du jour depuis minuit local, état de l'écran.
 */
object UsageAccess {

    /** L'utilisateur a-t-il accordé l'accès aux données d'utilisation ? */
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Ouvre l'écran système « Accès aux données d'utilisation » (sur Bulle si possible). */
    fun openSettings(context: Context) {
        val base = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent(base).setData(Uri.fromParts("package", context.packageName, null)))
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(base) }
        } catch (_: SecurityException) {
            runCatching { context.startActivity(base) }
        }
    }

    /** Lanceur(s) et interface système : exclus du temps d'écran, comme le fait le bien-être numérique. */
    fun excludedPackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = runCatching {
            context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return (launchers + "com.android.systemui").toSet()
    }

    /** Événements d'utilisation de [start, end[ convertis en [UsageEvt] (vide sans permission). */
    fun events(context: Context, start: Long, end: Long): List<UsageEvt> {
        if (!hasAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val result = ArrayList<UsageEvt>()
        runCatching {
            val events = usm.queryEvents(start, end) ?: return emptyList()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val kind = when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageKind.RESUMED
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageKind.PAUSED
                    UsageEvents.Event.ACTIVITY_STOPPED -> UsageKind.STOPPED
                    UsageEvents.Event.SCREEN_INTERACTIVE -> UsageKind.SCREEN_ON
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageKind.SCREEN_OFF
                    UsageEvents.Event.KEYGUARD_SHOWN -> UsageKind.KEYGUARD_SHOWN
                    UsageEvents.Event.KEYGUARD_HIDDEN -> UsageKind.KEYGUARD_HIDDEN
                    else -> null
                }
                if (kind != null) result += UsageEvt(e.timeStamp, kind, e.packageName.orEmpty())
            }
        }
        return result
    }

    /** Utilisation du jour (depuis minuit local), ou null sans permission. À appeler hors du fil principal. */
    fun today(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): DayUsage? {
        if (!hasAccess(context)) return null
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return UsageAggregator.aggregate(events(context, start, now), start, now, excludedPackages(context))
    }

    /** Aujourd'hui et hier (à la même heure et en entier), ou null sans permission. Hors du fil principal. */
    fun report(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ScreenTimeReport? {
        if (!hasAccess(context)) return null
        val excluded = excludedPackages(context)
        val todayDate = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val yStart = todayDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sameTime = java.time.Instant.ofEpochMilli(now).atZone(zone).minusDays(1).toInstant().toEpochMilli()
        val today = UsageAggregator.aggregate(events(context, start, now), start, now, excluded)
        val yEvents = events(context, yStart, start)
        val ySame = if (yEvents.isEmpty()) null else UsageAggregator.aggregate(yEvents, yStart, sameTime, excluded)
        val yAll = if (yEvents.isEmpty()) null else UsageAggregator.aggregate(yEvents, yStart, start, excluded)
        return ScreenTimeReport(today, ySame, yAll)
    }

    /** Écran allumé ET déverrouillé (SCREEN_ON, SCREEN_OFF, USER_PRESENT ; valeur initiale du système). */
    fun activeScreen(context: Context): Flow<Boolean> {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        fun unlocked() = keyguard?.isKeyguardLocked != true
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        return broadcasts(context, filter)
            .mapNotNull {
                when (it.action) {
                    Intent.ACTION_SCREEN_OFF -> false
                    Intent.ACTION_USER_PRESENT -> true
                    // Écran allumé sans verrouillage (ou déverrouillage par visage déjà fait).
                    Intent.ACTION_SCREEN_ON -> if (unlocked()) true else null
                    else -> null
                }
            }
            .onStart { emit((power?.isInteractive ?: true) && unlocked()) }
            .distinctUntilChanged()
    }

    /** Écran allumé (pour limiter les recalculs à l'écran allumé). */
    fun screenOn(context: Context): Flow<Boolean> {
        val power = context.getSystemService(PowerManager::class.java)
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        return broadcasts(context, filter)
            .mapNotNull { it.action == Intent.ACTION_SCREEN_ON }
            .onStart { emit(power?.isInteractive ?: true) }
            .distinctUntilChanged()
    }
}
