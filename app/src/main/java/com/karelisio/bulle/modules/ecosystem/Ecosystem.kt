package com.karelisio.bulle.modules.ecosystem

import android.app.BroadcastOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.modules.external.BreathPattern
import com.karelisio.bulle.modules.external.ExternalModule
import com.karelisio.bulle.modules.external.ExternalPreset
import com.karelisio.bulle.modules.external.ExternalPriority
import com.karelisio.bulle.modules.external.ExternalSpec
import com.karelisio.bulle.modules.external.ExternalTemplate
import com.karelisio.bulle.modules.external.ui.BreathingPanel

/**
 * Applis Karelisio connues de Bulle : préréglage d'affichage (modèle, couleur, priorité) appliqué
 * quand l'appli n'envoie pas d'extra `template` / `accentColor` / `priority`.
 */
enum class KarelisioApp(
    val packageName: String,
    @StringRes val nameRes: Int,
    @StringRes val roleRes: Int,
    val preset: ExternalPreset,
) {
    ECHO("com.karelisio.echo", R.string.eco_echo, R.string.eco_echo_role, ExternalPreset(ExternalTemplate.HEART, 0xFFFF6FA5.toInt(), ExternalPriority.ALERT)),
    SOMNA("com.karelisio.somna", R.string.eco_somna, R.string.eco_somna_role, ExternalPreset(ExternalTemplate.PROGRESS, 0xFF9C8CFF.toInt())),
    EXIT("com.karelisio.exit", R.string.eco_exit, R.string.eco_exit_role, ExternalPreset(ExternalTemplate.CHRONOMETER, 0xFF5BD68A.toInt())),
    MAGO("com.karelisio.mago", R.string.eco_mago, R.string.eco_mago_role, ExternalPreset(ExternalTemplate.LIST, 0xFFFFA24D.toInt())),
    ORBIT("com.karelisio.orbit", R.string.eco_orbit, R.string.eco_orbit_role, ExternalPreset(ExternalTemplate.COUNTDOWN, 0xFF6FB6FF.toInt())),
    MIROIR("com.karelisio.miroir", R.string.eco_miroir, R.string.eco_miroir_role, ExternalPreset(ExternalTemplate.STANDARD, 0xFFC7A6FF.toInt())),
    RESERVE("com.karelisio.reserve", R.string.eco_reserve, R.string.eco_reserve_role, ExternalPreset(ExternalTemplate.LIST, 0xFFFFC14D.toInt())),
    VIGIE("com.karelisio.vigie", R.string.eco_vigie, R.string.eco_vigie_role, ExternalPreset(ExternalTemplate.STATUS, 0xFF4DD0C4.toInt())),
    TRAME("com.karelisio.trame", R.string.eco_trame, R.string.eco_trame_role, ExternalPreset(ExternalTemplate.STANDARD, 0xFFE8A27C.toInt())),
    ;

    companion object {
        fun of(packageName: String): KarelisioApp? = entries.firstOrNull { it.packageName == packageName }
    }
}

/**
 * Expériences propres aux applis Karelisio : intentions explicites envoyées par Bulle
 * (l'appli cible les reçoit avec un BroadcastReceiver, voir docs/INTEGRATION.md).
 * Bulle ne lit jamais leurs données : elle ne fait que demander, l'appli répond via l'API.
 */
object Ecosystem {
    const val ACTION_SEND_THOUGHT = "com.karelisio.echo.action.SEND_THOUGHT"
    const val ACTION_CRAVING = "com.karelisio.exit.action.CRAVING"
    const val ACTION_REQUEST_FUEL = "com.karelisio.reserve.action.REQUEST_FUEL"

    /** Extra ajouté à chaque intention : paquet de Bulle (informatif ; vérifier sentFromPackage). */
    const val EXTRA_SOURCE = "source"

    private const val FEEDBACK_ID = "bulle-feedback"
    private const val FEEDBACK_MS = 3_000L

    /** Préréglage d'affichage d'une appli connue, ou null. */
    fun presetFor(packageName: String): ExternalPreset? = KarelisioApp.of(packageName)?.preset

    /**
     * Écho : envoie un « pense-à-toi ». Retourne false (avec un retour visuel) si Écho
     * n'est pas installée.
     */
    fun sendThought(context: Context): Boolean {
        val sent = send(context, KarelisioApp.ECHO, ACTION_SEND_THOUGHT)
        feedback(
            context, KarelisioApp.ECHO,
            if (sent) ExternalTemplate.HEART else ExternalTemplate.STANDARD,
            if (sent) R.string.eco_feedback_thought_sent else R.string.eco_feedback_missing,
        )
        return sent
    }

    /**
     * Exit : « Envie de fumer » → ouvre l'exercice de respiration dans l'île et prévient Exit
     * (qui compte l'envie). La respiration s'ouvre même si Exit n'est pas installée.
     */
    fun openCraving(host: IslandHost): Boolean {
        host.openPanel(BreathingPanel(BreathPattern.RELAX_478))
        return send(host.context, KarelisioApp.EXIT, ACTION_CRAVING)
    }

    /** Réserve : demande les prix du carburant ; Réserve répondra en poussant un contenu via l'API. */
    fun requestFuel(context: Context): Boolean {
        val sent = send(context, KarelisioApp.RESERVE, ACTION_REQUEST_FUEL)
        feedback(context, KarelisioApp.RESERVE, ExternalTemplate.STANDARD, if (sent) R.string.eco_feedback_fuel_requested else R.string.eco_feedback_missing)
        return sent
    }

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** Broadcast explicite (setPackage) vers l'appli ; Android 14+ : identité de Bulle partagée. */
    private fun send(context: Context, app: KarelisioApp, action: String): Boolean {
        if (!isInstalled(context, app.packageName)) return false
        val intent = Intent(action)
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_SOURCE, context.packageName)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
                context.sendBroadcast(intent, null, options.toBundle())
            } else {
                context.sendBroadcast(intent)
            }
            true
        }.getOrDefault(false)
    }

    /** Petit retour visuel dans l'île (contenu local, quelques secondes). */
    private fun feedback(context: Context, app: KarelisioApp, template: ExternalTemplate, @StringRes text: Int) {
        val name = context.getString(app.nameRes)
        ExternalModule.pushLocal(
            ExternalSpec(
                packageName = app.packageName,
                id = FEEDBACK_ID,
                template = template,
                title = context.getString(text, name),
                accent = app.preset.accent,
                priority = ExternalPriority.ALERT,
                timeoutMs = FEEDBACK_MS,
                autoExpand = false,
            ),
        )
    }
}
