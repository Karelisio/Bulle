package com.karelisio.bulle.island

import android.content.Context
import com.karelisio.bulle.core.actions.ActionExecutor
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.island.Arrangement
import com.karelisio.bulle.core.island.ContentHub
import com.karelisio.bulle.core.island.IslandArbiter
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.Panels
import com.karelisio.bulle.modules.rules.RuleEffects
import com.karelisio.bulle.modules.rules.RuleEngine
import com.karelisio.bulle.modules.search.AppSearchPanel
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.settings.StyleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tout ce que la vue de l'île doit savoir pour se dessiner. */
data class IslandUiState(
    val geometry: IslandGeometry = IslandGeometry.Empty,
    val visible: Boolean = false,
    val primary: IslandContent? = null,
    val secondary: IslandContent? = null,
    val expanded: Boolean = false,
    val panel: IslandPanel? = null,
    val motion: MotionSettings = MotionSettings(),
    /** Le double toucher n'est détecté que s'il a une action (sinon le toucher simple serait retardé). */
    val doubleTapEnabled: Boolean = false,
    val style: StyleSettings = StyleSettings(),
    /** Plusieurs contenus : position du contenu affiché et nombre total (points de pagination). */
    val page: Pair<Int, Int> = 0 to 1,
)

/** Ce que l'utilisateur a fait de l'île (étendue, échange des bulles, panneau ouvert). */
private data class Interaction(
    val expandedKey: String? = null,
    val panel: IslandPanel? = null,
    val swappedPair: Pair<String, String>? = null,
    /** Contenu choisi en balayant l'île (clé, instant). */
    val preferred: Pair<String, Long>? = null,
)

/** Demande de dimensionnement de la fenêtre de l'île (pixels). */
data class WindowRequest(
    val width: Int,
    val height: Int,
    val touchable: Boolean,
    val watchOutside: Boolean,
    /** Haut de l'enveloppe par rapport au haut de l'île au repos (île verticale du paysage ; 0 sinon). */
    val offsetY: Int = 0,
)

/**
 * Gestionnaire d'état central de l'île : agrège les contenus des modules,
 * applique la file de priorité, le masquage automatique et les interactions.
 */
/** Raison du masquage en cours, pour le diagnostic (null = visible). */
private val _hiddenReason = MutableStateFlow<HiddenReason?>(null)
val islandHiddenReason: StateFlow<HiddenReason?> = _hiddenReason

