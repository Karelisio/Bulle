package com.karelisio.bulle.ui.onboarding

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import com.karelisio.bulle.notifications.NotificationRepository
import com.karelisio.bulle.system.KeepAliveService
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.system.Reliability

/** Toutes les autorisations dont Bulle peut avoir besoin. */
enum class PermissionKey {
    /** Android 13+ : APK hors Play Store, « Autoriser les paramètres restreints ». Non vérifiable. */
    RESTRICTED,
    ACCESSIBILITY,
    NOTIFICATION_ACCESS,
    POST_NOTIFICATIONS,
    INSTALL_PACKAGES,
    BATTERY,

    /** Gestion du lancement des applis (MagicOS et autres surcouches). Non vérifiable. */
    OEM_LAUNCH,
    BLUETOOTH,
    LOCATION,
    MEDIA_IMAGES,
    WRITE_SETTINGS,
    SECURE_SETTINGS,

    /** Accès « Ne pas déranger » (tuile du Volet). */
    DND_ACCESS,
}

/** Commande adb qui accorde WRITE_SECURE_SETTINGS (réactivation automatique de l'accessibilité). */
const val ADB_GRANT_COMMAND = "adb shell pm grant com.karelisio.bulle android.permission.WRITE_SECURE_SETTINGS"

/** État de santé du service de l'île. */
enum class ServiceHealth {
    /** Relié et actif. */
    RUNNING,

    /** Coché dans les réglages mais coupé par le système. */
    KILLED,

    /** Décoché (ou retiré par le système). */
    DISABLED,
}

/** Logique pure, testable sans appareil. */
object PermissionLogic {

    /** Autorisations présentées pendant la prise en main, dans l'ordre. */
    fun onboardingSteps(sdk: Int, family: OemSettings.Family, volet: Boolean = false): List<PermissionKey> = buildList {
        if (sdk >= 33) add(PermissionKey.RESTRICTED)
        add(PermissionKey.ACCESSIBILITY)
        add(PermissionKey.NOTIFICATION_ACCESS)
        if (sdk >= 33) add(PermissionKey.POST_NOTIFICATIONS)
        add(PermissionKey.INSTALL_PACKAGES)
        add(PermissionKey.BATTERY)
        if (family != OemSettings.Family.STOCK) add(PermissionKey.OEM_LAUNCH)
        // Réglages rapides du Volet (luminosité, Ne pas déranger) : seulement s'il est activé.
        if (volet) addAll(voletSteps)
    }

    /** Autorisations propres au Volet (en plus de l'accessibilité et de l'accès aux notifications). */
    val voletSteps: List<PermissionKey> = listOf(PermissionKey.WRITE_SETTINGS, PermissionKey.DND_ACCESS)

    /** Autorisations nécessaires au Volet, dans l'ordre d'affichage de ses réglages. */
    val voletRequired: List<PermissionKey> =
        listOf(PermissionKey.ACCESSIBILITY, PermissionKey.NOTIFICATION_ACCESS) + voletSteps

    /** Autorisations demandées seulement à l'activation d'une fonction. */
    val onDemand: List<PermissionKey> = listOf(
        PermissionKey.BLUETOOTH,
        PermissionKey.LOCATION,
        PermissionKey.MEDIA_IMAGES,
        PermissionKey.WRITE_SETTINGS,
        PermissionKey.DND_ACCESS,
    )

