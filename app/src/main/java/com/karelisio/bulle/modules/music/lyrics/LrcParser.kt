package com.karelisio.bulle.modules.music.lyrics

/**
 * Parseur LRC tolérant :
 *  - horodatages `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]`, minutes sur 1 à 3 chiffres ;
 *  - plusieurs horodatages sur une même ligne (refrains) ;
 *  - balise `[offset:±ms]` (positive = paroles affichées plus tôt, comme le format LRC le définit) ;
 *  - balises d'en-tête (`[ar:]`, `[ti:]`…) ignorées ;
 *  - balises mot à mot `<mm:ss.xx>` (LRC « enrichi ») : retirées du texte mais conservées dans
 *    [LyricLine.words] pour le remplissage karaoké ; une balise finale sans texte donne [LyricLine.endMs] ;
 *  - BOM, retours chariot, lignes vides.
 * Un texte sans aucun horodatage est rendu comme paroles non synchronisées.
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val SPACES = Regex("""\s+""")
    private val META_TAG = Regex("""^\[([a-zA-Z#]+):(.*)]$""")
    private val LRC_LINE = Regex("""^\s*\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]""", RegexOption.MULTILINE)

    /** Marque d'ordre des octets (U+FEFF) parfois présente en tête des fichiers LRC. */
    private val BOM = Char(0xFEFF)

    /** Le texte ressemble-t-il à du LRC synchronisé (au moins deux lignes horodatées) ? */
    fun looksLikeLrc(text: String): Boolean = LRC_LINE.findAll(text).take(2).count() >= 2

    /** Analyse un texte LRC (ou brut). Retourne null si aucune ligne exploitable. */
    fun parse(text: String, source: LyricsSource): Lyrics? {
        val raw = text.removePrefix(BOM.toString()).replace("\r\n", "\n").replace('\r', '\n')
        var offset = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()

        for (line0 in raw.split('\n')) {
            val line = line0.trim()
            if (line.isEmpty()) {
                plain += ""
                continue
            }
            // Horodatages consécutifs en tête de ligne.
            var rest = line
            val times = ArrayList<Long>(2)
            while (true) {
                val m = TIME_TAG.matchAt(rest, 0) ?: break
                times += toMillis(m)
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            if (times.isNotEmpty()) {
                // Les balises mot à mot sont absolues : rattachées au premier horodatage de la ligne,
                // puis décalées d'autant pour les répétitions (refrains).
                val base = times.min()
                val split = splitWords(rest, base)
                for (t in times) {
                    val shift = t - base
                    timed += LyricLine(
                        timeMs = t,
                        text = split.text,
                        words = if (shift == 0L) split.words else split.words.map { it.copy(timeMs = it.timeMs + shift) },
                        endMs = if (split.endMs >= 0) split.endMs + shift else -1,
                    )
                }
                continue
            }
            val meta = META_TAG.matchEntire(line)
            if (meta != null) {
                if (meta.groupValues[1].equals("offset", ignoreCase = true)) {
                    offset = meta.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                }
                continue
            }
            plain += WORD_TAG.replace(line, "").trim()
        }

        if (timed.isNotEmpty()) {
            val lines = timed
                .map { l ->
                    if (offset == 0L) {
                        l
                    } else {
                        l.copy(
                            timeMs = (l.timeMs - offset).coerceAtLeast(0),
                            words = l.words.map { w -> w.copy(timeMs = (w.timeMs - offset).coerceAtLeast(0)) },
                            endMs = if (l.endMs >= 0) (l.endMs - offset).coerceAtLeast(0) else -1,
                        )
                    }
                }
                .sortedBy { it.timeMs } // tri stable : l'ordre d'origine départage les égalités
                .dropLastWhile { it.text.isEmpty() }
            if (lines.isEmpty()) return null
            return Lyrics(lines, synced = true, source = source)
        }
        val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (lines.isEmpty()) return null
        // Fusionne les blocs de lignes vides en une seule (séparation de couplets).
        val compact = ArrayList<LyricLine>(lines.size)
        for (l in lines) {
            if (l.isEmpty() && compact.lastOrNull()?.text?.isEmpty() == true) continue
            compact += LyricLine(-1, l)
        }
        return Lyrics(compact, synced = false, source = source)
    }

    /** Texte d'une ligne horodatée, ses mots horodatés (s'il y a des balises) et sa fin chantée. */
    private class SplitLine(val text: String, val words: List<LyricWord>, val endMs: Long)

    /**
     * Découpe [rest] (le texte après les horodatages de ligne) selon les balises mot à mot.
     * Les espaces sont normalisés (un seul entre deux mots, aucun en bout de ligne) de sorte que la
     * concaténation des mots soit exactement le texte affiché : le remplissage karaoké compte en caractères.
     */
    private fun splitWords(rest: String, lineTime: Long): SplitLine {
        val tags = WORD_TAG.findAll(rest).toList()
        if (tags.isEmpty()) return SplitLine(rest.replace(SPACES, " ").trim(), emptyList(), -1)
        val raw = ArrayList<Pair<Long, String>>(tags.size + 1)
        val lead = rest.substring(0, tags[0].range.first)
        if (lead.isNotBlank()) raw += lineTime to lead
        tags.forEachIndexed { i, m ->
            val end = if (i + 1 < tags.size) tags[i + 1].range.first else rest.length
            raw += toMillis(m) to rest.substring(m.range.last + 1, end)
        }
        // Balise(s) finale(s) sans texte : fin chantée de la ligne.
        var endMs = -1L
        while (raw.isNotEmpty() && raw[raw.lastIndex].second.isBlank()) {
            endMs = maxOf(endMs, raw[raw.lastIndex].first)
            raw.removeAt(raw.lastIndex)
        }
        val words = ArrayList<LyricWord>(raw.size)
        for ((t, segment) in raw) {
            var piece = segment.replace(SPACES, " ")
            val prevEndsWithSpace = words.isEmpty() || words[words.lastIndex].text.endsWith(' ')
            if (prevEndsWithSpace) piece = piece.trimStart()
            if (piece.isBlank()) {
                // Segment vide (espace seul entre deux balises) : l'espace revient au mot précédent.
                if (piece.isNotEmpty() && words.isNotEmpty()) {
                    val last = words[words.lastIndex]
                    words[words.lastIndex] = last.copy(text = last.text + " ")
                }
                continue
            }
            words += LyricWord(t, piece)
        }
        if (words.isNotEmpty()) {
            val last = words[words.lastIndex]
            words[words.lastIndex] = last.copy(text = last.text.trimEnd())
        }
        return SplitLine(words.joinToString("") { it.text }, words, endMs)
    }

    private fun toMillis(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return min * 60_000 + sec * 1_000 + ms
    }
}
