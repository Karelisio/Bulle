package com.karelisio.bulle.modules.network

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

private object UsageSerializer : Serializer<UsageState> {
    override val defaultValue = UsageState()

    override suspend fun readFrom(input: InputStream): UsageState = try {
        BulleJson.decodeFromString(UsageState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cumul réseau illisible", e)
    }

    override suspend fun writeTo(t: UsageState, output: OutputStream) {
        output.write(BulleJson.encodeToString(UsageState.serializer(), t).encodeToByteArray())
    }
}

private val Context.usageStore: DataStore<UsageState> by dataStore(
    fileName = "network_usage.json",
    serializer = UsageSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { UsageState() },
)

/** Cumul de consommation du jour (petit fichier JSON privé du module). */
object NetworkUsageStore {

    fun flow(context: Context): Flow<UsageState> = context.applicationContext.usageStore.data.distinctUntilChanged()

    /** Intègre un relevé des compteurs (changement de réseau, écran, ou toutes les minutes pendant un affichage). */
    internal suspend fun checkpoint(context: Context, kind: NetKind) {
        val reading = NetworkProbe.reading(context, kind)
        runCatching {
            context.applicationContext.usageStore.updateData { DailyUsage.accumulate(it, reading, ZoneId.systemDefault()) }
        }
    }

    /** Remise à zéro manuelle : le cumul repart de maintenant (« depuis l'activation »). */
    suspend fun reset(context: Context) {
        val reading = NetworkProbe.reading(context, NetworkProbe.connection(context).kind)
        runCatching {
            context.applicationContext.usageStore.updateData {
                DailyUsage.fresh(DailyUsage.epochDay(reading.atMillis, ZoneId.systemDefault()), reading)
            }
        }
    }
}
