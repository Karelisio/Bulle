package com.karelisio.bulle.testing

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.ContinuationInterceptor

/**
 * Robolectric vide la file du fil principal entre deux tests. Si un test précédent s'est terminé
 * avec une tâche Compose en attente (animation, boîte de dialogue restée ouverte…), le répartiteur
 * global [AndroidUiDispatcher.Main] croit encore qu'une distribution est programmée et n'en
 * reprogramme plus : les LaunchedEffect des tests suivants ne s'exécutent jamais.
 * À appeler dans le @Before des tests qui dépendent d'effets : remet le répartiteur à zéro.
 */
object ComposeDispatcherReset {
    fun unstick() {
        runCatching {
            val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as? AndroidUiDispatcher ?: return
            val cls = AndroidUiDispatcher::class.java
            val lock = cls.getDeclaredField("lock").apply { isAccessible = true }.get(dispatcher)
            synchronized(lock) {
                (cls.getDeclaredField("toRunTrampolined").apply { isAccessible = true }.get(dispatcher) as? MutableCollection<*>)?.clear()
                for (name in listOf("toRunOnFrame", "spareToRunOnFrame")) {
                    (cls.getDeclaredField(name).apply { isAccessible = true }.get(dispatcher) as? MutableList<*>)?.clear()
                }
                for (name in listOf("scheduledTrampolineDispatch", "scheduledFrameDispatch")) {
                    cls.getDeclaredField(name).apply { isAccessible = true }.setBoolean(dispatcher, false)
                }
            }
        }
    }
}
