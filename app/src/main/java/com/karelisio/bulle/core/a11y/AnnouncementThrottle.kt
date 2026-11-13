package com.karelisio.bulle.core.a11y

import com.karelisio.bulle.core.settings.IslandAnnouncements

/**
 * Décide si un changement du résumé de l'île doit être annoncé par TalkBack (région « live »
 * polie), sans le submerger : logique pure, horloge injectée.
 *
 *  - [IslandAnnouncements.NONE] : jamais ;
 *  - [IslandAnnouncements.IMPORTANT] : à l'arrivée d'un nouveau contenu important (alerte,
 *    message, appel…), jamais pour les mises à jour d'un contenu déjà annoncé ;
 *  - [IslandAnnouncements.ALL] : à l'arrivée de tout nouveau contenu, puis ses mises à jour
 *    (progression, minuteur…) au plus une fois toutes les [updateIntervalMs].
 *
 * Deux arrivées très rapprochées (moins de [burstIntervalMs]) ne sont annoncées qu'une fois,
 * sauf si la seconde est importante.
 */
class AnnouncementThrottle(
    private val clock: () -> Long,
    private val updateIntervalMs: Long = 10_000L,
    private val burstIntervalMs: Long = 1_500L,
) {
    private var lastKey: String? = null
    private var lastText: String? = null
    private var lastAnnouncedAt: Long? = null

    /** À appeler à chaque changement du résumé. Retourne vrai si ce changement doit être annoncé. */
    fun offer(key: String?, text: String?, important: Boolean, level: IslandAnnouncements): Boolean {
        val newContent = key != lastKey
        val changed = newContent || text != lastText
        lastKey = key
        lastText = text
        if (!changed || key == null || text.isNullOrBlank()) return false
        val now = clock()
        val since = lastAnnouncedAt?.let { now - it } ?: Long.MAX_VALUE
        val announce = when (level) {
            IslandAnnouncements.NONE -> false
            IslandAnnouncements.IMPORTANT -> newContent && important
            IslandAnnouncements.ALL ->
                if (newContent) important || since >= burstIntervalMs else since >= updateIntervalMs
        }
        if (announce) lastAnnouncedAt = now
        return announce
    }
}
