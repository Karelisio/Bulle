package com.karelisio.bulle.island

import android.content.Context
import com.karelisio.bulle.core.island.Arrangement
import com.karelisio.bulle.core.island.ContentHub
import com.karelisio.bulle.core.island.IslandArbiter
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.SettingsRepository
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
)

/** Ce que l'utilisateur a fait de l'île (étendue, échange des bulles, panneau ouvert). */
private data class Interaction(
    val expandedKey: String? = null,
    val panel: IslandPanel? = null,
    val swappedPair: Pair<String, String>? = null,
)

/** Demande de dimensionnement de la fenêtre de l'île (pixels). */
data class WindowRequest(
    val width: Int,
    val height: Int,
    val touchable: Boolean,
    val watchOutside: Boolean,
)

/**
 * Gestionnaire d'état central de l'île : agrège les contenus des modules,
 * applique la file de priorité, le masquage automatique et les interactions.
 */
class IslandController(
    override val context: Context,
    private val scope: CoroutineScope,
    private val window: IslandWindow,
) : IslandHost {

    val settings: StateFlow<BulleSettings> =
        SettingsRepository.flow(context).stateIn(scope, SharingStarted.Eagerly, BulleSettings())

    private val hub = ContentHub(ModuleEnv(context, scope, settings))
    private val screen = MutableStateFlow(ScreenEnv())
    private val interaction = MutableStateFlow(Interaction())

    private val arrangement: StateFlow<Arrangement> =
        combine(hub.contents, interaction) { list, i -> IslandArbiter.arrange(list, i.swappedPair) }
            .stateIn(scope, SharingStarted.Eagerly, Arrangement.Empty)

    val state: StateFlow<IslandUiState> = combine(
        settings, screen, ForegroundApp.packageName, arrangement, interaction,
    ) { s, scr, fg, arr, i ->
        val policyVisible = VisibilityPolicy.isVisible(s, scr, fg)
        val forced = s.islandEnabled && arr.primary?.showWhenHidden == true
        val primary = arr.primary
        val expanded = i.panel != null || (primary != null && primary.expandable && i.expandedKey == primary.key)
        IslandUiState(
            geometry = scr.geometry,
            visible = policyVisible || forced,
            primary = primary,
            secondary = arr.secondary.takeIf { policyVisible },
            expanded = expanded,
            panel = i.panel,
            motion = s.motion,
        )
    }.stateIn(scope, SharingStarted.Eagerly, IslandUiState())

    private var autoCollapseJob: Job? = null
    private val seenKeys = HashSet<String>()

    init {
        // Extension automatique à l'arrivée d'un contenu qui la demande (alerte, appel…).
        scope.launch {
            arrangement.map { it.primary }.collect { primary ->
                val all = hub.contents.value.mapTo(HashSet()) { it.key }
                seenKeys.retainAll(all)
                if (primary == null || !seenKeys.add(primary.key)) return@collect
                val ms = primary.autoExpandMs ?: return@collect
                if (interaction.value.panel != null) return@collect
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

    fun updateScreen(transform: (ScreenEnv) -> ScreenEnv) = screen.update(transform)

    fun applyWindow(request: WindowRequest) {
        val g = screen.value.geometry
        window.update(g.centerX, g.top, request.width, request.height, request.touchable, request.watchOutside)
    }

    // --- Gestes ---

    fun onGesture(gesture: IslandGesture) {
        autoCollapseJob?.cancel()
        val st = state.value
        when (gesture) {
            IslandGesture.TAP -> when {
                st.expanded && st.panel != null -> closePanel()
                st.expanded -> if (st.primary?.onExpandedTap(this) != true) collapse()
                st.primary?.expandable == true -> expand(st.primary.key)
            }
            IslandGesture.SWIPE_DOWN -> st.primary?.takeIf { it.expandable && !st.expanded }?.let { expand(it.key) }
            IslandGesture.SWIPE_UP -> if (st.expanded) { closePanel(); collapse() }
            else -> Unit // Actions configurables : étape « gestes ».
        }
    }

    fun onOutsideTouch() {
        if (state.value.expanded) {
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
        window.setFocusable(false)
    }

    override fun requestInput(enabled: Boolean) = window.setFocusable(enabled)

    /** Échange la bulle principale et la secondaire. */
    fun swapBubbles() {
        val arr = arrangement.value
        val p = arr.primary ?: return
        val s = arr.secondary ?: return
        interaction.update {
            val natural = if (it.swappedPair != null) s.key to p.key else p.key to s.key
            it.copy(swappedPair = if (it.swappedPair == null) natural else null, expandedKey = null)
        }
    }
}
