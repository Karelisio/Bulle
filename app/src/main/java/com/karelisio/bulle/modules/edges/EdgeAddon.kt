package com.karelisio.bulle.modules.edges

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.island.AddonEnv
import com.karelisio.bulle.island.CutoutLocator
import com.karelisio.bulle.island.ForegroundApp
import com.karelisio.bulle.island.HomeApp
import com.karelisio.bulle.island.IslandAddon
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.island.VisibilityPolicy
import com.karelisio.bulle.island.overlay.OverlayWindow
import com.karelisio.bulle.modules.standby.StandbyState
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bords : zones invisibles le long des bords gauche et droit qui reconnaissent des gestes
 * (glissements le long du bord, double toucher, appui long, glissement vers l'intérieur en
 * option) et lancent une action d'[com.karelisio.bulle.core.actions.ActionExecutor].
 * Mêmes fenêtres d'overlay que l'île, dans le même service.
 */
object EdgeAddon : IslandAddon {
    const val ID = "edges"
    override val id = ID
    override fun start(env: AddonEnv): AutoCloseable = EdgeRuntime(env).also { it.start() }
}

/** Ce qui décide de la pose des zones (comparé pour ne replacer les fenêtres qu'au besoin). */
private data class EdgeLayout(
    val settings: EdgeSettings,
    val zones: List<PlacedZone>,
    val active: Boolean,
    val colored: Boolean,
    val haptics: Boolean,
    val screenWidth: Int,
    val screenHeight: Int,
)

private class EdgeRuntime(private val env: AddonEnv) : AutoCloseable {
    private val service = env.service
    private val density = service.resources.displayMetrics.density
    private val zones = HashMap<EdgeSlot, ZoneWindow>()
    private val feedback = HashMap<EdgeSide, FeedbackWindow>()
    private var job: Job? = null
    private var lastActive: Boolean? = null
    @Volatile
    var layout: EdgeLayout? = null
        private set

    /** Horloge d'une seconde pendant un affichage temporaire (fin du test Fiabilité). */
    private val flashTick = flow {
        while (true) {
            emit(SystemClock.uptimeMillis())
            delay(1_000L)
        }
    }

    fun start() {
        EdgeBus.setRunning(true)
        job = env.scope.launch {
            combine(
                env.controller.settings,
                env.controller.screenEnv,
                ForegroundApp.packageName,
                combine(EdgeBus.preview, EdgeBus.flashUntil, flashTick) { p, until, now -> p || now < until },
                StandbyState.active,
            ) { s, screen, fg, colored, standby ->
                val e = s.edges
                val info = CutoutLocator.screenInfo(service)
                val voletPx = if (s.volet.enabled) (s.volet.triggerHeightDp * density).roundToInt() else 0
                val navBottom = runCatching {
                    service.getSystemService(WindowManager::class.java).maximumWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
                }.getOrDefault(0)
                val placed = EdgeGeometry.place(
                    e, info.width, info.height, density,
                    topReservedPx = EdgeGeometry.topReserved(info.statusBarHeight, voletPx, density),
                    bottomReservedPx = navBottom,
                )
                val hidden = VisibilityPolicy.overlayHiddenReason(s, screen, fg, HomeApp.packageName(service))
                val active = e.enabled && hidden == null && !screen.locked && !standby && (fg == null || fg !in e.disabledApps)
                EdgeLayout(e, placed, active, colored, s.motion.haptics, info.width, info.height)
            }.distinctUntilChanged().collect(::apply)
        }
    }

    private fun apply(l: EdgeLayout) {
        layout = l
        EdgeBus.publish(l.zones)
        val shown = l.active || l.colored
        if (lastActive != l.active) {
            IslandLog.log(if (l.active) "Bords : zones actives (${l.zones.size})" else "Bords : zones masquées")
            lastActive = l.active
        }
        val wanted = if (shown) l.zones.associateBy { it.slot } else emptyMap()
        (zones.keys - wanted.keys).forEach { zones.remove(it)?.destroy() }
        wanted.forEach { (slot, placed) ->
            val win = zones.getOrPut(slot) { ZoneWindow(slot) }
            win.place(placed.rect, l.settings.zone(slot), l.settings.swipeInAllowed, l.active, l.colored)
        }
        if (!shown) {
            feedback.values.forEach { it.destroy() }
            feedback.clear()
        }
    }

