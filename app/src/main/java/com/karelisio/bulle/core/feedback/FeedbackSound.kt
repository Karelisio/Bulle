package com.karelisio.bulle.core.feedback

import kotlinx.serialization.Serializable

/** Famille de son. Les sons synthétisés sont générés à la volée ([SoundSynth]), aucun fichier copié. */
@Serializable
enum class SoundKind {
    NONE,

    /** Son de notification du système ([FeedbackSound.uri], ou le son par défaut si null). */
    SYSTEM,

    /** Deux notes cristallines montantes. */
    CHIME,

    /** Une bulle qui remonte (glissando ascendant). */
    BUBBLE,

    /** Un « pop » bref qui descend. */
    POP,

    /** Une seule note douce qui s'éteint lentement. */
    SOFT_DING,

    /** Trois notes rapides qui scintillent. */
    TWINKLE,
}

/** Son choisi pour un retour. Valeur par défaut = aucun son (comportement historique). */
@Serializable
data class FeedbackSound(
    val kind: SoundKind = SoundKind.NONE,
    /** URI d'une sonnerie système (RingtoneManager), seulement pour [SoundKind.SYSTEM]. */
    val uri: String? = null,
    /** Titre de la sonnerie système, mémorisé pour l'affichage. */
    val title: String? = null,
) {
    val isNone: Boolean get() = kind == SoundKind.NONE

    companion object {
        val None = FeedbackSound()

        /** Sons synthétisés proposés dans les sélecteurs. */
        val synthesized: List<SoundKind> =
            listOf(SoundKind.CHIME, SoundKind.BUBBLE, SoundKind.POP, SoundKind.SOFT_DING, SoundKind.TWINKLE)
    }
}
