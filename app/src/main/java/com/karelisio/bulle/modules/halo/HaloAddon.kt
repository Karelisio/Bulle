package com.karelisio.bulle.modules.halo

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.toArgb
import com.karelisio.bulle.island.AddonEnv
import com.karelisio.bulle.island.ForegroundApp
import com.karelisio.bulle.island.HomeApp
import com.karelisio.bulle.island.IslandAddon
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.island.IslandPulses
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.battery.BatteryReader
import com.karelisio.bulle.modules.echo.EchoModule
import com.karelisio.bulle.modules.echo.EchoPink
import com.karelisio.bulle.modules.music.MusicContent
import com.karelisio.bulle.modules.standby.StandbyState
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Halo : à chaque notification retenue, un halo lumineux fait le tour de l'écran. Vit dans le
 * service d'accessibilité de l'île (overlay plein écran non touchable) ; écran éteint, il passe
 * par [HaloNightActivity].
 */
object HaloAddon : IslandAddon {
    const val ID = "halo"
    override val id = ID

    override fun start(env: AddonEnv): AutoCloseable = HaloRuntime(env).also { it.start() }

    /** Limite partagée des réveils de l'écran (survit aux reconnexions du service). */
    internal val rateLimiter = HaloRateLimiter()
}

private class HaloRuntime(private val env: AddonEnv) : AutoCloseable {
    private val service = env.service
    private val overlay = HaloOverlay(service)
    private val jobs = mutableListOf<Job>()
    /** Dernière publication par clé : une mise à jour (progression, rafraîchissement) ne relance pas de halo. */
    private val lastPosted = HashMap<String, Long>()

    private val settings get() = env.controller.settings.value.halo

    fun start() {
        HaloBus.setRunning(true)
        val scope = env.scope
        jobs += scope.launch {
            NotificationRepository.events.filterIsInstance<NotificationEvent.Posted>().collect { onPosted(it.sbn) }
        }
        jobs += scope.launch { HaloBus.tests.collect { style -> test(style) } }
        jobs += scope.launch {
            HaloBus.relock.collect {
                IslandLog.log("Halo : écran rééteint")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
        }
        // Branchement : halo vert qui monte selon le pourcentage.
        jobs += scope.launch {
            broadcasts(service, IntentFilter(Intent.ACTION_POWER_CONNECTED)).collect {
                val s = settings
                if (!s.enabled || !s.chargingHalo) return@collect
                val level = BatteryReader.current(service)?.level ?: return@collect
                IslandLog.log("Halo : charge ($level %)")
                play(HaloRequest(HaloKind.CHARGE, HaloPalette.CHARGE, s.style, level = level / 100f), wait = false)
            }
        }
        // Nouveau morceau : couleur de la pochette (option).
        jobs += scope.launch {
            env.controller.contents
                .map { list -> list.filterIsInstance<MusicContent>().firstOrNull { it.playback.isPlaying } }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.track.identity == b.track.identity }
                .drop(1)
                .collect { music ->
                    val s = settings
                    if (!s.enabled || !s.trackChange || blockedNow() != null) return@collect
                    IslandLog.log("Halo : nouveau morceau (${music.track.packageName})")
                    play(HaloRequest(HaloKind.MUSIC, HaloPalette.glow(music.accent.toArgb()), s.style, packageName = music.track.packageName))
                }
        }
    }

    private fun blockedNow() = HaloFilter.screenBlock(
        env.controller.settings.value,
        env.controller.screenEnv.value,
        ForegroundApp.packageName.value,
        HomeApp.packageName(service),
    )

    private fun candidate(sbn: StatusBarNotification): HaloCandidate {
        val n = sbn.notification
        val ranking = NotificationRepository.ranking(sbn.key)
        val now = SystemClock.elapsedRealtime()
        val previous = lastPosted.put(sbn.key, now)
        if (lastPosted.size > 200) lastPosted.entries.removeAll { now - it.value > 3_600_000L }
        val onlyOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        val template = n.extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return HaloCandidate(
            packageName = sbn.packageName,
            ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            silent = ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_LOW,
            conversation = ranking?.isConversation == true || template.endsWith("MessagingStyle"),
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            media = n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true,
            // Republiée : seule une vraie nouvelle alerte relance un halo.
            update = previous != null && (onlyOnce || now - previous < 2_000L),
        )
    }

