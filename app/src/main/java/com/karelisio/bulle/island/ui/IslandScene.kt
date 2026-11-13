package com.karelisio.bulle.island.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.karelisio.bulle.core.settings.OpenDirection
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawWithContent
import com.karelisio.bulle.core.island.Priority
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.settings.CardStyle
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandUiState
import com.karelisio.bulle.island.WindowRequest
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.core.style.EntryAnimation
import com.karelisio.bulle.core.style.EntryAnimations
import com.karelisio.bulle.ui.stylepacks.EntryAnimated
import com.karelisio.bulle.ui.stylepacks.EntryTracker
import com.karelisio.bulle.ui.theme.bulleColorScheme
import com.karelisio.bulle.core.a11y.SystemAccessibility
import com.karelisio.bulle.core.a11y.rememberSystemHighContrast
import com.karelisio.bulle.core.settings.AccessibilitySettings
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Mode { HIDDEN, IDLE, COMPACT, EXPANDED }

/** Cible géométrique de la surface de l'île (pixels). offsetX : décalage du centre par rapport à la caméra. */
private data class SurfaceTarget(
    val mode: Mode,
    val width: Float,
    val height: Float,
    val corner: Float,
    val offsetX: Float,
    val alpha: Float,
)

/** Animatables de la surface : largeur, hauteur, coins, décalage et opacité animés ensemble. */
private class SurfaceAnim(t: SurfaceTarget) {
    val width = Animatable(t.width)
    val height = Animatable(t.height)
    val corner = Animatable(t.corner)
    val offsetX = Animatable(t.offsetX)
    val alpha = Animatable(t.alpha)
}

private val ExpandedMargin = 10.dp
private val ExpandedMaxWidth = 440.dp
private val ExpandedCorner = 34.dp
private val ShadowMargin = 10.dp

@Composable
fun IslandScene(controller: IslandController) {
    val state by controller.state.collectAsStateWithLifecycle()
    // Typographie locale : police et taille choisies (les contenus lisent MaterialTheme.typography).
    val baseTypography = MaterialTheme.typography
    val font = state.style.font
    val textScale = state.style.textScale
    val typography = remember(baseTypography, font, textScale) {
        islandTypography(baseTypography, IslandFonts.family(font), textScale)
    }
    // Accessibilité : contraste élevé (réglage ou « Texte à contraste élevé » du système).
    val a11ySettings by remember(controller) {
        controller.settings.map { it.accessibility }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(controller.settings.value.accessibility)
    val systemContrast by rememberSystemHighContrast()
    val highContrast = a11ySettings.highContrastActive(systemContrast)
    val baseScheme = bulleColorScheme(dark = true)
    val scheme = remember(baseScheme, highContrast) { if (highContrast) highContrastIslandScheme(baseScheme) else baseScheme }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Interrupteur « Retour haptique » : les contenus (modules) qui passent par
        // LocalHapticFeedback deviennent silencieux quand il est coupé.
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val islandHaptics = remember(haptics, state.motion.haptics) {
            if (state.motion.haptics) haptics else NoHaptics
        }
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            androidx.compose.ui.platform.LocalHapticFeedback provides islandHaptics,
        ) {
            IslandSurface(state, controller, a11ySettings, highContrast)
        }
    }
}

