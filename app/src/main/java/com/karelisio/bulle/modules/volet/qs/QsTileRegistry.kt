package com.karelisio.bulle.modules.volet.qs

import android.content.Context

/**
 * Registre interne des tuiles. Pour ajouter une tuile : l'implémenter ([QsTile]) puis
 * l'inscrire dans [all] (et, si elle doit apparaître d'emblée, dans [defaultOrder]).
 */
object QsTileRegistry {
    val all: List<QsTile> = listOf(
        WifiTile, BluetoothTile, MobileDataTile, TorchTile, DndTile, RotationTile,
        AirplaneTile, HotspotTile, LocationTile, NfcTile, AutoBrightnessTile, ScreenshotTile, LockTile,
    )

    private val byId: Map<String, QsTile> = all.associateBy { it.id }

    /** Ordre par défaut : les six premières forment la rangée du volet à demi ouvert. */
    val defaultOrder: List<String> = listOf(
        "wifi", "bluetooth", "data", "torch", "dnd", "rotation",
        "airplane", "hotspot", "location", "nfc", "auto_brightness", "screenshot", "lock",
    )

    fun get(id: String): QsTile? = byId[id]

    /**
     * Tuiles affichées, sur l'appareil donné : tuiles système et tuiles d'applis ajoutées
     * (identifiant = composant aplati). Une appli désinstallée disparaît simplement de la liste.
     */
    fun current(context: Context, saved: List<String>, apps: List<AppTile>? = null): List<QsTile> {
        // Découverte des applis seulement si une tuile d'appli est enregistrée (rien ne change sinon).
        val appTiles = apps ?: if (saved.any(AppTiles::isAppTileId)) AppTiles.discover(context) else emptyList()
        val appById = appTiles.associateBy { it.id }
        return resolveOrder(saved, byId.keys + appById.keys)
            .mapNotNull { byId[it] ?: appById[it] }
            .filter { runCatching { it.isSupported(context) }.getOrDefault(false) }
    }

    /** Tuiles disponibles à l'ajout (connues, prises en charge, absentes de la liste). */
    fun available(context: Context, order: List<String>): List<QsTile> =
        all.filter { it.id !in order && runCatching { it.isSupported(context) }.getOrDefault(false) }

    /** Tuiles d'applis disponibles à l'ajout (section « Applis » de l'éditeur). */
    fun availableApps(apps: List<AppTile>, order: List<String>): List<AppTile> = apps.filter { it.id !in order }

    // ----- Logique pure (testée) -----

    /** Ordre effectif : sauvegardé (identifiants inconnus et doublons écartés), ou par défaut si vide. */
    fun resolveOrder(saved: List<String>, known: Set<String> = byId.keys): List<String> {
        val cleaned = saved.filter { it in known }.distinct()
        return cleaned.ifEmpty { defaultOrder.filter { it in known } }
    }

    /** Déplace l'élément d'indice [from] à l'indice [to]. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || from == to) return list
        val m = list.toMutableList()
        val item = m.removeAt(from)
        m.add(to.coerceIn(0, m.size), item)
        return m
    }

    fun add(order: List<String>, id: String): List<String> = if (id in order) order else order + id

    /** Retire une tuile ; au moins une tuile reste toujours. */
    fun remove(order: List<String>, id: String): List<String> =
        if (order.size <= 1) order else order - id
}
