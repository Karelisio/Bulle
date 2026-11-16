package com.karelisio.bulle.modules.volet.qs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Écran « Point d'accès » selon les surcouches, et état lu dans la diffusion système (logique testée). */
object HotspotIntents {
    private const val SETTINGS = "com.android.settings"

    /** États du point d'accès (WifiManager.WIFI_AP_STATE_*) : 12 = activation, 13 = activé. */
    fun isActive(apState: Int): Boolean = apState == 12 || apState == 13

    /**
     * Candidats, du plus précis au plus général : page Wi-Fi du point d'accès (AOSP, MagicOS),
     * page « Partage de connexion », puis réseaux sans fil. Jamais les Paramètres généraux.
     */
    fun candidates(): List<Intent> = listOf(
        Intent("com.android.settings.WIFI_TETHER_SETTINGS"),
        component("com.android.settings.Settings\$WifiTetherSettingsActivity"),
        component("com.android.settings.Settings\$WifiApSettingsActivity"),
        Intent("android.settings.TETHER_SETTINGS"),
        component("com.android.settings.Settings\$TetherSettingsActivity"),
        component("com.android.settings.TetherSettings"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Premier candidat ouvrable ([opens] : activité trouvée ET exportée), sinon le dernier. */
    fun pick(candidates: List<Intent>, opens: (Intent) -> Boolean): Intent =
        candidates.firstOrNull(opens) ?: candidates.last()

    fun resolve(context: Context): Intent = pick(candidates()) { intent ->
        runCatching {
            val info = context.packageManager.resolveActivity(intent, 0)?.activityInfo
            info != null && info.exported && info.packageName != context.packageName
        }.getOrDefault(false)
    }

    private fun component(cls: String) = Intent().setComponent(ComponentName(SETTINGS, cls))
}
