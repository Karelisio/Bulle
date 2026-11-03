package com.karelisio.bulle.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Raccourcis vers les réglages propres aux fabricants (gestion du lancement des applis,
 * économie d'énergie). Les écrans visés ne sont pas documentés : on essaie une liste
 * d'activités connues dans l'ordre, puis on se replie sur la fiche de l'appli.
 */
object OemSettings {

    private const val TAG = "OemSettings"

    /** Famille de fabricant, pour adapter les explications. */
    enum class Family { HONOR, OTHER_AGGRESSIVE, STOCK }

    /** Activités « Lancement d'applis » de MagicOS / EMUI, par ordre de préférence. */
    val honorLaunchComponents: List<ComponentName> = listOf(
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )

    /** Écrans « démarrage auto » / batterie d'autres surcouches agressives. */
    val otherLaunchComponents: List<ComponentName> = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
    )

    private val honorBrands = setOf("honor", "huawei")
    private val aggressiveBrands = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo", "samsung", "asus", "meizu", "tecno", "infinix",
    )

    /** Classe un fabricant (valeur de [Build.MANUFACTURER]). */
    fun familyOf(manufacturer: String): Family {
        val m = manufacturer.trim().lowercase()
        return when {
            m in honorBrands -> Family.HONOR
            m in aggressiveBrands -> Family.OTHER_AGGRESSIVE
            else -> Family.STOCK
        }
    }

    val family: Family get() = familyOf(Build.MANUFACTURER.orEmpty())

    /** Intent de la fiche système de Bulle (autorisations, batterie, paramètres restreints). */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Ouvre le gestionnaire de lancement des applis du fabricant, sinon la fiche de l'appli.
     * Renvoie vrai si un écran du fabricant a pu être ouvert.
     */
    fun openLaunchManager(context: Context): Boolean {
        val candidates = if (family == Family.HONOR) honorLaunchComponents + otherLaunchComponents
        else otherLaunchComponents + honorLaunchComponents
        for (component in candidates) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Absent sur cette version : on essaie le suivant.
            } catch (e: SecurityException) {
                Log.d(TAG, "Activité protégée : $component", e)
            }
        }
        openSafely(context, appDetailsIntent(context))
        return false
    }

    /** Lance un écran système sans planter s'il n'existe pas ; replie sur la fiche de l'appli. */
    fun openSafely(context: Context, intent: Intent, fallbackToDetails: Boolean = true): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Écran indisponible : ${intent.action}", e)
        if (fallbackToDetails && intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            try {
                context.startActivity(appDetailsIntent(context))
            } catch (_: Exception) {
                // Rien d'autre à tenter.
            }
        }
        false
    }
}
