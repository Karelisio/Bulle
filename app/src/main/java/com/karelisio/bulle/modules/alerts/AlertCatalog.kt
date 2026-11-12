package com.karelisio.bulle.modules.alerts

import android.Manifest

/**
 * Description pure (sans Compose) d'une alerte réglable : lecture / écriture de sa case,
 * permissions demandées à l'activation, et nature ponctuelle ou persistante.
 */
class AlertDef(
    val id: String,
    val get: (AlertSettings) -> Boolean,
    val set: (AlertSettings, Boolean) -> AlertSettings,
    /** Permissions d'exécution demandées à l'activation. */
    val permissions: List<String> = emptyList(),
    /** Si vrai, l'alerte fonctionne aussi sans la permission (en mode réduit). */
    val permissionOptional: Boolean = false,
    /** Nécessite l'accès aux notifications de Bulle. */
    val needsNotificationAccess: Boolean = false,
    /** Contenu persistant (pas de durée d'affichage réglable). */
    val persistent: Boolean = false,
)

/** Catégories repliables de l'écran des alertes, dans l'ordre d'affichage. */
enum class AlertCategory(val alertIds: List<String>) {
    ENERGY(listOf("lowBattery", "charging")),
    NETWORK(listOf("wifi", "cellular", "fiveG", "vpn", "hotspot", "airplane")),
    DEVICES(listOf("bluetooth", "usb")),
    SOUND(listOf("ringer", "mediaVolume", "dnd", "alarm", "torch")),
    PRIVACY(listOf("cameraInUse", "micInUse", "screenshot", "clipboard")),
    APPS(listOf("packages")),
    ECHO(listOf("echo")),
}

/** Compteurs d'une catégorie : « [enabled] sur [total] activées », « [missing] sans autorisation ». */
data class CategoryCount(val enabled: Int, val total: Int, val missing: Int)

object AlertCatalog {
    /** Permission de lecture des images (captures d'écran), selon la version d'Android. */
    private val imagesPermission: String
        get() = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    val defs: List<AlertDef> = listOf(
        AlertDef("lowBattery", { it.lowBattery }, { s, v -> s.copy(lowBattery = v) }),
        AlertDef("charging", { it.charging }, { s, v -> s.copy(charging = v) }),
        AlertDef(
            "bluetooth", { it.bluetooth }, { s, v -> s.copy(bluetooth = v) },
            permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
        ),
        AlertDef(
            "wifi", { it.wifi }, { s, v -> s.copy(wifi = v) },
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            permissionOptional = true,
        ),
        AlertDef("cellular", { it.cellular }, { s, v -> s.copy(cellular = v) }),
        AlertDef(
            "fiveG", { it.fiveG }, { s, v -> s.copy(fiveG = v) },
            permissions = listOf(Manifest.permission.READ_PHONE_STATE),
        ),
        AlertDef("vpn", { it.vpn }, { s, v -> s.copy(vpn = v) }),
        AlertDef("hotspot", { it.hotspot }, { s, v -> s.copy(hotspot = v) }),
        AlertDef("airplane", { it.airplane }, { s, v -> s.copy(airplane = v) }),
        AlertDef("usb", { it.usb }, { s, v -> s.copy(usb = v) }),
        AlertDef("ringer", { it.ringer }, { s, v -> s.copy(ringer = v) }),
        AlertDef("mediaVolume", { it.mediaVolume }, { s, v -> s.copy(mediaVolume = v) }),
        AlertDef("dnd", { it.dnd }, { s, v -> s.copy(dnd = v) }),
        AlertDef(
            "alarm", { it.alarm }, { s, v -> s.copy(alarm = v) },
            needsNotificationAccess = true, persistent = true,
        ),
        AlertDef("torch", { it.torch }, { s, v -> s.copy(torch = v) }, persistent = true),
        AlertDef("cameraInUse", { it.cameraInUse }, { s, v -> s.copy(cameraInUse = v) }, persistent = true),
        AlertDef("micInUse", { it.micInUse }, { s, v -> s.copy(micInUse = v) }, persistent = true),
        AlertDef(
            "screenshot", { it.screenshot }, { s, v -> s.copy(screenshot = v) },
            permissions = listOf(imagesPermission),
        ),
        AlertDef("packages", { it.packages }, { s, v -> s.copy(packages = v) }),
        AlertDef("clipboard", { it.clipboard }, { s, v -> s.copy(clipboard = v) }),
        AlertDef(
            "echo", { it.echoEnabled }, { s, v -> s.copy(echoEnabled = v) },
            needsNotificationAccess = true,
        ),
    )

    private val byId = defs.associateBy { it.id }

    fun def(id: String): AlertDef = byId.getValue(id)

    fun defsOf(category: AlertCategory): List<AlertDef> = category.alertIds.map(::def)

    /** Alerte cochée mais privée de ce dont elle a besoin (permission obligatoire ou accès aux notifications). */
    fun isMissingAccess(def: AlertDef, settings: AlertSettings, granted: (String) -> Boolean, notificationAccess: Boolean): Boolean {
        if (!def.get(settings)) return false
        val permMissing = def.permissions.isNotEmpty() && !def.permissionOptional && !granted(def.permissions.first())
        return permMissing || (def.needsNotificationAccess && !notificationAccess)
    }

    fun count(
        category: AlertCategory,
        settings: AlertSettings,
        granted: (String) -> Boolean,
        notificationAccess: Boolean,
    ): CategoryCount {
        val defs = defsOf(category)
        return CategoryCount(
            enabled = defs.count { it.get(settings) },
            total = defs.size,
            missing = defs.count { isMissingAccess(it, settings, granted, notificationAccess) },
        )
    }
}
