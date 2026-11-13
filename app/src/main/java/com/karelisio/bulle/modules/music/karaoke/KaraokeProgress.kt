package com.karelisio.bulle.modules.music.karaoke

import com.karelisio.bulle.modules.music.lyrics.LyricLine
import com.karelisio.bulle.modules.music.lyrics.Lyrics
import kotlin.math.min

/**
 * Remplissage « karaoké » de la ligne courante (logique pure, testée) : combien de caractères de la
 * ligne sont déjà chantés à une position donnée.
 *
 *  - Avec balises mot à mot (`<mm:ss.xx>`) : chaque mot se remplit entre son horodatage et celui du
 *    mot suivant ; le dernier jusqu'à la fin chantée ([LyricLine.endMs]) ou une durée estimée.
 *  - Sans balises : interpolation linéaire entre l'heure de la ligne et une fin estimée — la ligne
 *    suivante (un peu avant), plafonnée à une durée « naturelle » proportionnelle au texte, pour
 *    qu'une ligne suivie d'un long pont instrumental ne se remplisse pas au ralenti.
 *
 * Toutes les positions sont des positions « paroles » (position du lecteur − décalage de la sortie).
 */
object KaraokeProgress {
    /** Durée naturelle d'une ligne sans balises : base + par caractère. */
    const val BASE_MS = 900L
    const val PER_CHAR_MS = 110L

    /** Le remplissage se termine un peu avant la ligne suivante (souffle du chanteur). */
    const val GAP_RATIO = 0.92f

    /** Durée minimale du dernier mot quand rien n'indique sa fin. */
    const val LAST_WORD_MIN_MS = 350L
    const val LAST_WORD_PER_CHAR_MS = 160L

    /** Fin estimée du chant d'une ligne sans balises mot à mot. */
    fun lineEnd(line: LyricLine, nextTimeMs: Long?): Long {
        if (line.endMs > line.timeMs) return line.endMs
        val natural = BASE_MS + PER_CHAR_MS * line.text.length
        val gap = nextTimeMs?.let { ((it - line.timeMs) * GAP_RATIO).toLong() }?.takeIf { it > 0 }
        return line.timeMs + if (gap != null) min(gap, natural) else natural
    }

    /** Fin du dernier mot horodaté. */
    private fun lastWordEnd(line: LyricLine, nextTimeMs: Long?): Long {
        val last = line.words.last()
        if (line.endMs > last.timeMs) return line.endMs
        val natural = last.timeMs + maxOf(LAST_WORD_MIN_MS, LAST_WORD_PER_CHAR_MS * last.text.trim().length)
        return if (nextTimeMs != null && nextTimeMs > last.timeMs) min(natural, nextTimeMs) else natural
    }

    /**
     * Nombre de caractères remplis (fractionnaire, 0 à `line.text.length`) de [line] à [positionMs].
     * [nextTimeMs] : horodatage de la ligne suivante (null pour la dernière).
     */
    fun filledChars(line: LyricLine, nextTimeMs: Long?, positionMs: Long): Float {
        val total = line.text.length
        if (total == 0) return 0f
        if (line.words.isEmpty()) {
            val start = line.timeMs
            val end = lineEnd(line, nextTimeMs)
            if (positionMs <= start) return 0f
            if (positionMs >= end || end <= start) return total.toFloat()
            return total * ((positionMs - start).toFloat() / (end - start))
        }
        var acc = 0f
        val words = line.words
        for (i in words.indices) {
            val w = words[i]
            val len = w.text.length
            val start = w.timeMs
            val end = if (i + 1 < words.size) words[i + 1].timeMs else lastWordEnd(line, nextTimeMs)
            when {
                positionMs >= end -> acc += len
                positionMs <= start -> return acc
                else -> return acc + len * ((positionMs - start).toFloat() / (end - start))
            }
        }
        return acc.coerceAtMost(total.toFloat())
    }

    /** Part remplie (0..1) de [line]. */
    fun fraction(line: LyricLine, nextTimeMs: Long?, positionMs: Long): Float {
        val total = line.text.length
        if (total == 0) return if (positionMs >= line.timeMs) 1f else 0f
        return (filledChars(line, nextTimeMs, positionMs) / total).coerceIn(0f, 1f)
    }

    /** Caractères remplis de la ligne [index] de [lyrics] (0 si l'index est hors bornes). */
    fun filledCharsAt(lyrics: Lyrics, index: Int, positionMs: Long): Float {
        val line = lyrics.lines.getOrNull(index) ?: return 0f
        return filledChars(line, lyrics.lines.getOrNull(index + 1)?.timeMs, positionMs)
    }

    /**
     * Position du lecteur à viser pour revenir au début de la ligne [line] avec le décalage
     * [offsetMs] de la sortie courante (les paroles suivent la position − décalage).
     */
    fun seekTargetFor(line: LyricLine, offsetMs: Int): Long = (line.timeMs + offsetMs).coerceAtLeast(0)
}
