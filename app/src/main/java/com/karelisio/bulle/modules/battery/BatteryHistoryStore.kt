package com.karelisio.bulle.modules.battery

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

private object HistorySerializer : Serializer<BatteryHistory> {
    override val defaultValue = BatteryHistory()

    override suspend fun readFrom(input: InputStream): BatteryHistory = try {
        BulleJson.decodeFromString(BatteryHistory.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Historique batterie illisible", e)
    }

    override suspend fun writeTo(t: BatteryHistory, output: OutputStream) {
        output.write(BulleJson.encodeToString(BatteryHistory.serializer(), t).encodeToByteArray())
    }
}

private val Context.batteryHistoryStore: DataStore<BatteryHistory> by dataStore(
    fileName = "battery_history.json",
    serializer = HistorySerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { BatteryHistory() },
)

/** Historique local des niveaux de batterie (12 h max, quelques Ko). */
object BatteryHistoryStore {

    fun flow(context: Context): Flow<List<LevelSample>> =
        context.applicationContext.batteryHistoryStore.data.map { it.samples }.distinctUntilChanged()

    suspend fun record(context: Context, sample: LevelSample) {
        runCatching {
            context.applicationContext.batteryHistoryStore.updateData { BatteryHistory(BatteryMath.record(it.samples, sample)) }
        }
    }
}
