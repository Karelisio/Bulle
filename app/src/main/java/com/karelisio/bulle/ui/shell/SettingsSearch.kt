package com.karelisio.bulle.ui.shell

import com.karelisio.bulle.core.apps.normalize
import com.karelisio.bulle.ui.Routes

/** Une entrée cherchable : écran, titre et texte d'appoint (déjà traduits), mots-clés. */
data class SearchItem(val route: String, val title: String, val summary: String, val keywords: List<String> = emptyList())

/**
 * Recherche dans les réglages de l'appli (logique pure, testée) : tous les mots de la saisie doivent
 * apparaître (sans accents ni casse) dans le titre, le résumé ou les mots-clés de l'écran.
 * Classement : titre qui commence par la saisie, puis titre qui la contient, puis le reste.
 */
object SettingsSearch {

    /** Mots-clés en plus des titres : réglages précis rangés dans un écran. */
    val keywords: Map<String, List<String>> = mapOf(
        Routes.VOLET to listOf("volet", "notifications", "réglages rapides", "tuiles", "hauteur", "paysage", "zones", "applis en fond", "shizuku", "flou"),
        Routes.HALO to listOf("halo", "bords lumineux", "edge lighting", "lumière", "pochette", "écran éteint", "charge", "signature", "vague"),
        Routes.EDGES to listOf("bords", "gestes", "goutte", "luminosité", "volume", "curseur", "barre", "retour"),
        Routes.STANDBY to listOf("veille", "standby", "horloge", "chevet", "nuit", "photos", "diaporama", "widgets", "dream", "économiseur", "scène", "scene", "lave", "aurore", "bulles", "constellation", "vagues", "screensaver"),
        Routes.PROFILES to listOf("profils", "travail", "nuit", "jeu", "mode"),
        Routes.RELIABILITY to listOf("fiabilité", "journal", "adb", "shizuku", "réactiver", "rapport", "diagnostic", "magicos", "test"),
        Routes.PERMISSIONS to listOf("autorisations", "permissions", "écran de verrouillage", "pop-up", "photos", "accessibilité"),
        Routes.BACKUP to listOf("sauvegarde", "restaurer", "exporter", "importer", "dossier", "drive"),
        Routes.STYLE to listOf("style", "couleur", "forme", "contour", "taille", "police", "animation", "flou"),
        Routes.POSITION to listOf("position", "caméra", "décalage", "réglage fin"),
        Routes.VISIBILITY to listOf("masquage", "plein écran", "paysage", "applis masquées", "verrouillage"),
        Routes.GESTURES to listOf("gestes", "toucher", "double", "appui long", "glisser", "actions"),
        Routes.MUSIC to listOf("musique", "paroles", "lrclib", "karaoké", "pochette", "visualiseur"),
        Routes.ALERTS to listOf("alertes", "batterie faible", "wi-fi", "bluetooth", "capture", "volume", "vibration", "son"),
        Routes.VOLUME to listOf("volume", "touches", "sortie audio"),
        Routes.ABOUT to listOf("à propos", "version", "mise à jour", "nouveautés"),
    )

    fun match(items: List<SearchItem>, query: String, limit: Int = 12): List<SearchItem> {
        val words = normalize(query.trim()).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            val title = normalize(item.title)
            val haystack = (listOf(title, normalize(item.summary)) + item.keywords.map(::normalize)).joinToString(" ")
            if (!words.all { haystack.contains(it) }) return@mapNotNull null
            val q = words.joinToString(" ")
            val score = when {
                title.startsWith(q) -> 0
                title.contains(q) -> 1
                item.keywords.any { normalize(it).startsWith(words.first()) } -> 2
                else -> 3
            }
            item to score
        }.sortedBy { it.second }.map { it.first }.distinctBy { it.route }.take(limit)
    }
}