@Composable
private fun IslandSurface(
    state: IslandUiState,
    controller: IslandController,
    a11ySettings: AccessibilitySettings = AccessibilitySettings(),
    highContrast: Boolean = false,
) {
    val g = state.geometry
    if (g.width == 0) return
    val density = LocalDensity.current
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    val motion = remember(state.motion, reduceMotion) { IslandMotion(state.motion, reduceMotion) }

    val mode = when {
        !state.visible -> Mode.HIDDEN
        state.expanded -> Mode.EXPANDED
        state.primary != null -> Mode.COMPACT
        else -> Mode.IDLE
    }

    // --- Style ---
    val style = state.style
    val scheme = MaterialTheme.colorScheme
    val fill = islandFill(style, scheme)
    // Contraste élevé : fin contour clair (1 dp) autour de l'île, sauf si un contour est déjà choisi.
    val hcOutline = highContrast && a11ySettings.islandOutline && !style.outline
    val outlineOn = style.outline || hcOutline
    val outlineColor = if (hcOutline) Color.White.copy(alpha = 0.9f) else scheme.primary
    val outlinePx = when {
        style.outline -> with(density) { style.outlineWidthDp.dp.toPx() }
        hcOutline -> with(density) { 1.dp.toPx() }
        else -> 0f
    }
    val topFrac by animateFloatAsState(style.shape.topRadius, spring(dampingRatio = 0.7f), label = "shape")
    val bottomFrac by animateFloatAsState(style.shape.bottomRadius, spring(dampingRatio = 0.7f), label = "shape")
    val smoothing by animateFloatAsState(style.cornerSmoothing.coerceIn(0f, 1f), spring(dampingRatio = 0.8f), label = "smoothing")

    // Hauteur mesurée du contenu étendu (padding compris).
    var expandedContentHeight by remember { mutableIntStateOf(0) }

    val px = with(density) {
        object {
            val margin = (if (style.cardStyle == CardStyle.WIDE) 6.dp else ExpandedMargin).toPx()
            val maxW = if (style.cardStyle == CardStyle.WIDE) Float.MAX_VALUE else ExpandedMaxWidth.toPx()
            val corner = ExpandedCorner.toPx()
            val shadow = ShadowMargin.toPx()
            val rawSlot = (state.primary?.compactSlotWidth ?: 0.dp).toPx() * state.style.size.coerceIn(0.5f, 1.6f)
        }
    }
    // Largeur maximale en compact : les emplacements (et leur contenu) rétrécissent pour tenir.
    val maxCompactPx = g.screenWidth * style.maxCompactWidth.coerceIn(0.2f, 1f)
    val slotFit = if (px.rawSlot > 0f) ((maxCompactPx - g.width) / 2f / px.rawSlot).coerceIn(0.35f, 1f) else 1f
    val slotPx = px.rawSlot * slotFit
    val compactScale = style.size.coerceIn(0.5f, 1.6f) * slotFit
    val expandedWidth = min(min(g.screenWidth - 2 * px.margin, px.maxW), g.screenWidth * style.maxExpandedWidth.coerceIn(0.3f, 1f))
    val compactWidth = g.width + 2 * slotPx
    // Sens d'ouverture : le bord côté caméra reste fixe et l'île s'étend du côté choisi.
    val direction = g.openDirection

    val target = when (mode) {
        Mode.HIDDEN -> SurfaceTarget(mode, g.width.toFloat(), g.height.toFloat(), g.height / 2f, 0f, 0f)
        // Au repos, l'île peut être invisible (alpha 0) tout en restant touchable (mode ≠ HIDDEN).
        Mode.IDLE -> SurfaceTarget(mode, g.width.toFloat(), g.height.toFloat(), g.height / 2f, 0f, if (style.hideWhenIdle) 0f else 1f)
        Mode.COMPACT -> {
            val dx = IslandLayout.openingOffsetX(
                direction, g.width.toFloat(), compactWidth, g.centerX.toFloat(), g.screenWidth.toFloat(),
                margin = 0f, clampToScreen = direction != OpenDirection.CENTER,
            )
            SurfaceTarget(mode, compactWidth, g.height.toFloat(), g.height / 2f, dx, 1f)
        }
        Mode.EXPANDED -> {
            val h = if (expandedContentHeight > 0) expandedContentHeight.toFloat() else g.height * 4f
            // Carte calée sur la caméra (selon le sens d'ouverture), mais jamais hors de l'écran.
            val dx = IslandLayout.openingOffsetX(
                direction, g.width.toFloat(), expandedWidth, g.centerX.toFloat(), g.screenWidth.toFloat(),
                margin = px.margin, clampToScreen = true,
            )
            SurfaceTarget(mode, expandedWidth, max(h, g.height * 2f), px.corner, dx, 1f)
        }
    }

    val anim = remember { SurfaceAnim(target) }
    val currentController by rememberUpdatedState(controller)

    // TalkBack : la fenêtre d'affichage du rendu stable ne capte aucun toucher, et le système
    // ne présente pas aux services d'accessibilité les fenêtres non touchables. Pendant
    // l'exploration tactile, l'île passe donc en rendu classique (une seule fenêtre ajustée).
    val context = androidx.compose.ui.platform.LocalContext.current
    val touchExploration by remember(context) { SystemAccessibility.touchExploration(context) }.collectAsStateWithLifecycle()
    val stable = style.stableWindow && !touchExploration

    // --- Éclat : la fenêtre s'élargit le temps que les bulles s'envolent ---
    val burstPad = with(density) { 44.dp.toPx() }.toInt()
    val burstState = remember { BurstState() }
    fun applyWin(r: WindowRequest) {
        burstState.lastRequest = r
        currentController.applyWindow(
            // En rendu stable, la fenêtre d'affichage a déjà la place : la zone tactile reste celle de l'île.
            if (burstState.active && !stable) r.copy(width = r.width + 2 * burstPad, height = r.height + burstPad, touchable = r.touchable) else r,
        )
    }

    // --- Bulle secondaire (détachée à droite de la principale, en mode compact) ---
    val secondary = state.secondary.takeIf { mode == Mode.COMPACT }
    var lastSecondary by remember { mutableStateOf<IslandContent?>(null) }
    if (secondary != null) lastSecondary = secondary
    val secR = g.height / 2f
    val secGap = with(density) { 6.dp.toPx() }
    // Visible : à droite, avec un petit espace. Cachée : rentrée dans l'extrémité droite de la principale.
    // En compact, elle suit le décalage du sens d'ouverture (nul en mode centré).
    val secBase = if (mode == Mode.COMPACT) target.offsetX else 0f
    val secTarget = if (secondary != null) Offset(secBase + target.width / 2 + secGap + secR, secR)
    else Offset(secBase + max(0f, target.width / 2 - secR), 0f)
    val secOffset = remember { Animatable(secTarget.x) }
    val secRadius = remember { Animatable(secTarget.y) }

    LaunchedEffect(target, secTarget, motion) {
        val secExtent = { settled: Boolean ->
            if (settled) secTarget.x + secTarget.y else max(secOffset.value + secRadius.value, secTarget.x + secTarget.y) * (1f + motion.overshoot / 4)
        }
        // 1) La fenêtre englobe l'état courant ET la cible (+ dépassement du ressort).
        applyWin(envelope(anim, target, motion.overshoot, px.shadow, settled = false, secExtent(false)))
        // 2) Animation au ressort : tout ensemble.
        val growing = target.width * target.height >= anim.width.value * anim.height.value
        val spec = if (growing) motion.grow() else motion.shrink()
        coroutineScope {
            launch { anim.width.animateTo(target.width, spec) }
            launch { anim.height.animateTo(target.height, spec) }
            launch { anim.corner.animateTo(target.corner, spec) }
            launch { anim.offsetX.animateTo(target.offsetX, spec) }
            launch { anim.alpha.animateTo(target.alpha, motion.fade()) }
            // Scission liquide : la goutte s'éloigne en grossissant, ou revient se fondre.
            launch { secOffset.animateTo(secTarget.x, if (secTarget.y > 0f) motion.detach() else motion.shrink()) }
            launch { secRadius.animateTo(secTarget.y, if (secTarget.y > 0f) motion.grow() else motion.merge()) }
        }
        // 3) Une fois posée, la fenêtre se resserre exactement sur l'île.
        applyWin(envelope(anim, target, 0f, px.shadow, settled = true, secExtent(true)))
    }

    // Retour haptique discret quand l'île s'ouvre ou se ferme (désactivable dans Style).
    val haptics = state.motion.haptics
    var lastMode by remember { mutableIntStateOf(mode.ordinal) }
    LaunchedEffect(mode) {
        val was = Mode.entries[lastMode]
        if (haptics && (was == Mode.EXPANDED) != (mode == Mode.EXPANDED) && was != Mode.HIDDEN && mode != Mode.HIDDEN) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        lastMode = mode.ordinal
    }

    // Éclat à chaque alerte ponctuelle, reflet irisé à chaque changement de contenu.
    val burstAnim = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }
    val primary = state.primary
    val accent = primary?.accent ?: scheme.primary
    LaunchedEffect(primary?.key) {
        if (reduceMotion || primary == null || mode == Mode.HIDDEN) return@LaunchedEffect
        launch {
            if (style.shimmer) {
                shimmer.snapTo(0f)
                shimmer.animateTo(1f, tween((900 / state.motion.speed.coerceIn(0.5f, 2f)).toInt(), easing = LinearEasing))
            }
        }
        if (primary.priority != Priority.ALERT || primary.timeoutMs == null) return@LaunchedEffect
        burstState.burst = Burst(primary.key.hashCode() xor primary.postedAt.toInt(), accent)
        try {
            burstState.active = true
            burstState.lastRequest?.let(::applyWin)
            burstAnim.snapTo(0f)
            burstAnim.animateTo(1f, tween(1300, easing = LinearEasing))
        } finally {
            burstState.active = false
            burstState.burst = null
            burstState.lastRequest?.let(::applyWin)
        }
    }

    // Animations d'entrée par type de contenu (packs de styles) : jouées une fois par arrivée.
    val entryTracker = remember { EntryTracker() }
    entryTracker.onPrimary(primary?.key)
    val entryFor = { c: IslandContent? -> EntryAnimations.resolve(c, state.motion, reduceMotion) }

    val compactAlpha = remember { Animatable(0f) }
    val expandedAlpha = remember { Animatable(0f) }
    // Lus en composition : derivedStateOf évite de recomposer toute l'île à chaque image des fondus.
    val compactShown by remember { derivedStateOf { compactAlpha.value > 0f } }
    val expandedShown by remember { derivedStateOf { expandedAlpha.value > 0f } }
    val secondaryShown by remember { derivedStateOf { secRadius.value > 0.5f } }
    LaunchedEffect(mode, motion) {
        launch { compactAlpha.animateTo(if (mode == Mode.COMPACT) 1f else 0f, if (mode == Mode.COMPACT) motion.contentIn() else motion.contentOut()) }
        launch { expandedAlpha.animateTo(if (mode == Mode.EXPANDED) 1f else 0f, if (mode == Mode.EXPANDED) motion.contentIn() else motion.contentOut()) }
    }

    // Changement de mode de rendu (TalkBack activé / coupé) : replacer la fenêtre tout de suite.
    LaunchedEffect(stable) { burstState.lastRequest?.let(::applyWin) }

    // Sémantique TalkBack : résumé, état, actions principales, annonces limitées.
    val a11y = rememberIslandA11y(
        primary = primary.takeIf { mode == Mode.COMPACT || mode == Mode.EXPANDED },
        panel = state.panel.takeIf { mode == Mode.EXPANDED },
        expanded = mode == Mode.EXPANDED,
        hasSecondary = secondary != null,
        level = a11ySettings.announcements,
        host = controller,
        onToggleExpand = controller::toggleExpand,
        onSwap = controller::swapBubbles,
    )
    val idleLabel = if (mode == Mode.IDLE && !style.hideWhenIdle) androidx.compose.ui.res.stringResource(com.karelisio.bulle.R.string.a11y_island_name) else null
    // Texte agrandi (échelle de police système) : la carte étendue est plafonnée en hauteur et défile.
    val fontScale = density.fontScale
    val bigText = fontScale >= 1.5f
    val expandedScroll = androidx.compose.foundation.rememberScrollState()
    val expandedMaxHeight = with(density) { (view.resources.displayMetrics.heightPixels * 0.8f).toDp() }

    // Rendu stable : la fenêtre couvre toute la largeur depuis le haut de l'écran ; on place
    // donc l'île sur la caméra par un décalage (la fenêtre, elle, ne bouge jamais).
    Box(
        Modifier
            .fillMaxSize()
            .then(if (stable) Modifier.offset { IntOffset(g.centerX - g.screenWidth / 2, g.top) } else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Fond noir « liquide » commun aux deux bulles (Android 13+).
        LiquidBackground(smoothK = with(density) { 10.dp.toPx() }) {
            LiquidGeometry(
                fill = fill,
                outline = outlineColor,
                outlineWidth = outlinePx,
                topCorner = anim.corner.value * topFrac,
                mainCenterX = size.width / 2 + anim.offsetX.value,
                mainCenterY = anim.height.value / 2,
                mainHalfW = anim.width.value / 2,
                mainHalfH = anim.height.value / 2,
                mainCorner = anim.corner.value * bottomFrac,
                secCenterX = size.width / 2 + secOffset.value,
                secCenterY = secR,
                secRadius = secRadius.value,
                alpha = anim.alpha.value,
                smoothing = smoothing,
            )
        }
        Box(
            Modifier
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .layout { measurable, _ ->
                    val w = anim.width.value.roundToInt().coerceAtLeast(1)
                    val h = anim.height.value.roundToInt().coerceAtLeast(1)
                    val p = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { p.place(0, 0) }
                }
                .graphicsLayer {
                    translationX = anim.offsetX.value
                    alpha = anim.alpha.value
                    val c = anim.corner.value.coerceAtLeast(0f)
                    shape = islandShape(c * topFrac, c * bottomFrac, smoothing)
                    clip = true
                    shadowElevation = if (mode == Mode.EXPANDED || (style.shadow && mode != Mode.HIDDEN)) 8.dp.toPx() else 0f
                }
                .then(if (LiquidSupported) Modifier else Modifier.background(fill))
                .drawWithContent {
                    drawContent()
                    drawShimmer(shimmer.value, accent)
                }
                .then(
                    if (!LiquidSupported && outlineOn) {
                        val borderShape = if (style.cornerSmoothing > CornerSmoothing.Epsilon) {
                            SmoothCornerShape(g.height / 2f * style.shape.topRadius, g.height / 2f * style.shape.bottomRadius, style.cornerSmoothing)
                        } else RoundedCornerShape(50)
                        Modifier.border(with(density) { outlinePx.toDp() }, outlineColor, borderShape)
                    } else Modifier,
                )
                .islandGestures(enabled = mode != Mode.HIDDEN, doubleTap = state.doubleTapEnabled, onGesture = controller::onGesture)
                .islandSemantics(
                    a11y,
                    visible = mode != Mode.HIDDEN,
                    compact = mode == Mode.COMPACT,
                    idleLabel = idleLabel,
                    onGesture = controller::onGesture,
                ),
        ) {
            // Vue compacte : emplacements de part et d'autre de la caméra.
            if (mode == Mode.COMPACT || compactShown) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .graphicsLayer { alpha = compactAlpha.value }
                        // Résumé fourni : le contenu compact est décoratif pour TalkBack.
                        .then(if (a11y.label != null || mode != Mode.COMPACT) Modifier.clearAndSetSemantics { } else Modifier),
                ) {
                    AnimatedContent(
                        targetState = state.primary,
                        contentKey = { it?.key },
                        transitionSpec = {
                            // Gonflement (défaut) : transition historique ; sinon l'entrée est jouée par EntryAnimated.
                            val enter = if (entryFor(targetState) == EntryAnimation.INFLATE) {
                                fadeIn(tween(220, 90)) + scaleIn(tween(260, 60), initialScale = 0.85f)
                            } else EnterTransition.None
                            enter
                                .togetherWith(fadeOut(tween(90)))
                                .using(SizeTransform(clip = false))
                        },
                        contentAlignment = Alignment.TopCenter,
                        label = "compact",
                    ) { content ->
                        if (content != null) {
                            val entry = remember { if (entryTracker.consume(content.key)) entryFor(content) else EntryAnimation.INFLATE }
                            EntryAnimated(entry, content.accent ?: scheme.primary, state.motion.speed) {
                            CompactFontScale(g.height, density) {
                            CompactRow(
                                content, g.width, g.height, density, compactScale,
                                direction = direction,
                                marquee = style.marquee && !reduceMotion,
                                subtitle = content.compactSubtitle
                                    ?.takeIf { style.twoLineCompact && IslandLayout.twoLineFits(g.height, density.density, style.textScale) },
                            )
                            }
                            }
                        }
                    }
                }
            }
            // Vue étendue : contenu sous la caméra, en fondu décalé avec flou (désactivable dans Style).
            val blurOn = state.motion.blurTransitions
            if (mode == Mode.EXPANDED || expandedShown) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .requiredWidth(with(density) { expandedWidth.toDp() })
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .then(if (bigText) Modifier.heightIn(max = expandedMaxHeight) else Modifier)
                        .onSizeChanged { expandedContentHeight = it.height }
                        .graphicsLayer {
                            val a = expandedAlpha.value
                            alpha = a
                            val s = 0.94f + 0.06f * a
                            scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            val blur = if (blurOn) (1f - a) * 12.dp.toPx() else 0f
                            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                        }
                        .then(if (bigText) Modifier.verticalScroll(expandedScroll) else Modifier)
                        .padding(top = with(density) { g.height.toDp() } + 4.dp, bottom = 18.dp, start = 18.dp, end = 18.dp),
                ) {
                    val panel = state.panel
                    when {
                        panel != null -> panel.Content(controller)
                        else -> state.primary?.Expanded(controller)
                    }
                }
            }
        }
        // Bulle secondaire : contenu minimal, toucher = échanger les deux bulles.
        SecondaryBubble(
            content = lastSecondary.takeIf { secondaryShown || secondary != null },
            fill = fill,
            offsetX = { secOffset.value },
            radius = { secRadius.value },
            fullRadius = secR,
            onTap = controller::swapBubbles,
        )
        // Éclat : bulles irisées autour de l'île.
        Canvas(Modifier.fillMaxSize()) {
            val b = burstState.burst ?: return@Canvas
            b.draw(this, burstAnim.value, size.width / 2 + anim.offsetX.value, 0f, anim.width.value, anim.height.value, density.density)
        }
    }
}

