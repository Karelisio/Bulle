package com.karelisio.bulle.core.a11y

import android.content.Context
import androidx.annotation.StringRes
import com.karelisio.bulle.R

/**
 * Résumés TalkBack des contenus de l'île : fonctions pures (les chaînes passent par [Res],
 * une simple table d'identifiants en test). Forme : « Catégorie : élément, élément, état ».
 */
object IslandSpeech {

    /** Accès aux chaînes localisées (Context en production, table en test). */
    fun interface Res {
        fun str(@StringRes id: Int, vararg args: Any): String
    }

    fun res(context: Context): Res = Res { id, args -> context.getString(id, *args) }

    /** Assemble « Catégorie : a, b, c » en ignorant les parties vides et les doublons. */
    fun summary(category: String?, vararg parts: String?): String {
        val items = parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        val cat = category?.trim()?.takeIf(String::isNotEmpty)
        return when {
            cat == null -> items.joinToString(", ")
            items.isEmpty() -> cat
            else -> cat + " : " + items.joinToString(", ")
        }
    }

    fun music(r: Res, title: String?, artist: String?, playing: Boolean): String = summary(
        r.str(R.string.a11y_island_music),
        title, artist,
        r.str(if (playing) R.string.a11y_state_playing else R.string.a11y_state_paused),
    )

    fun call(r: Res, caller: String?, ringing: Boolean, video: Boolean, elapsed: String?): String = summary(
        r.str(if (video) R.string.a11y_island_video_call else R.string.a11y_island_call),
        caller ?: r.str(R.string.a11y_unknown_caller),
        if (ringing) r.str(R.string.a11y_state_ringing) else elapsed?.let { r.str(R.string.a11y_state_ongoing_for, it) },
    )

    /** Activité en cours (livraison, trajet, téléchargement…) ; [percent] null = indéterminé. */
    fun live(r: Res, app: String?, title: String?, text: String?, percent: Int?): String = summary(
        app ?: r.str(R.string.a11y_island_live),
        title, text,
        percent?.let { r.str(R.string.a11y_percent, it.coerceIn(0, 100)) },
    )

    fun downloads(r: Res, count: Int, percent: Int?): String = summary(
        r.str(R.string.a11y_island_downloads),
        r.str(R.string.a11y_downloads_count, count),
        percent?.let { r.str(R.string.a11y_percent, it.coerceIn(0, 100)) },
    )

    fun message(r: Res, sender: String?, app: String?, text: String?, typing: Boolean): String = summary(
        r.str(R.string.a11y_island_message),
        sender ?: app, if (typing) r.str(R.string.a11y_state_typing) else text,
        app.takeIf { sender != null },
    )

    fun timer(r: Res, phase: String?, remaining: String?, running: Boolean): String = summary(
        r.str(R.string.a11y_island_timer),
        phase,
        remaining?.let { r.str(R.string.a11y_remaining, it) },
        if (running) null else r.str(R.string.a11y_state_paused),
    )

    fun volume(r: Res, stream: String?, percent: Int, muted: Boolean): String = summary(
        r.str(R.string.a11y_island_volume),
        stream,
        if (muted) r.str(R.string.a11y_state_muted) else r.str(R.string.a11y_percent, percent.coerceIn(0, 100)),
    )

    fun recording(r: Res, kind: String, elapsed: String?, paused: Boolean): String = summary(
        kind,
        elapsed,
        r.str(if (paused) R.string.a11y_state_paused else R.string.a11y_state_recording),
    )

    fun countdown(r: Res, event: String, remaining: String?): String = summary(
        r.str(R.string.a11y_island_countdown),
        event, remaining,
    )

    fun alert(r: Res, text: String?): String = summary(r.str(R.string.a11y_island_alert), text)

    /** Repli quand le contenu ne fournit pas de résumé : sa seconde ligne compacte, sinon un libellé générique. */
    fun fallback(r: Res, subtitle: String?): String =
        summary(r.str(R.string.a11y_island_generic), subtitle)

    /** Durée lisible pour TalkBack (« 1 h 05 min 12 s » / « 3 min 04 s »). */
    fun duration(r: Res, ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> r.str(R.string.a11y_duration_hms, h, m, s)
            m > 0 -> r.str(R.string.a11y_duration_ms, m, s)
            else -> r.str(R.string.a11y_duration_s, s)
        }
    }
}
