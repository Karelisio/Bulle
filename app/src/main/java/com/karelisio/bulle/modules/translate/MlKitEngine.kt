package com.karelisio.bulle.modules.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Attente d'une Task Play Services sans dépendance supplémentaire. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Traduction ML Kit, entièrement sur l'appareil : seuls les modèles (~30 Mo par langue)
 * sont téléchargés, à la demande. L'identification de la langue est embarquée.
 */
object MlKitEngine : TranslationEngine {
    private val models by lazy { RemoteModelManager.getInstance() }
    private val identifier by lazy {
        LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build())
    }

    override val languages: List<String> by lazy { TranslateLanguage.getAllLanguages().sorted() }

    override suspend fun identify(text: String): String? {
        val tag = identifier.identifyLanguage(text.take(500)).await()
        if (tag == "und") return null
        return TranslateLanguage.fromLanguageTag(tag)
    }

    override suspend fun downloaded(): Set<String> =
        models.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()

    override suspend fun download(language: String, wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        models.download(TranslateRemoteModel.Builder(language).build(), conditions).await()
    }

    override suspend fun delete(language: String) {
        models.deleteDownloadedModel(TranslateRemoteModel.Builder(language).build()).await()
    }

    override suspend fun translate(text: String, source: String, target: String): String {
        if (source == target) return text
        val have = downloaded()
        // L'anglais (pivot de ML Kit) est toujours présent sur l'appareil.
        val missing = listOf(source, target).filter { it != TranslateLanguage.ENGLISH && it !in have }
        if (missing.isNotEmpty()) throw MissingModelException(missing)
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        return try {
            client.translate(text).await()
        } finally {
            client.close()
        }
    }
}
