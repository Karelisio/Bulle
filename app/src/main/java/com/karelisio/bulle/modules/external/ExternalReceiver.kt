package com.karelisio.bulle.modules.external

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.modules.ecosystem.Ecosystem

/**
 * Point d'entrée de l'API d'intégration (actions SHOW / UPDATE / DISMISS).
 *
 * Exporté mais protégé par la permission `com.karelisio.bulle.permission.SHOW`
 * (signature ou certificat connu) : seules les applis de confiance peuvent l'atteindre.
 *
 * Identité de l'appelant (la clé d'un contenu = paquet appelant + id) — jamais un extra texte :
 *  1. Android 14+ : [getSentFromPackage], si l'appli a partagé son identité
 *     (`BroadcastOptions.setShareIdentityEnabled(true)`) ;
 *  2. sinon l'extra `sender` : un PendingIntent créé par l'appli, dont `creatorPackage`
 *     est fourni par le système et ne peut pas être falsifié.
 * Si les deux sont présents et diffèrent, le broadcast est refusé. L'identité retenue doit
 * aussi détenir la permission (sauf Bulle elle-même).
 */
class ExternalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Extras d'une autre appli : un Parcelable inconnu peut faire échouer la lecture.
        runCatching {
            val systemSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) sentFromPackage else null
            val caller = resolveCaller(context, intent, systemSender)
            if (caller == null) {
                IslandLog.log("Intégrations : broadcast refusé : identité de l'appelant inconnue")
                return
            }
            val command = parse(intent, caller)
            val accepted = ExternalModule.handle(context, command)
            if (!accepted) IslandLog.log("Intégrations : contenu ignoré de $caller (${(command as? ExternalCommand.Invalid)?.reason ?: "appli bloquée"})")
        }.onFailure { IslandLog.log("Intégrations : broadcast illisible : ${it.javaClass.simpleName}") }
    }

    companion object {
        /** Paquet appelant vérifié, ou null si inconnu, contradictoire ou non autorisé. */
        fun resolveCaller(context: Context, intent: Intent, systemSender: String?): String? {
            val token = senderToken(intent)
            val fromToken = token?.creatorPackage
            val caller = when {
                systemSender != null && fromToken != null && systemSender != fromToken -> return null
                else -> systemSender ?: fromToken
            } ?: return null
            if (caller == context.packageName) return caller
            val granted = context.packageManager.checkPermission(ExternalApi.PERMISSION_SHOW, caller) == PackageManager.PERMISSION_GRANTED
            return caller.takeIf { granted }
        }

        /** Analyse l'Intent avec le préréglage de l'appli (écosystème Karelisio). */
        fun parse(intent: Intent, caller: String): ExternalCommand {
            val extras = intent.extras ?: Bundle.EMPTY
            return ExternalParser.parse(
                action = intent.action,
                extras = ExternalExtras { key -> runCatching { @Suppress("DEPRECATION") extras.get(key) }.getOrNull() },
                caller = caller,
                nowWall = System.currentTimeMillis(),
                nowElapsed = SystemClock.elapsedRealtime(),
                preset = Ecosystem.presetFor(caller),
            )
        }

        private fun senderToken(intent: Intent): PendingIntent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(ExternalApi.EXTRA_SENDER, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(ExternalApi.EXTRA_SENDER) as? PendingIntent
            }
    }
}