    private suspend fun onPosted(sbn: StatusBarNotification) {
        val s = settings
        if (!s.enabled) return
        // Veille affiche ses propres cartes de notification.
        if (StandbyState.active.value) return
        val c = candidate(sbn)
        // Mode éco : économie d'énergie active → pas de halo.
        if (s.powerSaveOff && service.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return
        val screenOn = service.getSystemService(PowerManager::class.java)?.isInteractive != false
        val dnd = service.getSystemService(NotificationManager::class.java)
            ?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL } == true
        val verdict = HaloFilter.decide(
            c, s, dnd,
            screenBlock = if (screenOn) blockedNow() else null,
            screenOn = screenOn,
            screenOffAllowed = !screenOn && HaloAddon.rateLimiter.allowed(SystemClock.elapsedRealtime(), s.screenOffMaxPerHour),
        )
        if (verdict != HaloVerdict.SHOW && verdict != HaloVerdict.SHOW_SCREEN_OFF) {
            if (verdict !in QUIET_SKIPS) IslandLog.log("Halo ignoré : $verdict (${c.packageName})")
            return
        }
        val echo = c.packageName == EchoModule.ECHO_PACKAGE
        val color = colorFor(s, c.packageName, echo)
        val request = HaloRequest(if (echo && s.echoHeart) HaloKind.ECHO else HaloKind.NOTIFICATION, color, s.style, packageName = c.packageName)
        if (verdict == HaloVerdict.SHOW_SCREEN_OFF) {
            HaloAddon.rateLimiter.record(SystemClock.elapsedRealtime())
            IslandLog.log("Halo écran éteint (${c.packageName})")
            HaloNightActivity.launch(service, request)
            return
        }
        IslandLog.log("Halo : ${c.packageName}" + if (echo) " (Écho)" else "")
        play(request, notificationKey = sbn.key)
    }

    private suspend fun colorFor(s: HaloSettings, pkg: String, echo: Boolean): Int {
        val music = env.controller.contents.value.filterIsInstance<MusicContent>().firstOrNull { it.track.packageName == pkg }
        val icon = if (s.colorMode == HaloColorMode.APP_ICON && !s.appColors.containsKey(pkg)) HaloDevice.iconColor(service, pkg) else null
        return HaloPalette.pick(
            s, pkg, echo,
            artwork = music?.accent?.toArgb(),
            appIcon = icon,
            dynamic = HaloDevice.dynamicColor(service),
            echoPink = EchoPink.toArgb(),
        )
    }

    private fun test(style: HaloStyle?) {
        val s = settings
        IslandLog.log("Halo : test" + (style?.let { " ($it)" } ?: ""))
        val color = when (s.colorMode) {
            HaloColorMode.FIXED -> s.fixedColor.toInt()
            else -> HaloDevice.dynamicColor(service)
        }
        env.scope.launch { play(HaloRequest(HaloKind.TEST, color, style ?: s.style), wait = false) }
    }

    /**
     * Joue le halo. Synchronisation avec l'île : s'il arrive un gonflement de l'île (même
     * notification, ou tout juste avant), le halo part à cet instant ; sinon après un court délai.
     */
    private suspend fun play(request: HaloRequest, notificationKey: String? = null, wait: Boolean = true) {
        if (wait) {
            val since = SystemClock.elapsedRealtime() - 400L
            withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                IslandPulses.latest.filterNotNull().first { p ->
                    p.at >= since && (notificationKey == null || p.notificationKey == null || p.notificationKey == notificationKey)
                }
            }
        }
        overlay.show(request, HaloDevice.screen(service), HaloLook.of(settings, service.resources.displayMetrics.density))
        if (request.kind != HaloKind.TEST) com.karelisio.bulle.system.ModuleUsage.record(service, com.karelisio.bulle.system.TrackedModule.HALO)
    }

    override fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        overlay.destroy()
        HaloBus.setRunning(false)
    }

    companion object {
        /** Attente maximale du gonflement de l'île (ms). */
        const val SYNC_TIMEOUT_MS = 350L

        /** Refus trop fréquents pour être notés (toutes les notifications en cours, résumés). */
        val QUIET_SKIPS = setOf(HaloVerdict.SKIP_DISABLED, HaloVerdict.SKIP_SUMMARY, HaloVerdict.SKIP_ONGOING)
    }
}
