package com.karelisio.bulle.modules.halo

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.island.HiddenReason
import com.karelisio.bulle.island.ScreenEnv
import com.karelisio.bulle.island.VisibilityPolicy
import com.karelisio.bulle.modules.volet.media.MediaColors
import kotlin.math.max

/** Ce que le halo doit savoir d'une notification (copie figée, testable). */
data class HaloCandidate(
    val packageName: String,
    val ongoing: Boolean,
    /** Importance du canal basse ou minimale (notification silencieuse). */
    val silent: Boolean,
    val conversation: Boolean,
    val groupSummary: Boolean,
    /** Notification d'un lecteur multimédia (MediaStyle). */
    val media: Boolean = false,
    /** Notification mise à jour sans alerte (onlyAlertOnce, simple rafraîchissement). */
    val update: Boolean = false,
)

/** Décision prise pour une notification (le motif est noté dans le Journal de l'île). */
enum class HaloVerdict {
    SHOW,
    /** Écran éteint : affichage sur fond noir (activité dédiée). */
    SHOW_SCREEN_OFF,
    SKIP_DISABLED,
    SKIP_APP,
    SKIP_SUMMARY,
    SKIP_ONGOING,
    SKIP_SILENT,
    SKIP_NOT_CONVERSATION,
    SKIP_DND,
    SKIP_FULLSCREEN,
    SKIP_HIDDEN_APP,
    SKIP_SCREEN_OFF,
    SKIP_RATE_LIMIT,
}

/** Filtres du halo (logique pure). */
object HaloFilter {

    fun decide(
        c: HaloCandidate,
        s: HaloSettings,
        dnd: Boolean,
        screenBlock: HiddenReason?,
        screenOn: Boolean,
        screenOffAllowed: Boolean = true,
    ): HaloVerdict {
        if (!s.enabled) return HaloVerdict.SKIP_DISABLED
        if (c.groupSummary) return HaloVerdict.SKIP_SUMMARY
        if (c.update) return HaloVerdict.SKIP_ONGOING
        if (s.appFilter == HaloAppFilter.SELECTED && c.packageName !in s.selectedApps) return HaloVerdict.SKIP_APP
        if (s.ignoreOngoing && (c.ongoing || c.media)) return HaloVerdict.SKIP_ONGOING
        if (s.ignoreSilent && c.silent) return HaloVerdict.SKIP_SILENT
        if (s.conversationsOnly && !c.conversation) return HaloVerdict.SKIP_NOT_CONVERSATION
        if (s.muteInDnd && dnd) return HaloVerdict.SKIP_DND
        if (!screenOn) {
            if (!s.screenOff) return HaloVerdict.SKIP_SCREEN_OFF
            return if (screenOffAllowed) HaloVerdict.SHOW_SCREEN_OFF else HaloVerdict.SKIP_RATE_LIMIT
        }
        return when (screenBlock) {
            HiddenReason.FULLSCREEN, HiddenReason.LANDSCAPE, HiddenReason.RULE -> HaloVerdict.SKIP_FULLSCREEN
            HiddenReason.HIDDEN_APP -> HaloVerdict.SKIP_HIDDEN_APP
            else -> HaloVerdict.SHOW
        }
    }

    /** Masquage de l'écran pour le halo : mêmes règles que l'île ([VisibilityPolicy.overlayHiddenReason]). */
    fun screenBlock(settings: BulleSettings, screen: ScreenEnv, foreground: String?, launcher: String?): HiddenReason? =
        VisibilityPolicy.overlayHiddenReason(settings, screen, foreground, launcher)
}

/** Limite de réveils de l'écran (fenêtre glissante d'une heure, écart minimal entre deux). */
class HaloRateLimiter(private val windowMs: Long = 3_600_000L, private val minGapMs: Long = 20_000L) {
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun allowed(now: Long, maxPerWindow: Int): Boolean {
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= maxPerWindow) return false
        val last = times.lastOrNull()
        return last == null || now - last >= minGapMs
    }

    @Synchronized
    fun record(now: Long) {
        times.addLast(now)
    }

    /** Vérifie et, si permis, enregistre. */
    fun tryAcquire(now: Long, maxPerWindow: Int): Boolean =
        allowed(now, maxPerWindow).also { if (it) record(now) }
}

/** Couleurs du halo (logique pure sur des entiers ARGB). */
object HaloPalette {
    /** Vert de la charge. */
    const val CHARGE = 0xFF5BE38A.toInt()
    /** Bleu par défaut (aucune couleur exploitable). */
    const val DEFAULT = 0xFF7FD3FF.toInt()

    /**
     * Rend une couleur lumineuse et saturée, lisible comme lumière sur un bord noir :
     * une icône grise reste grise, mais jamais sombre.
     */
    fun glow(argb: Int): Int {
        val hsl = MediaColors.toHsl(argb)
        val sat = if (hsl[1] < 0.08f) hsl[1] else max(hsl[1], 0.62f)
        val light = hsl[2].coerceIn(0.58f, 0.74f)
        return MediaColors.fromHsl(hsl[0], sat, light)
    }

    /** Choix de la couleur : couleur par appli, Écho, pochette, puis le mode réglé. */
    fun pick(
        s: HaloSettings,
        packageName: String?,
        echo: Boolean,
        artwork: Int?,
        appIcon: Int?,
        dynamic: Int,
        echoPink: Int,
    ): Int {
        packageName?.let { s.appColors[it] }?.let { return it.toInt() }
        if (echo && s.echoHeart) return echoPink
        if (artwork != null && s.artworkForMusic) return glow(artwork)
        return when (s.colorMode) {
            HaloColorMode.APP_ICON -> glow(appIcon ?: dynamic)
            HaloColorMode.DYNAMIC -> dynamic
            HaloColorMode.FIXED -> s.fixedColor.toInt()
        }
    }
}

/** Durées d'une animation de halo (logique pure). */
object HaloTiming {
    /** Durée d'un battement du cœur d'Écho (identique à EchoHeart). */
    const val HEART_BEAT_MS = 1150L
    const val FADE_MS = 280L

    /**
     * Enveloppe du battement d'Écho (0..1) à l'instant [ms] : double « boum-boum » calé sur
     * les images clés du cœur (130, 260, 390, 600 ms).
     */
    fun heartbeat(ms: Long): Float {
        val t = (ms % HEART_BEAT_MS).toFloat()
        val k = floatArrayOf(0f, 130f, 260f, 390f, 600f, HEART_BEAT_MS.toFloat())
        val v = floatArrayOf(0.15f, 1f, 0.35f, 0.85f, 0.15f, 0.15f)
        for (i in 0 until k.size - 1) {
            if (t <= k[i + 1]) {
                val f = (t - k[i]) / (k[i + 1] - k[i])
                return v[i] + (v[i + 1] - v[i]) * f
            }
        }
        return v.last()
    }

    /** Opacité globale : apparition rapide, maintien, fondu final. */
    fun envelope(elapsed: Long, total: Long): Float {
        if (total <= 0) return 0f
        val fadeIn = 120f
        val e = elapsed.toFloat()
        return when {
            e < fadeIn -> e / fadeIn
            e > total - FADE_MS -> ((total - e) / FADE_MS).coerceIn(0f, 1f)
            else -> 1f
        }
    }
}
