package com.karelisio.bulle.ui.home

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.standby.StandbyNightMode

/** Usages proposés par l'assistant. */
enum class Usage { MUSIC, NOTIFICATIONS, BEDSIDE, GESTURES }

/**
 * Assistant « Je veux… » (logique pure, testée) : active et règle d'un coup les modules utiles à
 * chaque usage choisi. Ne coupe jamais rien : ce qui n'est pas concerné reste tel quel.
 */
object UsageSetup {
    fun apply(s: BulleSettings, usages: Set<Usage>): BulleSettings {
        var r = s
        fun module(id: String) { r = r.copy(modules = r.modules + (id to true)) }
        if (Usage.MUSIC in usages) {
            module("music")
            module("volume")
            r = r.copy(
                music = r.music.copy(lyricsEnabled = true),
                halo = r.halo.copy(artworkForMusic = true),
            )
        }
        if (Usage.NOTIFICATIONS in usages) {
            module("messages")
            r = r.copy(
                volet = r.volet.copy(enabled = true),
                halo = r.halo.copy(enabled = true, ignoreSilent = true, ignoreOngoing = true),
                alerts = r.alerts.copy(enabled = true),
            )
        }
        if (Usage.BEDSIDE in usages) {
            module("weather")
            module("calendar")
            r = r.copy(
                standby = r.standby.copy(enabled = true, autoLaunch = true, nightMode = StandbyNightMode.AUTO),
                halo = r.halo.copy(muteInDnd = true),
            )
        }
        if (Usage.GESTURES in usages) {
            r = r.copy(edges = r.edges.copy(enabled = true))
        }
        return r
    }
}
