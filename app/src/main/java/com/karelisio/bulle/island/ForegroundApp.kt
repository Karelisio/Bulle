package com.karelisio.bulle.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appli au premier plan, déduite des seuls événements TYPE_WINDOW_STATE_CHANGED
 * (nom de paquet). Le contenu de l'écran n'est jamais lu.
 */
object ForegroundApp {
    private val _packageName = MutableStateFlow<String?>(null)
    val packageName: StateFlow<String?> = _packageName.asStateFlow()

    // Fenêtres qui ne sont pas « une appli ouverte » (volet système…).
    private val ignored = setOf("com.android.systemui")

    // Claviers installés : leur fenêtre n'est pas une appli (sinon l'île réapparaîtrait pendant
    // une saisie dans une appli où elle est masquée). Mis à jour par le service d'accessibilité.
    @Volatile
    var inputMethods: Set<String> = emptySet()

    fun onWindowChanged(pkg: String?) {
        if (pkg.isNullOrBlank() || pkg in ignored || pkg in inputMethods) return
        _packageName.value = pkg
    }

    /** Relit la liste des claviers installés. */
    fun refreshInputMethods(context: android.content.Context) {
        inputMethods = runCatching {
            context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .inputMethodList.mapTo(HashSet()) { it.packageName }
        }.getOrDefault(emptySet())
    }
}
