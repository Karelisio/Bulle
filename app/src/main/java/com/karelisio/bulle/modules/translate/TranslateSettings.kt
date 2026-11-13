package com.karelisio.bulle.modules.translate

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/** Réglages de la traduction du presse-papiers. Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class TranslateSettings(
    /** Langue cible ; null = langue du téléphone. */
    val targetLanguage: String? = null,
    /** Téléchargement des modèles seulement en Wi-Fi. */
    val wifiOnly: Boolean = true,
)

private object TranslateSettingsSerializer : Serializer<TranslateSettings> {
    override val defaultValue = TranslateSettings()

    override suspend fun readFrom(input: InputStream): TranslateSettings = try {
        BulleJson.decodeFromString(TranslateSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Réglages de traduction illisibles", e)
    }

    override suspend fun writeTo(t: TranslateSettings, output: OutputStream) {
        output.write(BulleJson.encodeToString(TranslateSettings.serializer(), t).encodeToByteArray())
    }
}

private val Context.translateStore: DataStore<TranslateSettings> by dataStore(
    fileName = "translate_settings.json",
    serializer = TranslateSettingsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { TranslateSettings() },
)

object TranslatePrefs {
    fun flow(context: Context): Flow<TranslateSettings> = context.applicationContext.translateStore.data.distinctUntilChanged()

    suspend fun update(context: Context, transform: (TranslateSettings) -> TranslateSettings) {
        runCatching { context.applicationContext.translateStore.updateData(transform) }
    }
}

/** Choix des langues (fonctions pures). */
object TranslateLanguages {
    /** Langues proposées en premier dans le sélecteur. */
    val FAVORITES = listOf("fr", "en", "es", "de", "it", "pt", "nl", "ar", "zh", "ja", "ko", "ru")

    /** Langue cible effective : réglage, sinon langue du téléphone si prise en charge, sinon l'anglais. */
    fun target(settings: TranslateSettings, supported: List<String>, phone: String = Locale.getDefault().language): String =
        settings.targetLanguage?.takeIf { it in supported } ?: phone.takeIf { it in supported } ?: "en"

    /** Si le texte est déjà dans la langue cible, on traduit vers l'anglais (ou le français si la cible est l'anglais). */
    fun resolveTarget(source: String?, target: String): String =
        if (source != null && source == target) (if (target == "en") "fr" else "en") else target

    /** Nom affichable d'une langue, dans la langue de l'interface. */
    fun displayName(code: String, locale: Locale = Locale.getDefault()): String =
        Locale.forLanguageTag(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifEmpty { code }

    /** Favoris pris en charge d'abord, puis le reste par ordre alphabétique des noms. */
    fun ordered(supported: List<String>, locale: Locale = Locale.getDefault()): List<String> {
        val fav = FAVORITES.filter { it in supported }
        return fav + supported.filter { it !in fav }.sortedBy { displayName(it, locale) }
    }
}
