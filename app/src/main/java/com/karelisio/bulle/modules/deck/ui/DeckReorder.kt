package com.karelisio.bulle.modules.deck.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.karelisio.bulle.modules.deck.DeckSection
import com.karelisio.bulle.modules.deck.moved

/**
 * Glisser-déposer « maison » dans une LazyColumn : une section à la fois.
 * Pendant le geste, l'ordre local [order] remplace la liste enregistrée ; l'élément saisi suit le
 * doigt ([offset], lu dans graphicsLayer) et les voisins glissent grâce à animateItem (ressort).
 * L'ordre n'est persisté qu'au relâchement.
 */
@Stable
class DeckReorderState internal constructor(private val list: LazyListState) {
    var section by mutableStateOf<DeckSection?>(null)
        private set
    var dragging by mutableStateOf<String?>(null)
        private set
    var order by mutableStateOf<List<String>>(emptyList())
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    /** Ordre déposé, affiché jusqu'à ce que les réglages enregistrés le reflètent (pas de saut arrière). */
    private var pending by mutableStateOf<Pair<DeckSection, List<String>>?>(null)

    private var prefix = ""
    /** Position de l'élément au dernier échange : tant que la mise en page n'a pas suivi, on attend. */
    private var staleOffset: Int? = null

    fun isDragging(section: DeckSection, id: String) = this.section == section && dragging == id

    /** Liste à afficher pour [section] : l'ordre local pendant le geste, sinon [saved]. */
    fun displayed(section: DeckSection, saved: List<String>): List<String> = when {
        this.section == section -> order
        pending?.first == section && pending?.second?.sorted() == saved.sorted() -> pending!!.second
        else -> saved
    }

    /** À appeler quand les listes enregistrées changent. */
    fun clearPending() {
        pending = null
    }

    fun start(section: DeckSection, keyPrefix: String, ids: List<String>, id: String) {
        this.section = section
        prefix = keyPrefix
        order = ids
        dragging = id
        offset = 0f
        staleOffset = null
    }

    /** Retourne vrai quand l'élément a changé de place (pour l'haptique). */
    fun drag(dy: Float): Boolean {
        val id = dragging ?: return false
        offset += dy
        val items = list.layoutInfo.visibleItemsInfo
        val cur = items.firstOrNull { it.key == prefix + id } ?: return false
        if (cur.offset == staleOffset) return false
        val index = order.indexOf(id)
        val center = cur.offset + offset + cur.size / 2f
        if (offset > 0 && index < order.lastIndex) {
            val next = items.firstOrNull { it.key == prefix + order[index + 1] } ?: return false
            if (center > next.offset + next.size / 2f) {
                order = order.moved(index, index + 1)
                offset -= (next.offset + next.size - cur.offset - cur.size)
                staleOffset = cur.offset
                return true
            }
        } else if (offset < 0 && index > 0) {
            val prev = items.firstOrNull { it.key == prefix + order[index - 1] } ?: return false
            if (center < prev.offset + prev.size / 2f) {
                order = order.moved(index, index - 1)
                offset -= (prev.offset - cur.offset)
                staleOffset = cur.offset
                return true
            }
        }
        return false
    }

    /** Fin du geste : rend l'ordre final et remet l'état à zéro. */
    fun end(): Pair<DeckSection, List<String>>? {
        val s = section ?: return null
        val result = s to order
        pending = result
        section = null
        dragging = null
        offset = 0f
        staleOffset = null
        return result
    }
}

@Composable
fun rememberDeckReorderState(list: LazyListState): DeckReorderState = remember(list) { DeckReorderState(list) }

/**
 * Poignée de déplacement : appui long puis glisser. [onDrop] reçoit l'ordre final.
 * [haptics] suit le réglage « Retour haptique ».
 */
@Composable
fun Modifier.deckDragHandle(
    state: DeckReorderState,
    section: DeckSection,
    keyPrefix: String,
    ids: List<String>,
    id: String,
    haptics: Boolean,
    onDrop: (DeckSection, List<String>) -> Unit,
): Modifier {
    val feedback = LocalHapticFeedback.current
    val currentIds by rememberUpdatedState(ids)
    val drop by rememberUpdatedState(onDrop)
    val buzz by rememberUpdatedState(haptics)
    return pointerInput(section, id) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                state.start(section, keyPrefix, currentIds, id)
                if (buzz) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, amount ->
                change.consume()
                if (state.drag(amount.y) && buzz) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDragEnd = { state.end()?.let { (s, order) -> drop(s, order) } },
            onDragCancel = { state.end()?.let { (s, order) -> drop(s, order) } },
        )
    }
}