    private fun feedbackFor(side: EdgeSide): FeedbackWindow {
        val l = layout
        return feedback.getOrPut(side) { FeedbackWindow(side) }.also { it.place(l?.screenWidth ?: 0, l?.screenHeight ?: 0) }
    }

    override fun close() {
        job?.cancel()
        zones.values.forEach { it.destroy() }
        zones.clear()
        feedback.values.forEach { it.destroy() }
        feedback.clear()
        EdgeBus.setRunning(false)
    }

    // ---------------------------------------------------------------------------------------

    /** Fenêtre non touchable du retour visuel, le long d'un bord (goutte, jauge). */
    private inner class FeedbackWindow(val side: EdgeSide) {
        val state = EdgeTouchState()
        private val widthPx = (140 * density).roundToInt()
        private val win = OverlayWindow(service, "Bulle — bord (retour)", extraFlags = LP.FLAG_NOT_TOUCHABLE)

        fun place(screenW: Int, screenH: Int) {
            val p = win.params
            val x = if (side == EdgeSide.LEFT) 0 else (screenW - widthPx).coerceAtLeast(0)
            if (p.x != x || p.y != 0 || p.width != widthPx || p.height != screenH.coerceAtLeast(1)) {
                p.x = x; p.y = 0; p.width = widthPx; p.height = screenH.coerceAtLeast(1)
                win.commit()
            }
            if (!win.isShown) {
                win.showCompose {
                    BulleTheme(dark = true) { EdgeFeedback(state, side, layout?.settings?.liquidFeedback != false) }
                }
            }
        }

        /** Coordonnées écran → coordonnées de la fenêtre. */
        fun localX(rawX: Float) = rawX - win.params.x
        fun localY(rawY: Float) = rawY - win.params.y

        fun destroy() = win.destroy()
    }

    /** Fenêtre tactile d'une zone. */
    private inner class ZoneWindow(val slot: EdgeSlot) {
        private val win = OverlayWindow(service, "Bulle — bord ${slot.name.lowercase()}")
        private val view = ZoneView(service)
        private var zone = EdgeZone()
        private var active = false

        init {
            win.showView(view)
        }

        fun place(r: EdgeRect, z: EdgeZone, swipeInAllowed: Boolean, active: Boolean, colored: Boolean) {
            zone = z
            this.active = active
            val p = win.params
            if (p.x != r.x || p.y != r.y || p.width != r.width || p.height != r.height) {
                p.x = r.x; p.y = r.y; p.width = r.width; p.height = r.height
                win.commit()
            }
            // Zones inactives mais colorées (aperçu sans module activé) : ne captent pas les touchers.
            win.setFlag(LP.FLAG_NOT_TOUCHABLE, !active)
            view.configure(r, z, swipeIn = swipeInAllowed && z.swipeIn.type != ActionType.NONE, colored = colored)
        }

        fun destroy() = win.destroy()

        @SuppressLint("ViewConstructor")
        private inner class ZoneView(context: Context) : View(context) {
            private val main = Handler(Looper.getMainLooper())
            private var detector = newDetector(EdgeZone(), false, 100)
            private var lastX = 0f
            private var lastY = 0f
            private var downRawX = 0f
            private val ticker = object : Runnable {
                override fun run() {
                    if (!detector.active) return
                    handle(detector.tick(lastX, lastY, SystemClock.uptimeMillis()))
                    main.postDelayed(this, 40L)
                }
            }

            private fun newDetector(z: EdgeZone, swipeIn: Boolean, heightPx: Int) = EdgeGestureDetector(
                inwardSign = if (slot.side == EdgeSide.LEFT) 1 else -1,
                slopPx = 8f * density,
                swipeMinPx = 36f * density,
                inwardMinPx = 28f * density,
                sliderTravelPx = maxOf(heightPx.toFloat(), 220f * density),
                slider = z.slider != EdgeSlider.OFF,
                swipeIn = swipeIn,
                doubleTapEnabled = z.doubleTap.type != ActionType.NONE,
                longPressEnabled = z.longPress.type != ActionType.NONE,
            )

            fun configure(r: EdgeRect, z: EdgeZone, swipeIn: Boolean, colored: Boolean) {
                if (!detector.active) detector = newDetector(z, swipeIn, r.height)
                background = if (colored) {
                    GradientDrawable().apply {
                        cornerRadius = r.width / 2f
                        setColor(PREVIEW_COLORS[slot.ordinal % PREVIEW_COLORS.size])
                    }
                } else {
                    null
                }
                // Glissement vers l'intérieur : la zone est retirée du geste Retour (choix explicite).
                systemGestureExclusionRects = if (swipeIn) listOf(Rect(0, 0, r.width, r.height)) else emptyList()
                contentDescription = null
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (!active) return false
                val t = SystemClock.uptimeMillis()
                lastX = event.rawX
                lastY = event.rawY
                val fb = feedbackFor(slot.side)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        fb.state.touching = true
                        fb.state.x = fb.localX(event.rawX)
                        fb.state.y = fb.localY(event.rawY)
                        fb.state.stretch = 0f
                        handle(detector.down(event.rawX, event.rawY, t))
                        main.removeCallbacks(ticker)
                        main.postDelayed(ticker, 40L)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        fb.state.x = fb.localX(event.rawX)
                        fb.state.y = fb.localY(event.rawY)
                        fb.state.stretch = inwardStretch(slot.side, event.rawX - downRawX)
                        handle(detector.move(event.rawX, event.rawY, t))
                    }
                    MotionEvent.ACTION_UP -> {
                        handle(detector.up(event.rawX, event.rawY, t))
                        release(fb)
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // Le système a repris la main (geste Retour) : rien n'est lancé.
                        handle(detector.cancel())
                        release(fb)
                    }
                }
                return true
            }

