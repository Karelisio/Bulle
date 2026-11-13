package com.karelisio.bulle.core.style

import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.modules.alerts.ui.StyledAlert

/**
 * Animation d'entrée d'un contenu compact dans l'île, jouée quand le contenu principal change.
 * L'ordre et les noms sont sérialisés (réglages, packs de styles) : n'ajouter qu'à la fin.
 */
enum class EntryAnimation {
    /** Gonflement : comportement historique de l'île (aucun calque supplémentaire). */
    INFLATE,

    /** Fondu simple. */
    FADE,

    /** Le contenu glisse depuis la caméra, vers le bas. */
    SLIDE,

    /** Rebond élastique : le contenu surgit petit et dépasse sa taille avant de se poser. */
    ELASTIC,

    /** Goutte : le contenu tombe de la caméra en s'étirant, puis s'écrase légèrement. */
    DROP,

    /** Éclat : le contenu se resserre pendant qu'une onde d'accent traverse l'île. */
    RIPPLE,
    ;

    companion object {
        /** Nom sérialisé → animation ; null si inconnu (version plus récente, texte modifié). */
        fun parse(name: String?): EntryAnimation? = name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}

/** Résolution de l'animation d'entrée à jouer pour un contenu (logique pure, testée). */
object EntryAnimations {
    /** Clé de la valeur par défaut dans [MotionSettings.entryAnimations]. */
    const val DEFAULT_KEY = "default"

    /** Nombre maximal d'entrées acceptées (réglages importés). */
    const val MAX_ENTRIES = 32

    /** Clé valide : identifiant de module en minuscules (ou [DEFAULT_KEY]). */
    val KeyPattern = Regex("^[a-z0-9_.-]{1,40}$")

    /** Types de contenu proposés dans l'écran « Animations d'entrée », dans l'ordre d'affichage. */
    val ConfigurableModules = listOf(
        "music", "messages", "alerts", "call", "live", "calendar",
        "weather", "focus", "battery", "network", "volume", "echo", "external",
    )

    /**
     * Animation pour [moduleId] : réglage du module, sinon valeur par défaut, sinon gonflement.
     * [contentOwnsEntrance] : le contenu a déjà sa propre animation d'entrée (alerte personnalisée
     * dans Alertes) ; elle reste prioritaire, l'île ne rajoute rien.
     * [reduceMotion] : toute animation autre que le gonflement devient un simple fondu.
     */
    fun resolve(
        moduleId: String?,
        entries: Map<String, String>,
        contentOwnsEntrance: Boolean = false,
        reduceMotion: Boolean = false,
    ): EntryAnimation {
        if (contentOwnsEntrance) return EntryAnimation.INFLATE
        val chosen = moduleId?.let { EntryAnimation.parse(entries[it]) }
            ?: EntryAnimation.parse(entries[DEFAULT_KEY])
            ?: EntryAnimation.INFLATE
        return if (reduceMotion && chosen != EntryAnimation.INFLATE) EntryAnimation.FADE else chosen
    }

    fun resolve(content: IslandContent?, motion: MotionSettings, reduceMotion: Boolean): EntryAnimation =
        resolve(content?.moduleId, motion.entryAnimations, ownsEntrance(content), reduceMotion)

    /** Vrai si le contenu est une alerte dont l'animation d'entrée a été choisie dans Alertes. */
    fun ownsEntrance(content: IslandContent?): Boolean = (content as? StyledAlert)?.style?.entrance != null

    /** Réglages avec [key] → [animation] (null = retirer, le module suit alors la valeur par défaut). */
    fun with(entries: Map<String, String>, key: String, animation: EntryAnimation?): Map<String, String> {
        // Le gonflement par défaut n'est pas stocké : réglages vides = comportement d'origine.
        val drop = animation == null || (key == DEFAULT_KEY && animation == EntryAnimation.INFLATE)
        return if (drop) entries - key else entries + (key to animation!!.name)
    }

    /** Entrées nettoyées : clés valides, valeurs connues, nombre borné. */
    fun sanitize(entries: Map<String, String>): Map<String, String> = entries
        .filter { (k, v) -> KeyPattern.matches(k) && EntryAnimation.parse(v) != null }
        .entries.take(MAX_ENTRIES)
        .associate { it.key to it.value }
}