    /** Autorisation d'exécution Android correspondante, ou null si c'est un réglage spécial. */
    fun runtimePermission(key: PermissionKey, sdk: Int): String? = when (key) {
        PermissionKey.POST_NOTIFICATIONS -> if (sdk >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        PermissionKey.BLUETOOTH -> Manifest.permission.BLUETOOTH_CONNECT
        PermissionKey.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        PermissionKey.MEDIA_IMAGES ->
            if (sdk >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        else -> null
    }

    /** Autorisation qu'on ne peut que déclarer faite (« C'est fait »). */
    fun isManual(key: PermissionKey) = key == PermissionKey.RESTRICTED || key == PermissionKey.OEM_LAUNCH

    /**
     * Les paramètres restreints sont forcément levés si l'accessibilité ou l'accès aux
     * notifications a pu être accordé : inutile de redemander.
     */
    fun restrictedSatisfied(confirmed: Boolean, accessibility: Boolean, notificationAccess: Boolean) =
        confirmed || accessibility || notificationAccess

    fun diagnose(enabledInSystem: Boolean, running: Boolean): ServiceHealth = when {
        running -> ServiceHealth.RUNNING
        enabledInSystem -> ServiceHealth.KILLED
        else -> ServiceHealth.DISABLED
    }
}

/** Lecture de l'état réel des autorisations et des écrans système correspondants. */
object PermissionChecks {

    private const val PREFS = "bulle_permissions"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Confirmations manuelles (« C'est fait »), gardées localement. */
    fun isConfirmed(context: Context, key: PermissionKey) = prefs(context).getBoolean("done_${key.name}", false)

    fun setConfirmed(context: Context, key: PermissionKey, done: Boolean) =
        prefs(context).edit { putBoolean("done_${key.name}", done) }

    /** Une autorisation d'exécution a-t-elle déjà été demandée (pour détecter un refus définitif) ? */
    fun wasRequested(context: Context, key: PermissionKey) = prefs(context).getBoolean("asked_${key.name}", false)

    fun markRequested(context: Context, key: PermissionKey) = prefs(context).edit { putBoolean("asked_${key.name}", true) }

    fun isGranted(context: Context, key: PermissionKey): Boolean {
        PermissionLogic.runtimePermission(key, Build.VERSION.SDK_INT)?.let { perm ->
            return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
        return when (key) {
            PermissionKey.RESTRICTED -> PermissionLogic.restrictedSatisfied(
                isConfirmed(context, key),
                Reliability.isAccessibilityEnabled(context),
                NotificationRepository.isAccessGranted(context),
            )
            PermissionKey.ACCESSIBILITY -> Reliability.isAccessibilityEnabled(context)
            PermissionKey.NOTIFICATION_ACCESS -> NotificationRepository.isAccessGranted(context)
            // Avant Android 13, la notification n'est pas une autorisation d'exécution.
            PermissionKey.POST_NOTIFICATIONS -> true
            PermissionKey.INSTALL_PACKAGES -> context.packageManager.canRequestPackageInstalls()
            PermissionKey.BATTERY -> isIgnoringBatteryOptimizations(context)
            PermissionKey.OEM_LAUNCH -> isConfirmed(context, key)
            PermissionKey.WRITE_SETTINGS -> Settings.System.canWrite(context)
            PermissionKey.SECURE_SETTINGS -> Reliability.canWriteSecureSettings(context)
            PermissionKey.DND_ACCESS -> isDndAccessGranted(context)
            else -> false
        }
    }

    /** État de toutes les autorisations, d'un coup. */
    fun snapshot(context: Context): Map<PermissionKey, Boolean> =
        PermissionKey.entries.associateWith { isGranted(context, it) }

    fun isDndAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Le service de maintien tourne-t-il ? (getRunningServices renvoie encore nos propres services.) */
    @Suppress("DEPRECATION")
    fun isKeepAliveRunning(context: Context): Boolean = try {
        context.getSystemService(ActivityManager::class.java)
            ?.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == KeepAliveService::class.java.name && it.service.packageName == context.packageName } == true
    } catch (_: SecurityException) {
        false
    }

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    /** Écran système où se règle l'autorisation (null pour WRITE_SECURE_SETTINGS : adb seulement). */
    @Suppress("BatteryLife") // Bulle est un overlay permanent : l'exemption est sa raison d'être sur MagicOS.
    fun settingsIntent(context: Context, key: PermissionKey): Intent? = when (key) {
        PermissionKey.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PermissionKey.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                NotificationRepository.component(context).flattenToString(),
            )
        PermissionKey.INSTALL_PACKAGES -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri(context))
        PermissionKey.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        PermissionKey.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context))
        PermissionKey.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        PermissionKey.SECURE_SETTINGS -> null
        PermissionKey.OEM_LAUNCH -> null // Voir OemSettings.openLaunchManager.
        else -> OemSettings.appDetailsIntent(context)
    }

    /** Ouvre l'écran système d'une autorisation (hors autorisations d'exécution). */
    fun openSettings(context: Context, key: PermissionKey) {
        if (key == PermissionKey.OEM_LAUNCH) {
            OemSettings.openLaunchManager(context)
            return
        }
        val intent = settingsIntent(context, key) ?: return
        if (key == PermissionKey.NOTIFICATION_ACCESS) {
            // Certaines surcouches n'ont pas l'écran de détail : liste générale.
            if (!OemSettings.openSafely(context, intent, fallbackToDetails = false)) {
                OemSettings.openSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        } else {
            OemSettings.openSafely(context, intent)
        }
    }
}