/** État de l'éclat en cours (lu hors composition). */
private class BurstState {
    var active = false
    var burst: Burst? = null
    var lastRequest: WindowRequest? = null
}

@Composable
private fun SecondaryBubble(
    content: IslandContent?,
    fill: Color,
    offsetX: () -> Float,
    radius: () -> Float,
    fullRadius: Float,
    onTap: () -> Unit,
) {
    if (content == null) return
    val density = LocalDensity.current
    val d = with(density) { (fullRadius * 2).toDp() }
    Box(
        Modifier
            .size(d)
            .graphicsLayer {
                translationX = offsetX()
                val p = (radius() / fullRadius).coerceIn(0f, 1.2f)
                scaleX = p
                scaleY = p
                // Le contenu n'apparaît qu'une fois la goutte détachée.
                alpha = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                shape = CircleShape
                clip = true
            }
            .then(if (LiquidSupported) Modifier else Modifier.background(fill))
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .secondarySemantics(content, onTap),
        contentAlignment = Alignment.Center,
    ) {
        content.Minimal()
    }
}

/** Couleur de fond de l'île selon le style (noir pur par défaut pour se fondre avec la caméra). */
fun islandFill(style: StyleSettings, darkScheme: androidx.compose.material3.ColorScheme): Color = when (style.colorMode) {
    IslandColorMode.BLACK -> Color.Black
    IslandColorMode.DYNAMIC -> androidx.compose.ui.graphics.lerp(darkScheme.primaryContainer, Color.Black, 0.55f)
    IslandColorMode.CUSTOM -> Color(style.customColor.toInt()).copy(alpha = 1f)
}

