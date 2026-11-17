package com.karelisio.bulle.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.modules.edges.EdgeBus
import com.karelisio.bulle.modules.halo.HaloBus
import com.karelisio.bulle.notifications.NotificationRepository

/**
 * Rapport de diagnostic à partager : version, appareil, état des services et modules, journal.
 * Aucune donnée personnelle (pas de contenu de notification, pas de liste d'applis).
 */
object DiagnosticReport {

    /** Construit le texte (logique pure pour l'en-tête, testée). */
    fun header(
        version: String,
        device: String,
        sdk: Int,
        family: String,
        lines: List<Pair<String, String>>,
    ): String = buildString {
        appendLine("Rapport Bulle $version")
        appendLine("Appareil : $device (Android API $sdk, $family)")
        lines.forEach { (k, v) -> appendLine("$k : $v") }
    }

    fun build(context: Context, s: BulleSettings): String {
        fun yes(b: Boolean) = if (b) "oui" else "non"
        val lines = listOf(
            "Service de l'île" to yes(IslandAccessibilityService.running.value),
            "Accessibilité cochée" to yes(Reliability.isAccessibilityEnabled(context)),
            "Réactivation auto (WRITE_SECURE_SETTINGS)" to yes(Reliability.canWriteSecureSettings(context)),
            "Accès aux notifications" to yes(NotificationRepository.isAccessGranted(context)) + " / relié " + yes(NotificationRepository.connected.value),
            "Économie d'énergie" to yes(context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true),
            "Île activée" to yes(s.islandEnabled),
            "Volet" to yes(s.volet.enabled),
            "Halo" to "${yes(s.halo.enabled)} (tourne : ${yes(HaloBus.running.value)}, style ${s.halo.style})",
            "Bords" to "${yes(s.edges.enabled)} (tourne : ${yes(EdgeBus.running.value)}, ${s.edges.bars})",
            "Veille" to "${yes(s.standby.enabled)} (auto : ${yes(s.standby.autoLaunch)})",
            "Modules" to s.modules.entries.joinToString { "${it.key}=${if (it.value) 1 else 0}" }.ifEmpty { "par défaut" },
        )
        return header(
            BuildConfig.VERSION_NAME,
            "${Build.MANUFACTURER} ${Build.MODEL}",
            Build.VERSION.SDK_INT,
            OemSettings.family.name,
            lines,
        ) + "\n--- Journal de l'île ---\n" + IslandLog.dump()
    }

    fun share(context: Context, s: BulleSettings, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Rapport Bulle ${BuildConfig.VERSION_NAME}")
            .putExtra(Intent.EXTRA_TEXT, build(context, s))
        runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
