package com.karelisio.bulle.modules.music.lyrics

/**
 * Parseur LRC tolérant :
 *  - horodatages `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]`, minutes sur 1 à 3 chiffres ;
 *  - plusieurs horodatages sur une même ligne (refrains) ;
 *  - balise `[offset:±ms]` (positive = paroles affichées plus tôt, comme le format LRC le définit) ;
 *  - balises d'en-tête (`[ar:]`, `[ti:]`…) ignorées, balises mot à mot `<mm:ss.xx>` retirées ;
 *  - BOM, retours chariot, lignes vides.
 * Un texte sans aucun horodatage est rendu comme paroles non synchronisées.
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
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
                val clean = WORD_TAG.replace(rest, "").replace(Regex("\\s+"), " ").trim()
                for (t in times) timed += LyricLine(t, clean)
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
                .map { it.copy(timeMs = (it.timeMs - offset).coerceAtLeast(0)) }
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
