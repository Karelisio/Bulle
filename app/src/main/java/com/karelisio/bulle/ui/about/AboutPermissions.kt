package com.karelisio.bulle.ui.about

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.notifications.BulleNotificationListener
import com.karelisio.bulle.system.Reliability

/** Une autorisation utilisée par Bulle, sa raison et la page système qui la révoque. */
internal data class BullePermission(
    val id: String,
    @StringRes val title: Int,
    @StringRes val why: Int,
    val icon: ImageVector,
    /** État actuel, ou null si non applicable (toujours accordée). */
    val granted: (Context) -> Boolean?,
    /** Page système pour la gérer, ou null s'il n'y en a pas. */
    val settingsIntent: ((Context) -> Intent)?,
    /** Commande adb de révocation (WRITE_SECURE_SETTINGS). */
    val adbCommand: ((Context) -> String)? = null,
)

private fun Context.has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun packageUri(action: String) = { context: Context -> Intent(action, Uri.parse("package:${context.packageName}")) }

internal val bullePermissions: List<BullePermission> = listOf(
    BullePermission(
        "accessibility", R.string.perm_accessibility, R.string.perm_accessibility_why, Icons.Rounded.Accessibility,
        granted = { Reliability.isAccessibilityEnabled(it) },
        settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
    ),
    BullePermission(
        "listener", R.string.perm_listener, R.string.perm_listener_why, Icons.Rounded.NotificationsActive,
        granted = { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) },
        settingsIntent = { context ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, BulleNotificationListener::class.java).flattenToString(),
            )
        },
    ),
    BullePermission(
        "notifications", R.string.perm_notifications, R.string.perm_notifications_why, Icons.Rounded.Notifications,
        granted = { NotificationManagerCompat.from(it).areNotificationsEnabled() },
        settingsIntent = { context ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
    ),
    BullePermission(
        "install", R.string.perm_install, R.string.perm_install_why, Icons.Rounded.InstallMobile,
        granted = { it.packageManager.canRequestPackageInstalls() },
        settingsIntent = packageUri(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
    ),
    BullePermission(
        "battery", R.string.perm_battery, R.string.perm_battery_why, Icons.Rounded.BatteryChargingFull,
        granted = { (it.getSystemService(PowerManager::class.java))?.isIgnoringBatteryOptimizations(it.packageName) },
        settingsIntent = { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
    ),
    BullePermission(
        "bluetooth", R.string.perm_bluetooth, R.string.perm_bluetooth_why, Icons.Rounded.Bluetooth,
        granted = { it.has(Manifest.permission.BLUETOOTH_CONNECT) },
        settingsIntent = ::appDetails,
    ),
    BullePermission(
        "location", R.string.perm_location, R.string.perm_location_why, Icons.Rounded.LocationOn,
        granted = { it.has(Manifest.permission.ACCESS_FINE_LOCATION) || it.has(Manifest.permission.ACCESS_COARSE_LOCATION) },
        settingsIntent = ::appDetails,
    ),
    BullePermission(
        "images", R.string.perm_images, R.string.perm_images_why, Icons.Rounded.Image,
        granted = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.has(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                it.has(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        },
        settingsIntent = ::appDetails,
    ),
    BullePermission(
        "write_settings", R.string.perm_write_settings, R.string.perm_write_settings_why, Icons.Rounded.SettingsSuggest,
        granted = { Settings.System.canWrite(it) },
        settingsIntent = packageUri(Settings.ACTION_MANAGE_WRITE_SETTINGS),
    ),
    BullePermission(
        "secure_settings", R.string.perm_secure_settings, R.string.perm_secure_settings_why, Icons.Rounded.AdminPanelSettings,
        granted = { Reliability.canWriteSecureSettings(it) },
        settingsIntent = null,
        adbCommand = { "adb shell pm revoke ${it.packageName} android.permission.WRITE_SECURE_SETTINGS" },
    ),
    BullePermission(
        "internet", R.string.perm_internet, R.string.perm_internet_why, Icons.Rounded.Language,
        granted = { null },
        settingsIntent = null,
    ),
)

/** Ouvre la page système ; repli sur la fiche de l'appli si l'appareil ne la propose pas. */
internal fun openPermissionSettings(context: Context, permission: BullePermission) {
    val intent = permission.settingsIntent?.invoke(context) ?: return
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        runCatching { context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