class IslandController(
    override val context: Context,
    private val scope: CoroutineScope,
    private val window: IslandWindow,
) : IslandHost {

    /** Réglages enregistrés. */
    val storedSettings: StateFlow<BulleSettings> =
        SettingsRepository.flow(context).stateIn(scope, SharingStarted.Eagerly, BulleSettings())

    private val ruleEffects = MutableStateFlow(RuleEffects())

    /** Réglages effectifs (avec les effets des règles automatiques actives). */
    val settings: StateFlow<BulleSettings> = combine(storedSettings, ruleEffects) { s, e ->
        e.applyTo(s)
    }.stateIn(scope, SharingStarted.Eagerly, BulleSettings())

    private val rules = RuleEngine(
        context = context,
        scope = scope,
        rules = storedSettings.map { it.rules.rules }.stateIn(scope, SharingStarted.Eagerly, emptyList()),
        onEffects = { e ->
            ruleEffects.value = e
            screen.update { it.copy(ruleVisibility = e.visibility) }
        },
        onRun = { runAction(it) },
    )

    private val hub = ContentHub(ModuleEnv(context, scope, settings))

    /** Contenus actifs de tous les modules (Halo : pochette ; Veille : activités en cours). */
    val contents: StateFlow<List<IslandContent>> get() = hub.contents
    private val screen = MutableStateFlow(ScreenEnv())

    /** État de l'écran (géométrie de l'île, plein écran, verrouillage), lu par le Volet. */
    val screenEnv: StateFlow<ScreenEnv> get() = screen
    private val interaction = MutableStateFlow(Interaction())

    private val arrangement: StateFlow<Arrangement> =
        combine(hub.contents, interaction) { list, i -> IslandArbiter.arrange(list, i.swappedPair, preferred = i.preferred) }
            .stateIn(scope, SharingStarted.Eagerly, Arrangement.Empty)

    private val foreground = combine(ForegroundApp.packageName, IslandOcclusion.active) { fg, occ -> fg to occ.isNotEmpty() }

    val state: StateFlow<IslandUiState> = combine(
        settings, screen, foreground, arrangement, interaction,
    ) { s, scr, (fg, occluded), arr, i ->
        val reason = VisibilityPolicy.hiddenReason(s, scr, fg, HomeApp.packageName(context))
        if (_hiddenReason.value != reason) {
            IslandLog.log(if (reason == null) "Île affichée" else "Île masquée : $reason" + (fg?.let { " ($it)" } ?: ""))
        }
        _hiddenReason.value = reason
        val policyVisible = reason == null
        // Contenu prioritaire (appel, paroles…) : passe au-dessus du masquage automatique,
        // mais pas de l'île désactivée ni du choix « masquer sur l'écran de verrouillage ».
        val forced = s.islandEnabled && arr.primary?.showWhenHidden == true &&
            reason != HiddenReason.DISABLED && reason != HiddenReason.LOCKSCREEN
        val primary = arr.primary
        val expanded = i.panel != null || (primary != null && primary.expandable && i.expandedKey == primary.key)
        IslandUiState(
            geometry = scr.geometry,
            visible = (policyVisible || forced) && !occluded,
            primary = primary,
            secondary = arr.secondary.takeIf { policyVisible },
            expanded = expanded,
            panel = i.panel,
            motion = s.motion,
            doubleTapEnabled = s.gestures.doubleTap.type != ActionType.NONE,
            // Paysage natif : île invisible au repos autour de la caméra, mais active.
            style = if (scr.landscape && s.visibility.landscapeNative) s.style.copy(hideWhenIdle = true) else s.style,
            page = IslandArbiter.position(hub.contents.value, primary?.key),
        )
    }.stateIn(scope, SharingStarted.Eagerly, IslandUiState())

    private var autoCollapseJob: Job? = null
    private val seenKeys = HashSet<String>()

    init {
        rules.start()
        // Extension automatique à l'arrivée d'un contenu qui la demande (alerte, appel…).
        scope.launch {
            arrangement.map { it.primary }.collect { primary ->
                val all = hub.contents.value.mapTo(HashSet()) { it.key }
                seenKeys.retainAll(all)
                if (primary == null || !seenKeys.add(primary.key)) return@collect
                // L'île gonfle pour ce nouveau contenu : le Halo démarre au même instant.
                if (state.value.visible) IslandPulses.emit(primary)
                val ms = primary.autoExpandMs ?: return@collect
                if (interaction.value.panel != null) return@collect
                // Paysage (île verticale) : seuls les appels s'ouvrent d'eux-mêmes.
                if (!IslandAutoExpand.allowed(screen.value.geometry.vertical, primary.moduleId, settings.value.visibility.landscapeCalls)) return@collect
                interaction.update { it.copy(expandedKey = primary.key) }
                autoCollapseJob?.cancel()
                autoCollapseJob = scope.launch {
                    delay(ms)
                    interaction.update { if (it.expandedKey == primary.key) it.copy(expandedKey = null) else it }
                }
            }
        }
    }

    // --- Entrées de l'environnement ---

    /** Dernière taille de fenêtre demandée par la vue, pour la replacer si la géométrie bouge. */
    private var lastWindow: WindowRequest? = null

    fun updateScreen(transform: (ScreenEnv) -> ScreenEnv) {
        val before = screen.value.geometry
        screen.update(transform)
        // La vue ne redemande une fenêtre que si la taille cible change : un simple déplacement
        // (réglage fin X/Y, rotation à 180°) laisserait la fenêtre à l'ancienne position.
        if (screen.value.geometry != before) lastWindow?.let(::applyWindow)
    }

    fun applyWindow(request: WindowRequest) {
        lastWindow = request
        val g = screen.value.geometry
        // TalkBack : rendu classique (voir IslandScene), la fenêtre d'affichage stable n'étant pas touchable.
        window.stable = settings.value.style.stableWindow &&
            !com.karelisio.bulle.core.a11y.SystemAccessibility.isTouchExplorationEnabled(context)
        val top = (g.top + request.offsetY).coerceAtLeast(0)
        window.update(
            g.centerX, top, request.width, request.height, request.touchable, request.watchOutside,
            screenWidth = g.screenWidth.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
            screenHeight = context.resources.displayMetrics.heightPixels,
        )
        // Enveloppe tactile réelle de l'île : les zones du Volet s'y collent sans trou.
        com.karelisio.bulle.modules.volet.VoletStrips.islandEnvelope.value = com.karelisio.bulle.modules.volet.IslandEnvelope(
            g.centerX - request.width.coerceAtLeast(1) / 2, top,
            request.width.coerceAtLeast(1), request.height.coerceAtLeast(1), request.touchable,
        )
    }

    // --- Gestes ---

    private val executor = ActionExecutor(
        context,
        object : ActionExecutor.IslandActions {
            override fun toggleExpand() = this@IslandController.toggleExpand()
            override fun swapBubbles() = this@IslandController.swapBubbles()
            override fun openAppSearch() = openPanel(AppSearchPanel)
            override fun openDeck() {
                val deck = Panels.deck
                if (deck != null) openPanel(deck) else toggleExpand()
            }
            override fun openTimer() = openPanel(com.karelisio.bulle.modules.focus.FocusPanel)
            override fun openTranslate() = openPanel(com.karelisio.bulle.modules.translate.TranslatePanel)
            override fun cycleContent(direction: Int): Boolean = this@IslandController.cycleContent(direction)
            override fun openCraving() {
                com.karelisio.bulle.modules.ecosystem.Ecosystem.openCraving(context) { openPanel(it) }
            }
        },
    )

    fun onGesture(gesture: IslandGesture) {
        autoCollapseJob?.cancel()
        val st = state.value
        // Île étendue, plusieurs contenus : balayer de côté passe au suivant / précédent.
        if (st.expanded && st.panel == null && (gesture == IslandGesture.SWIPE_LEFT || gesture == IslandGesture.SWIPE_RIGHT) &&
            cycleContent(if (gesture == IslandGesture.SWIPE_LEFT) 1 else -1, keepExpanded = true)
        ) return
        // Gestes contextuels quand l'île est étendue.
        if (gesture == IslandGesture.SWIPE_UP) {
            if (st.expanded) { closePanel(); collapse() }
            return
        }
        // Appui long sur un contenu issu d'une notification : ouvre le Volet dessus.
        if (gesture == IslandGesture.LONG_PRESS) {
            val key = st.primary?.notificationKey
            val volet = com.karelisio.bulle.modules.volet.VoletController.current.value
            if (key != null && volet != null && volet.settings.enabled) {
                closePanel()
                collapse()
                volet.openFor(key)
                return
            }
        }
        if (st.expanded && gesture == IslandGesture.TAP) {
            when {
                st.panel != null -> closePanel()
                st.primary?.onExpandedTap(this) != true -> collapse()
            }
            return
        }
        val action = settings.value.gestures.actionFor(gesture) ?: return
        executor.run(action)
    }

    /** Exécute une action (gestes, règles automatiques, Deck). */
    /**
     * Action lancée depuis le Deck ou un panneau. Celles qui ouvrent un autre écran (caméra QR,
     * appli, réglages, enregistrement…) referment d'abord l'île pour ne pas rester devant ; une
     * capture d'écran attend la fin de la fermeture pour ne pas photographier le Deck.
     */
    override fun runAction(action: GestureAction) {
        if (!ActionExecutor.leavesIsland(action.type)) return executor.run(action)
        closePanel()
        collapse()
        if (action.type == ActionType.SCREENSHOT) {
            scope.launch { kotlinx.coroutines.delay(450); executor.run(action) }
        } else {
            executor.run(action)
        }
    }

    fun toggleExpand() {
        val st = state.value
        when {
            st.expanded -> { closePanel(); collapse() }
            st.primary?.expandable == true -> expand(st.primary.key)
        }
    }

    /** Touche Retour pendant une saisie : ferme le panneau. */
    fun onBack(): Boolean {
        if (!state.value.expanded) return false
        closePanel()
        collapse()
        return true
    }

    /** Saisie en cours (recherche d'applis…) : les touchers sur le clavier arrivent comme « extérieurs ». */
    @Volatile
    private var inputActive = false

    fun onOutsideTouch() {
        // Taper sur le clavier ne doit pas refermer le panneau de saisie.
        if (inputActive) return
        val st = state.value
        // Certains contenus restent ouverts (paroles synchronisées) : seul un toucher sur l'île les ferme.
        if (st.expanded && st.panel == null && st.primary?.stayExpandedOnOutsideTouch == true) return
        if (st.expanded) {
            autoCollapseJob?.cancel()
            interaction.update { it.copy(expandedKey = null, panel = null) }
        }
    }

    // --- IslandHost ---

    override fun expand(key: String) = interaction.update { it.copy(expandedKey = key) }

    override fun collapse() = interaction.update { it.copy(expandedKey = null) }

    override fun dismiss(key: String) {
        hub.dismiss(key)
        interaction.update { if (it.expandedKey == key) it.copy(expandedKey = null) else it }
    }

    override fun openPanel(panel: IslandPanel) = interaction.update { it.copy(panel = panel) }

    override fun closePanel() {
        interaction.update { it.copy(panel = null) }
        inputActive = false
        window.setFocusable(false)
    }

    override fun requestInput(enabled: Boolean) {
        inputActive = enabled
        if (enabled) autoCollapseJob?.cancel()
        window.setFocusable(enabled, showKeyboard = enabled)
    }

    /** À appeler quand l'île s'arrête. */
    fun release() = rules.stop()

    /**
     * Passe au contenu suivant (+1) ou précédent (-1). [keepExpanded] : la vue étendue suit.
     * Retourne faux s'il n'y a qu'un contenu.
     */
    fun cycleContent(direction: Int, keepExpanded: Boolean = state.value.expanded): Boolean {
        val current = arrangement.value.primary ?: return false
        val next = IslandArbiter.cycle(hub.contents.value, current.key, direction) ?: return false
        autoCollapseJob?.cancel()
        val now = android.os.SystemClock.elapsedRealtime()
        interaction.update {
            it.copy(
                preferred = next.key to now,
                swappedPair = null,
                expandedKey = if (keepExpanded && next.expandable) next.key else null,
            )
        }
        IslandLog.log("Île : contenu ${next.moduleId} (balayage)")
        return true
    }

    /** Échange la bulle principale et la secondaire. */
    fun swapBubbles() {
        val arr = arrangement.value
        val p = arr.primary ?: return
        val s = arr.secondary ?: return
        interaction.update {
            // L'échange n'est effectif que si la paire mémorisée est celle affichée (inversée) ;
            // une paire périmée (autre contenu arrivé entre-temps) ne doit pas « consommer » le toucher.
            val swappedNow = it.swappedPair == (s.key to p.key)
            it.copy(swappedPair = if (swappedNow) null else p.key to s.key, expandedKey = null)
        }
    }
}
