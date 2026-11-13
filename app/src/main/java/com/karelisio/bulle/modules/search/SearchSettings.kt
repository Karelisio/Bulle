package com.karelisio.bulle.modules.search

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/**
 * Sections de la recherche de l'île. Toujours des valeurs par défaut (compatibilité JSON).
 * La recherche d'applis reste toujours active.
 */
@Serializable
data class SearchSettings(
    val calculator: Boolean = true,
    val units: Boolean = true,
    /** Conversion de devises (requête réseau vers Frankfurter, uniquement pour une saisie de devise). */
    val currency: Boolean = true,
    /** Contacts : seulement si READ_CONTACTS est accordée (sinon une puce propose de l'autoriser). */
    val contacts: Boolean = true,
    val systemSettings: Boolean = true,
)

/** Fichier JSON privé du module : réglages + cache des taux de change. */
@Serializable
internal data class SearchState(
    val settings: SearchSettings = SearchSettings(),
    val rates: RatesSnapshot? = null,
)

private object SearchStateSerializer : Serializer<SearchState> {
    override val defaultValue = SearchState()

    override suspend fun readFrom(input: InputStream): SearchState = try {
        BulleJson.decodeFromString(SearchState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Réglages de recherche illisibles", e)
    }

    override suspend fun writeTo(t: SearchState, output: OutputStream) {
        output.write(BulleJson.encodeToString(SearchState.serializer(), t).encodeToByteArray())
    }
}

private val Context.searchStore: DataStore<SearchState> by dataStore(
    fileName = "search_state.json",
    serializer = SearchStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { SearchState() },
)

/** Accès aux réglages de la recherche et au cache des taux (DataStore du module). */
object SearchPrefs {
    fun settings(context: Context): Flow<SearchSettings> =
        context.applicationContext.searchStore.data.map { it.settings }.distinctUntilChanged()

    suspend fun update(context: Context, transform: (SearchSettings) -> SearchSettings) {
        runCatching { context.applicationContext.searchStore.updateData { it.copy(settings = transform(it.settings)) } }
    }

    internal suspend fun cachedRates(context: Context): RatesSnapshot? =
        runCatching { context.applicationContext.searchStore.data.first().rates }.getOrNull()

    /** Date (AAAA-MM-JJ) des taux en cache, pour l'écran de réglages. */
    suspend fun cachedRatesDate(context: Context): String? = cachedRates(context)?.date?.takeIf { it.isNotEmpty() }

    internal suspend fun saveRates(context: Context, rates: RatesSnapshot) {
        runCatching { context.applicationContext.searchStore.updateData { it.copy(rates = rates) } }
    }
}
