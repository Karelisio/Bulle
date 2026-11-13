package com.karelisio.bulle.ui.shell

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.bulle.R
import com.karelisio.bulle.ui.Routes

/** Onglets de la coque de l'appli (barre flottante du bas). L'ordre est celui de la barre. */
enum class ShellTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_tab_home, Icons.Rounded.Home),
    LOOK(R.string.nav_tab_look, Icons.Rounded.Palette),
    CONTENTS(R.string.nav_tab_contents, Icons.Rounded.Widgets),
    SETTINGS(R.string.nav_tab_settings, Icons.Rounded.Settings),
}

/** Logique pure de navigation entre onglets (testée en JUnit). */
object ShellNavigation {
    /** Onglet affiché au lancement. */
    val DEFAULT = ShellTab.HOME

    /**
     * Destination d'un appui sur « retour » depuis [current] :
     * un autre onglet revient d'abord à l'accueil ; depuis l'accueil, null (l'appli peut se fermer).
     */
    fun onBack(current: ShellTab): ShellTab? = if (current == DEFAULT) null else DEFAULT

    /** Vrai si la coque doit intercepter « retour ». */
    fun interceptsBack(current: ShellTab): Boolean = onBack(current) != null

    /** Sens de la transition entre deux onglets (+1 vers la droite, -1 vers la gauche, 0 sinon). */
    fun direction(from: ShellTab, to: ShellTab): Int = (to.ordinal - from.ordinal).coerceIn(-1, 1)
}

/** Teinte de la pastille des icônes d'un groupe. */
enum class GroupTint { PRIMARY, SECONDARY, TERTIARY }

/** Une entrée de liste : ouvre un écran de réglages existant. */
data class ShellEntry(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector,
)

/** Un groupe de cartes dans un onglet. */
data class ShellGroup(@StringRes val titleRes: Int, val tint: GroupTint, val entries: List<ShellEntry>)

/** Contenu des onglets de listes (Style, Contenus, Réglages). */
object ShellCatalog {

    fun groups(tab: ShellTab): List<ShellGroup> = when (tab) {
        ShellTab.HOME -> emptyList()
        ShellTab.LOOK -> listOf(
            ShellGroup(
                R.string.nav_group_appearance, GroupTint.PRIMARY,
                listOf(
                    ShellEntry(Routes.STYLE, R.string.home_entry_style, R.string.nav_sum_style, Icons.Rounded.Palette),
                    ShellEntry(Routes.POSITION, R.string.home_entry_position, R.string.nav_sum_position, Icons.Rounded.OpenWith),
                ),
            ),
            ShellGroup(
                R.string.nav_group_behaviour, GroupTint.SECONDARY,
                listOf(
                    ShellEntry(Routes.VISIBILITY, R.string.home_entry_visibility, R.string.nav_sum_visibility, Icons.Rounded.VisibilityOff),
                    ShellEntry(Routes.GESTURES, R.string.gestures_title, R.string.nav_sum_gestures, Icons.Rounded.TouchApp),
                ),
            ),
        )
        ShellTab.CONTENTS -> listOf(
            ShellGroup(
                R.string.nav_group_on_island, GroupTint.PRIMARY,
                listOf(
                    ShellEntry(Routes.MUSIC, R.string.home_entry_music, R.string.nav_sum_music, Icons.Rounded.MusicNote),
                    ShellEntry(Routes.LIVE, R.string.home_entry_live, R.string.nav_sum_live, Icons.Rounded.Timer),
                    ShellEntry(Routes.CALLS, R.string.home_entry_calls, R.string.nav_sum_calls, Icons.Rounded.Call),
                    ShellEntry(Routes.ALERTS, R.string.home_entry_alerts, R.string.nav_sum_alerts, Icons.Rounded.NotificationsActive),
                    ShellEntry(Routes.CALENDAR, R.string.calendar_module_title, R.string.nav_sum_calendar, Icons.Rounded.Event),
                    ShellEntry(Routes.WEATHER, R.string.weather_module_title, R.string.nav_sum_weather, Icons.Rounded.WbSunny),
                    ShellEntry(Routes.FOCUS, R.string.focus_module_title_nav, R.string.nav_sum_focus, Icons.Rounded.HourglassTop),
                    ShellEntry(Routes.MESSAGES, R.string.module_messages, R.string.nav_sum_messages, Icons.Rounded.Forum),
                    ShellEntry(Routes.NETWORK, R.string.network_module_title, R.string.network_nav_summary, Icons.Rounded.NetworkCheck),
                    ShellEntry(Routes.BATTERY, R.string.battery_module_title, R.string.battery_nav_summary, Icons.Rounded.BatteryChargingFull),
                    ShellEntry(Routes.VOLUME, R.string.volume_module_title_nav, R.string.nav_sum_volume, Icons.AutoMirrored.Rounded.VolumeUp),
                ),
            ),
            ShellGroup(
                R.string.nav_group_around, GroupTint.TERTIARY,
                listOf(
                    ShellEntry(Routes.VOLET, R.string.home_entry_volet, R.string.nav_sum_volet, Icons.Rounded.Notifications),
                    ShellEntry(Routes.DECK, R.string.deck_title, R.string.nav_sum_deck, Icons.Rounded.Dashboard),
                    ShellEntry(Routes.RULES, R.string.rules_title, R.string.nav_sum_rules, Icons.Rounded.AutoAwesome),
                    ShellEntry(Routes.INTEGRATIONS, R.string.external_module_title, R.string.nav_sum_integrations, Icons.Rounded.Hub),
                ),
            ),
        )
        ShellTab.SETTINGS -> listOf(
            ShellGroup(
                R.string.nav_group_care, GroupTint.PRIMARY,
                listOf(
                    ShellEntry(Routes.RELIABILITY, R.string.home_entry_reliability, R.string.nav_sum_reliability, Icons.Rounded.HealthAndSafety),
                    ShellEntry(Routes.PERMISSIONS, R.string.home_entry_permissions, R.string.nav_sum_permissions, Icons.Rounded.VerifiedUser),
                ),
            ),
            ShellGroup(
                R.string.home_group_app, GroupTint.SECONDARY,
                listOf(
                    ShellEntry(Routes.BACKUP, R.string.home_entry_backup, R.string.nav_sum_backup, Icons.Rounded.SettingsBackupRestore),
                    ShellEntry(Routes.ABOUT, R.string.home_entry_about, R.string.nav_sum_about, Icons.Rounded.Info),
                ),
            ),
        )
    }

    @StringRes
    fun titleRes(tab: ShellTab): Int = when (tab) {
        ShellTab.HOME -> R.string.nav_tab_home
        ShellTab.LOOK -> R.string.nav_title_look
        ShellTab.CONTENTS -> R.string.nav_title_contents
        ShellTab.SETTINGS -> R.string.nav_title_settings
    }

    @StringRes
    fun subtitleRes(tab: ShellTab): Int = when (tab) {
        ShellTab.HOME -> R.string.home_tagline
        ShellTab.LOOK -> R.string.nav_subtitle_look
        ShellTab.CONTENTS -> R.string.nav_subtitle_contents
        ShellTab.SETTINGS -> R.string.nav_subtitle_settings
    }
}