/**
 * Ligne compacte. Centrée : [Leading] | caméra | [Trailing]. Vers la gauche : [Leading][Trailing] | caméra.
 * Vers la droite : caméra | [Leading][Trailing]. [subtitle] : petite seconde ligne sous [Trailing].
 */
@Composable
private fun CompactRow(
    content: IslandContent,
    baseWidthPx: Int,
    heightPx: Int,
    density: androidx.compose.ui.unit.Density,
    scale: Float,
    direction: OpenDirection = OpenDirection.CENTER,
    marquee: Boolean = false,
    subtitle: String? = null,
) {
    with(density) {
        // Défilement des textes trop longs : l'enfant est mesuré sans limite puis défile dans l'emplacement.
        val scroll = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_200) else Modifier
        val camera = @Composable { Spacer(Modifier.width(baseWidthPx.toDp())) }
        val leading = @Composable {
            // Les emplacements suivent la taille choisie : le contenu est dessiné à l'échelle.
            Box(Modifier.width(content.compactSlotWidth * scale), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .requiredWidth(content.compactSlotWidth)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { MarqueeSlot(scroll, marquee) { content.Leading() } }
            }
        }
        val trailing = @Composable {
            Box(Modifier.width(content.compactSlotWidth * scale), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .requiredWidth(content.compactSlotWidth)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (subtitle == null) {
                        MarqueeSlot(scroll, marquee) { content.Trailing() }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            MarqueeSlot(scroll, marquee) { content.Trailing() }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                                modifier = scroll,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.height(heightPx.toDp()), verticalAlignment = Alignment.CenterVertically) {
            when (direction) {
                OpenDirection.CENTER -> { leading(); camera(); trailing() }
                OpenDirection.LEFT -> { leading(); trailing(); camera() }
                OpenDirection.RIGHT -> { camera(); leading(); trailing() }
            }
        }
    }
}

/**
 * Échelle de police appliquée au contenu compact : celle du système, plafonnée pour qu'une ligne
 * tienne dans la hauteur de l'île (au-delà, défilement ou points de suspension sur la largeur).
 * Échelle ≤ 1,3 sur une île de hauteur courante : inchangée.
 */
internal fun compactFontScale(heightPx: Int, density: androidx.compose.ui.unit.Density): Float {
    val heightDp = heightPx / density.density.coerceAtLeast(0.1f)
    val max = (heightDp / 26f).coerceAtLeast(1f)
    return density.fontScale.coerceIn(0.5f, max)
}

@Composable
private fun CompactFontScale(heightPx: Int, density: androidx.compose.ui.unit.Density, content: @Composable () -> Unit) {
    val scale = compactFontScale(heightPx, density)
    if (scale == density.fontScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, scale), content = content)
    }
}

