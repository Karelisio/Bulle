package com.karelisio.bulle.modules.live

import android.app.PendingIntent

/** Nature d'une activité en cours : décide de l'affichage compact. */
enum class LiveKind {
    /** Compte à rebours (minuteur, cuisson…). */
    TIMER,

    /** Chronomètre qui monte (chrono, séance de sport, enregistrement…). */
    STOPWATCH,

    /** Guidage GPS. */
    NAVIGATION,

    /** Barre de progression (téléchargement, envoi, livraison…). */
    PROGRESS,

    /** Activité en cours sans chiffre à afficher (VTC, partage de position…). */
    GENERIC,
}

/** Segment coloré d'une barre de progression (Notification.ProgressStyle, Android 16). */
data class LiveSegment(val length: Int, val color: Int)

/** Point remarquable sur la barre (étape d'une livraison…). */
data class LivePoint(val position: Int, val color: Int)

/** Progression : [current] sur [max], ou indéterminée. */
data class LiveProgress(
    val current: Int,
    val max: Int,
    val indeterminate: Boolean,
    val segments: List<LiveSegment> = emptyList(),
    val points: List<LivePoint> = emptyList(),
) {
    /** Fraction 0..1 (0 si indéterminée). */
    val fraction: Float
        get() = if (indeterminate || max <= 0) 0f else (current.toFloat() / max).coerceIn(0f, 1f)
}

/**
 * Chronomètre : [baseWallMillis] est le champ `when` de la notification (heure murale).
 * Compte à rebours : temps restant = base − maintenant. Sinon : écoulé = maintenant − base.
 */
data class LiveChrono(val baseWallMillis: Long, val countDown: Boolean) {
    fun valueAt(nowWallMillis: Long): Long =
        if (countDown) baseWallMillis - nowWallMillis else nowWallMillis - baseWallMillis
}

/** Bouton d'action repris de la notification (sans saisie de texte). */
data class LiveAction(val title: String, val intent: PendingIntent)

/**
 * Modèle pur d'une activité en cours, extrait d'une notification par [LiveParser].
 * Ne contient que des données comparables : l'égalité évite des recompositions.
 */
data class LiveActivity(
    val key: String,
    val packageName: String,
    val kind: LiveKind,
    val title: String?,
    val text: String?,
    val subText: String?,
    /** Texte court de la puce de barre d'état (Android 16, notifications promues). */
    val shortText: String?,
    val progress: LiveProgress?,
    val chrono: LiveChrono?,
    /** Couleur de la notification (0 = aucune). */
    val color: Int,
    /** Notification « promue » par le système (Android 16) ou ProgressStyle. */
    val promoted: Boolean,
    /** Progression générique (téléchargement…), masquable dans les réglages. */
    val genericProgress: Boolean,
    val actions: List<LiveAction>,
    val contentIntent: PendingIntent?,
)
