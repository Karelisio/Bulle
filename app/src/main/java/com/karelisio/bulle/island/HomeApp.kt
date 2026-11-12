package com.karelisio.bulle.island

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Launcher par défaut (écran d'accueil). Résolu une fois puis mis en cache : la résolution
 * passe par le gestionnaire de paquets, inutile de la refaire à chaque changement de fenêtre.
 * [refresh] la relance (ex. au démarrage du service ou quand le launcher par défaut change).
 */
object HomeApp {
    @Volatile
    private var cached: String? = null

    @Volatile
    private var resolved = false

    /** Paquet du launcher par défaut, ou null s'il n'y en a pas (ou si c'est le sélecteur système). */
    fun packageName(context: Context): String? {
        if (!resolved) refresh(context)
        return cached
    }

    /** Relit le launcher par défaut et retourne son paquet. */
    fun refresh(context: Context): String? {
        val pkg = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
        // Sans launcher par défaut choisi, Android résout vers son sélecteur (« android ») : ce n'est
        // pas un écran d'accueil.
        cached = pkg?.takeIf { it.isNotBlank() && it != "android" }
        resolved = true
        return cached
    }
}
