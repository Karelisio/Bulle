package com.karelisio.bulle.ui.about

/** Une version du CHANGELOG.md : titre (« 0.1.0 ») et notes en markdown. */
internal data class ChangelogSection(val title: String, val body: String)

/** Découpage du CHANGELOG.md embarqué dans les assets (pur). */
internal object Changelog {
    fun sections(markdown: String): List<ChangelogSection> {
        val out = mutableListOf<ChangelogSection>()
        var title: String? = null
        val body = StringBuilder()
        fun flush() {
            title?.let { out += ChangelogSection(it, body.toString().trim()) }
            body.clear()
        }
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.startsWith("## ")) {
                flush()
                title = line.removePrefix("## ").trim().trim('[', ']')
            } else if (title != null) {
                body.appendLine(line)
            }
        }
        flush()
        return out
    }
}
