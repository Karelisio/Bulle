package com.karelisio.bulle.modules.music.lyrics

/** Une ligne de paroles. [timeMs] = -1 pour des paroles non synchronisées. */
data class LyricLine(val timeMs: Long, val text: String)

/** Provenance des paroles, affichée sous le texte. */
enum class LyricsSource { PLAYER, LRCLIB }

/** Paroles prêtes à afficher. */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: LyricsSource,
) {
    /**
     * Index de la ligne en cours à [positionMs] (la dernière dont l'horodatage est passé),
     * -1 avant la première ligne ou si les paroles ne sont pas synchronisées.
     * Recherche dichotomique : appelée à chaque image.
     */
    fun indexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

/** État du chargement des paroles pour le morceau en cours. */
sealed interface LyricsState {
    data object Off : LyricsState
    data object Loading : LyricsState
    data object NotFound : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}
