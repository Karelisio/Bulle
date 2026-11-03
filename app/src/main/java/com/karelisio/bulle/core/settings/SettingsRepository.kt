package com.karelisio.bulle.core.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

val BulleJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

private object SettingsSerializer : Serializer<BulleSettings> {
    override val defaultValue = BulleSettings()

    override suspend fun readFrom(input: InputStream): BulleSettings = try {
        BulleJson.decodeFromString(BulleSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Réglages illisibles", e)
    }

    override suspend fun writeTo(t: BulleSettings, output: OutputStream) {
        output.write(BulleJson.encodeToString(BulleSettings.serializer(), t).encodeToByteArray())
    }
}

private val Context.settingsStore: DataStore<BulleSettings> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { BulleSettings() },
)

/** Point d'accès unique aux réglages. */
object SettingsRepository {
    fun flow(context: Context): Flow<BulleSettings> =
        context.applicationContext.settingsStore.data.distinctUntilChanged()

    suspend fun current(context: Context): BulleSettings = flow(context).first()

    suspend fun update(context: Context, transform: (BulleSettings) -> BulleSettings) {
        context.applicationContext.settingsStore.updateData(transform)
    }
}
