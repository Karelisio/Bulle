package com.karelisio.bulle.modules.translate

/** Le modèle d'une langue n'est pas sur le téléphone : il faut le télécharger avant de traduire. */
class MissingModelException(val languages: List<String>) : Exception("Modèles manquants : $languages")

/**
 * Traducteur hors ligne, abstrait pour les tests (l'implémentation réelle est [MlKitEngine]).
 * Les langues sont des codes ML Kit (« fr », « en », « zh »…).
 */
interface TranslationEngine {
    /** Langues prises en charge par la traduction. */
    val languages: List<String>

    /** Langue détectée du texte (code pris en charge), ou null si indéterminée. */
    suspend fun identify(text: String): String?

    /** Langues dont le modèle est téléchargé. */
    suspend fun downloaded(): Set<String>

    /** Télécharge le modèle de [language] (attend le Wi-Fi si [wifiOnly]). */
    suspend fun download(language: String, wifiOnly: Boolean)

    suspend fun delete(language: String)

    /** Traduit ; lève [MissingModelException] si un modèle manque (jamais de téléchargement implicite). */
    suspend fun translate(text: String, source: String, target: String): String
}
