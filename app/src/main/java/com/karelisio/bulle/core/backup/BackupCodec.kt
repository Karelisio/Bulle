package com.karelisio.bulle.core.backup

import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.update.UpdateSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Enveloppe d'un fichier de sauvegarde. */
@Serializable
data class BackupEnvelope(
    val schema: Int = BackupCodec.SCHEMA,
    val appVersion: String = "",
    /** Date de l'export (epoch ms). */
    val exportedAt: Long = 0,
    val settings: BulleSettings = BulleSettings(),
)

/** Résultat de lecture d'un fichier de sauvegarde. */
sealed interface DecodeResult {
    data class Ok(val envelope: BackupEnvelope) : DecodeResult
    /** Pas du JSON. */
    data object InvalidJson : DecodeResult
    /** JSON valide mais pas une sauvegarde Bulle. */
    data object NotABackup : DecodeResult
    /** Créée par une version plus récente (schéma inconnu). */
    data class UnsupportedSchema(val schema: Int) : DecodeResult
}

/** Encodage / décodage des sauvegardes (fonctions pures). */
object BackupCodec {
    const val SCHEMA = 1

    private val pretty = Json(from = BulleJson) { prettyPrint = true }

    /** Retire l'état technique qui n'a pas de sens sur un autre appareil ou plus tard. */
    fun sanitize(settings: BulleSettings): BulleSettings = settings.copy(
        update = UpdateSettings(),
        backup = settings.backup.copy(lastAutoBackup = 0),
    )

    fun encode(settings: BulleSettings, appVersion: String, now: Long): String =
        pretty.encodeToString(
            BackupEnvelope.serializer(),
            BackupEnvelope(SCHEMA, appVersion, now, sanitize(settings)),
        )

    fun decode(text: String): DecodeResult {
        val root = try {
            BulleJson.parseToJsonElement(text) as? JsonObject ?: return DecodeResult.NotABackup
        } catch (e: SerializationException) {
            return DecodeResult.InvalidJson
        } catch (e: IllegalArgumentException) {
            return DecodeResult.InvalidJson
        }
        val schema = runCatching { root["schema"]?.jsonPrimitive?.intOrNull }.getOrNull()
        if (schema == null || root["settings"] !is JsonObject) return DecodeResult.NotABackup
        if (schema > SCHEMA || schema < 1) return DecodeResult.UnsupportedSchema(schema)
        return try {
            DecodeResult.Ok(BulleJson.decodeFromJsonElement(BackupEnvelope.serializer(), root))
        } catch (e: SerializationException) {
            DecodeResult.NotABackup
        } catch (e: IllegalArgumentException) {
            DecodeResult.NotABackup
        }
    }

    /**
     * Réglages à appliquer : ceux de la sauvegarde, en conservant l'état technique
     * courant (dernière vérification de mise à jour, dernière sauvegarde auto).
     */
    fun merge(current: BulleSettings, imported: BulleSettings): BulleSettings = imported.copy(
        update = current.update,
        backup = imported.backup.copy(lastAutoBackup = current.backup.lastAutoBackup),
    )

    /** Nom de fichier proposé à l'export : bulle-reglages-AAAA-MM-JJ.json. */
    fun exportFileName(now: Long): String =
        "bulle-reglages-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".json"
}