/** Enveloppe de défilement : sans défilement, le contenu est émis tel quel (mise en page inchangée). */
@Composable
private fun MarqueeSlot(scroll: Modifier, enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) Box(scroll) { content() } else content()
}

/** Taille de fenêtre qui contient l'île courante et sa cible, symétrique autour de la caméra. */
private fun envelope(anim: SurfaceAnim, t: SurfaceTarget, overshoot: Float, shadow: Float, settled: Boolean, secExtent: Float): WindowRequest {
    fun span(cur: Float, tgt: Float) = max(cur, tgt) + abs(tgt - cur) * overshoot
    val halfW = if (settled) abs(t.offsetX) + t.width / 2 else max(
        abs(anim.offsetX.value) + anim.width.value / 2,
        abs(t.offsetX) + t.width / 2,
    ) + abs(t.width - anim.width.value) * overshoot / 2 +
        // Dépassement du décalage (sens d'ouverture gauche/droite) : nul quand le décalage ne change pas.
        abs(t.offsetX - anim.offsetX.value) * overshoot
    var w = 2 * max(halfW, secExtent)
    var h = if (settled) t.height else span(anim.height.value, t.height)
    val expanded = t.mode == Mode.EXPANDED || anim.height.value > t.height + 1f
    if (expanded) {
        w += 2 * shadow
        h += shadow
    }
    return WindowRequest(
        width = w.roundToInt() + 2,
        height = h.roundToInt() + 2,
        touchable = t.mode != Mode.HIDDEN,
        watchOutside = t.mode == Mode.EXPANDED,
    )
}