            private fun release(fb: FeedbackWindow) {
                main.removeCallbacks(ticker)
                fb.state.touching = false
                fb.state.stretch = 0f
            }

            private fun handle(events: List<EdgeEvent>) {
                val fb = feedback[slot.side] ?: return
                for (e in events) when (e) {
                    is EdgeEvent.Gesture -> {
                        val action = zone.actionFor(e.gesture)
                        if (action.type == ActionType.NONE) continue
                        haptic(HapticFeedbackConstants.CONFIRM)
                        fb.state.confirms++
                        IslandLog.log("Bords : ${slot.name.lowercase()} ${e.gesture.name.lowercase()} → ${action.type}")
                        env.controller.runAction(action)
                    }
                    EdgeEvent.SliderStart -> {
                        val kind = zone.slider
                        if (!EdgeSliders.canWrite(service, kind)) {
                            IslandLog.log("Bords : luminosité sans autorisation « Modifier les paramètres système »")
                            runCatching {
                                service.startActivity(
                                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${service.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            continue
                        }
                        fb.state.value = EdgeSliders.read(service, kind)
                        fb.state.slider = kind
                        haptic(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    is EdgeEvent.SliderMove -> {
                        val kind = fb.state.slider ?: continue
                        val before = fb.state.value
                        val target = (before + e.delta).coerceIn(0f, 1f)
                        fb.state.value = EdgeSliders.write(service, kind, target).let { if (kind == EdgeSlider.VOLUME) target else it }
                        if (EdgeSliderMath.crossedNotch(before, fb.state.value)) {
                            haptic(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    EdgeEvent.SliderEnd -> {
                        val kind = fb.state.slider ?: continue
                        haptic(HapticFeedbackConstants.CONFIRM)
                        IslandLog.log("Bords : ${if (kind == EdgeSlider.BRIGHTNESS) "luminosité" else "volume"} ${(fb.state.value * 100).roundToInt()} %")
                        main.postDelayed({ if (!fb.state.touching) fb.state.slider = null }, 700L)
                    }
                }
            }

            private fun haptic(constant: Int) {
                if (layout?.haptics == false) return
                performHapticFeedback(constant)
            }
        }
    }

    companion object {
        /** Couleurs de l'aperçu (une par zone), translucides. */
        val PREVIEW_COLORS = intArrayOf(0x996E9BFF.toInt(), 0x99FF8FB8.toInt(), 0x997FE7D7.toInt(), 0x99FFB36B.toInt())
    }
}
