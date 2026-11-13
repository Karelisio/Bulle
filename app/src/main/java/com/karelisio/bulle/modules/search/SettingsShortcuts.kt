package com.karelisio.bulle.modules.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.normalize

/** Raccourci vers un écran des Réglages système, avec ses mots-clés FR + EN. */
data class SettingsShortcut(
    val id: String,
    val action: String,
    @StringRes val label: Int,
    val keywords: List<String>,
    /** Ajoute « package:com.karelisio.bulle » (écrans propres à l'appli). */
    val withPackage: Boolean = false,
)

/** Liste statique des raccourcis Settings.ACTION_* et leur filtrage (fonction pure). */
object SettingsShortcuts {
    val all: List<SettingsShortcut> = listOf(
        SettingsShortcut("wifi", Settings.ACTION_WIFI_SETTINGS, R.string.search_set_wifi, listOf("wifi", "wi-fi", "wlan", "reseau sans fil", "wireless", "internet")),
        SettingsShortcut("bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, R.string.search_set_bluetooth, listOf("bluetooth", "bt", "appairage", "pairing", "ecouteurs", "headphones")),
        SettingsShortcut("battery", Intent.ACTION_POWER_USAGE_SUMMARY, R.string.search_set_battery, listOf("batterie", "battery", "autonomie", "economie d'energie", "power", "charge")),
        SettingsShortcut("display", Settings.ACTION_DISPLAY_SETTINGS, R.string.search_set_display, listOf("affichage", "ecran", "display", "screen", "luminosite", "brightness", "mode sombre", "dark mode")),
        SettingsShortcut("sound", Settings.ACTION_SOUND_SETTINGS, R.string.search_set_sound, listOf("son", "sound", "sonnerie", "ringtone", "volume", "vibration")),
        SettingsShortcut(
            "notifications",
            // Liste des notifications par appli : Android 13+ ; avant, l'accueil des Réglages.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS,
            R.string.search_set_notifications, listOf("notifications", "notification", "alertes", "alerts")),
        SettingsShortcut("apps", Settings.ACTION_APPLICATION_SETTINGS, R.string.search_set_apps, listOf("applications", "applis", "apps", "application", "app")),
        SettingsShortcut("storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, R.string.search_set_storage, listOf("stockage", "storage", "memoire", "espace", "space")),
        SettingsShortcut("location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, R.string.search_set_location, listOf("localisation", "location", "position", "gps")),
        SettingsShortcut("accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, R.string.search_set_accessibility, listOf("accessibilite", "accessibility", "talkback")),
        SettingsShortcut("developer", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, R.string.search_set_developer, listOf("developpeur", "developer", "options pour les developpeurs", "developer options", "adb", "debogage", "debugging")),
        SettingsShortcut("nfc", Settings.ACTION_NFC_SETTINGS, R.string.search_set_nfc, listOf("nfc", "sans contact", "contactless", "paiement", "payment")),
        SettingsShortcut("mobile_data", Settings.ACTION_DATA_ROAMING_SETTINGS, R.string.search_set_mobile_data, listOf("donnees mobiles", "mobile data", "cellulaire", "cellular", "4g", "5g", "itinerance", "roaming", "sim")),
        SettingsShortcut("airplane", Settings.ACTION_AIRPLANE_MODE_SETTINGS, R.string.search_set_airplane, listOf("mode avion", "avion", "airplane", "flight mode", "plane")),
        SettingsShortcut("hotspot", Settings.ACTION_WIRELESS_SETTINGS, R.string.search_set_network, listOf("reseau", "network", "point d'acces", "hotspot", "partage de connexion", "tethering", "vpn")),
        SettingsShortcut("date", Settings.ACTION_DATE_SETTINGS, R.string.search_set_date, listOf("date", "heure", "time", "fuseau", "timezone")),
        SettingsShortcut("language", Settings.ACTION_LOCALE_SETTINGS, R.string.search_set_language, listOf("langue", "language", "langues", "languages")),
        SettingsShortcut("keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, R.string.search_set_keyboard, listOf("clavier", "keyboard", "saisie", "input")),
        SettingsShortcut("security", Settings.ACTION_SECURITY_SETTINGS, R.string.search_set_security, listOf("securite", "security", "verrouillage", "lock", "empreinte", "fingerprint")),
        SettingsShortcut("privacy", Settings.ACTION_PRIVACY_SETTINGS, R.string.search_set_privacy, listOf("confidentialite", "privacy", "autorisations", "permissions")),
        SettingsShortcut("dnd", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, R.string.search_set_dnd, listOf("ne pas deranger", "do not disturb", "dnd", "silence")),
        SettingsShortcut("cast", Settings.ACTION_CAST_SETTINGS, R.string.search_set_cast, listOf("cast", "diffusion", "projection", "screen mirroring", "miroir")),
        SettingsShortcut("bulle", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, R.string.search_set_bulle, listOf("bulle", "autorisations de bulle", "bulle permissions"), withPackage = true),
        SettingsShortcut("settings", Settings.ACTION_SETTINGS, R.string.search_set_all, listOf("parametres", "reglages", "settings", "options")),
    )

    /**
     * Raccourcis correspondant à [query] (insensible à la casse et aux accents) : un mot-clé qui
     * commence par la saisie (dès 2 lettres), ou dont un mot commence par elle (dès 3 lettres).
     */
    fun filter(query: String, shortcuts: List<SettingsShortcut> = all, limit: Int = 3): List<SettingsShortcut> {
        val q = normalize(query.trim()).replace(Regex("\\s+"), " ")
        if (q.length < 2) return emptyList()
        return shortcuts.mapNotNull { s ->
            val score = s.keywords.minOfOrNull { kw ->
                val k = normalize(kw)
                when {
                    k == q -> 0
                    k.startsWith(q) -> 1
                    q.length >= 3 && k.split(' ', '-').any { it.startsWith(q) } -> 2
                    else -> Int.MAX_VALUE
                }
            } ?: Int.MAX_VALUE
            if (score == Int.MAX_VALUE) null else s to score
        }.sortedBy { it.second }.take(limit).map { it.first }
    }

    fun intent(context: Context, shortcut: SettingsShortcut): Intent =
        Intent(shortcut.action).apply {
            if (shortcut.withPackage) data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Ouvre l'écran ; repli sur l'accueil des Réglages si l'OEM ne le propose pas. */
    fun open(context: Context, shortcut: SettingsShortcut) {
        try {
            context.startActivity(intent(context, shortcut))
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch (e: SecurityException) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