/** Reconnaissance des gestes sur l'île : toucher, appui long et glissements. */
private fun Modifier.islandGestures(enabled: Boolean, doubleTap: Boolean, onGesture: (IslandGesture) -> Unit): Modifier {
    if (!enabled) return this
    return this
        .pointerInput(doubleTap) {
            detectTapGestures(
                onTap = { onGesture(IslandGesture.TAP) },
                onDoubleTap = if (doubleTap) ({ onGesture(IslandGesture.DOUBLE_TAP) }) else null,
                onLongPress = { onGesture(IslandGesture.LONG_PRESS) },
            )
        }
        .pointerInput(Unit) {
            var total = Offset.Zero
            val threshold = 24.dp.toPx()
            detectDragGestures(
                onDragStart = { total = Offset.Zero },
                onDrag = { change, amount -> change.consume(); total += amount },
                onDragEnd = {
                    val g = when {
                        abs(total.x) > abs(total.y) && total.x > threshold -> IslandGesture.SWIPE_RIGHT
                        abs(total.x) > abs(total.y) && total.x < -threshold -> IslandGesture.SWIPE_LEFT
                        total.y > threshold -> IslandGesture.SWIPE_DOWN
                        total.y < -threshold -> IslandGesture.SWIPE_UP
                        else -> null
                    }
                    g?.let(onGesture)
                },
            )
        }
}

/** Retour haptique neutre (interrupteur « Retour haptique » coupé). */
private object NoHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) = Unit
}
