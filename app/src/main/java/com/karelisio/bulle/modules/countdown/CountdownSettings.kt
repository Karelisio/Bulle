package com.karelisio.bulle.modules.countdown

import kotlinx.serialization.Serializable

/**
 * Un événement suivi par le compte à rebours. Dates en texte ISO (stables en JSON).
 *
 * @property date jour de l'événement, « 2026-12-25 ». Pour un anniversaire ([yearly]), l'année
 * d'origine (naissance) est gardée : seule l'occurrence suivante compte.
 * @property time heure optionnelle « 14:30 » ; null = toute la journée (cible : minuit).
 * @property createdAt création (epoch ms) : point de départ de la barre de progression.
 */
@Serializable
data class CountdownEvent(
    val id: String,
    val name: String,
    val emoji: String = DEFAULT_EMOJI,
    val date: String,
    val time: String? = null,
    val yearly: Boolean = false,
    val color: Long = DEFAULT_COLOR,
    val createdAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_EMOJI = "🎉"
        const val DEFAULT_COLOR = 0xFFFFB86AL

        /** Palette proposée dans l'éditeur. */
        val COLORS = listOf(0xFFFFB86AL, 0xFFFF7A9AL, 0xFFB69CFFL, 0xFF6AD7FFL, 0xFF7BE0A0L, 0xFFFFD54FL)

        /** Émojis proposés dans l'éditeur. */
        val EMOJIS = listOf("🎂", "🎉", "🎄", "✈️", "💍", "🎓", "❤️", "🏖️", "🎁", "📅")
    }
}

/** Ce qui s'affiche au repos. */
@Serializable
enum class CountdownIdleMode { PINNED, NEXT, OFF }

/** Réglages du module « Compte à rebours ». Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class CountdownSettings(
    val events: List<CountdownEvent> = emptyList(),
    /** Événement épinglé (affiché au repos en mode [CountdownIdleMode.PINNED]). */
    val pinnedId: String? = null,
    val idleMode: CountdownIdleMode = CountdownIdleMode.PINNED,
    /** Rappel ponctuel la veille de chaque événement. */
    val remindDayBefore: Boolean = true,
    /** Rappels déjà montrés (« id@2026-12-25 »), pour ne pas les répéter après un redémarrage. */
    val reminded: Set<String> = emptySet(),
) {
    /** Événement montré au repos, ou null. */
    fun idleEvent(now: java.time.ZonedDateTime): CountdownEvent? = when (idleMode) {
        CountdownIdleMode.OFF -> null
        CountdownIdleMode.PINNED -> events.firstOrNull { it.id == pinnedId }?.takeIf { CountdownMath.status(it, now) != null }
        CountdownIdleMode.NEXT -> CountdownMath.upcoming(events, now).firstOrNull()?.event
    }
}

/** Réglages du module, dans un fichier privé. */
object CountdownPrefs {
    const val FILE_NAME = "countdown.json"
    val store = PrivateJsonStore(FILE_NAME, CountdownSettings.serializer()) { CountdownSettings() }

    /** Ajoute ou remplace un événement ; le premier événement créé est épinglé. */
    fun upsert(settings: CountdownSettings, event: CountdownEvent): CountdownSettings {
        val exists = settings.events.any { it.id == event.id }
        val events = if (exists) settings.events.map { if (it.id == event.id) event else it } else settings.events + event
        val pinned = settings.pinnedId?.takeIf { id -> events.any { it.id == id } } ?: event.id
        return settings.copy(events = events, pinnedId = pinned)
    }

    fun remove(settings: CountdownSettings, id: String): CountdownSettings {
        val events = settings.events.filterNot { it.id == id }
        return settings.copy(
            events = events,
            pinnedId = settings.pinnedId?.takeIf { it != id } ?: events.firstOrNull()?.id,
            reminded = settings.reminded.filterNot { it.startsWith("$id@") }.toSet(),
        )
    }
}
