package com.karelisio.bulle.modules.profiles

import android.content.Context
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Un profil : ce qu'il change d'un coup. Chaque champ null = laissé tel quel ; true / false =
 * activé / coupé à l'application du profil.
 */
@Serializable
data class BulleProfile(
    val id: String,
    val name: String,
    val emoji: String = "✨",
    val island: Boolean? = null,
    val halo: Boolean? = null,
    val edges: Boolean? = null,
    val volet: Boolean? = null,
    val standby: Boolean? = null,
    val alerts: Boolean? = null,
    val haptics: Boolean? = null,
)

/** Réglages des profils (liste et profil actif). */
@Serializable
data class ProfilesSettings(
    val list: List<BulleProfile> = Profiles.defaults,
    val activeId: String? = null,
)

/** Ce qu'un profil peut changer (ordre d'affichage de l'éditeur). */
enum class ProfileField { ISLAND, HALO, EDGES, VOLET, STANDBY, ALERTS, HAPTICS }

/** Profils (logique pure + application). */
object Profiles {

    val defaults: List<BulleProfile> = listOf(
        BulleProfile("work", "Travail", "💼", island = true, halo = false, edges = true, alerts = true),
        BulleProfile("night", "Nuit", "🌙", halo = false, alerts = false, haptics = false, standby = true),
        BulleProfile("game", "Jeu", "🎮", halo = false, edges = false, alerts = false, haptics = false),
    )

    fun get(p: BulleProfile, f: ProfileField): Boolean? = when (f) {
        ProfileField.ISLAND -> p.island
        ProfileField.HALO -> p.halo
        ProfileField.EDGES -> p.edges
        ProfileField.VOLET -> p.volet
        ProfileField.STANDBY -> p.standby
        ProfileField.ALERTS -> p.alerts
        ProfileField.HAPTICS -> p.haptics
    }

    fun set(p: BulleProfile, f: ProfileField, v: Boolean?): BulleProfile = when (f) {
        ProfileField.ISLAND -> p.copy(island = v)
        ProfileField.HALO -> p.copy(halo = v)
        ProfileField.EDGES -> p.copy(edges = v)
        ProfileField.VOLET -> p.copy(volet = v)
        ProfileField.STANDBY -> p.copy(standby = v)
        ProfileField.ALERTS -> p.copy(alerts = v)
        ProfileField.HAPTICS -> p.copy(haptics = v)
    }

    /** Réglages après application du profil [p] (logique pure, testée). */
    fun applyTo(s: BulleSettings, p: BulleProfile): BulleSettings {
        var r = s
        p.island?.let { r = r.copy(islandEnabled = it) }
        p.halo?.let { r = r.copy(halo = r.halo.copy(enabled = it)) }
        p.edges?.let { r = r.copy(edges = r.edges.copy(enabled = it)) }
        p.volet?.let { r = r.copy(volet = r.volet.copy(enabled = it)) }
        p.standby?.let { r = r.copy(standby = r.standby.copy(enabled = it)) }
        p.alerts?.let { r = r.copy(alerts = r.alerts.copy(enabled = it)) }
        p.haptics?.let { r = r.copy(motion = r.motion.copy(haptics = it)) }
        return r.copy(profiles = r.profiles.copy(activeId = p.id))
    }

    /** Profil reprenant l'état actuel (tous les champs fixés). */
    fun snapshot(s: BulleSettings, id: String, name: String, emoji: String = "⭐") = BulleProfile(
        id, name, emoji,
        island = s.islandEnabled, halo = s.halo.enabled, edges = s.edges.enabled, volet = s.volet.enabled,
        standby = s.standby.enabled, alerts = s.alerts.enabled, haptics = s.motion.haptics,
    )

    /** Applique le profil [id] (geste, règle, écran Profils). */
    fun apply(context: Context, id: String) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            var name: String? = null
            SettingsRepository.update(app) { s ->
                val p = s.profiles.list.firstOrNull { it.id == id } ?: return@update s
                name = p.name
                applyTo(s, p)
            }
            IslandLog.log(name?.let { "Profil : $it" } ?: "Profil introuvable : $id")
        }
    }

    fun newId(): String = "p" + System.currentTimeMillis().toString(36)
}
