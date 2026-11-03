package com.karelisio.bulle.modules

import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.modules.music.MusicModule
import com.karelisio.bulle.modules.push.PushModule

/** Liste des modules de l'île. Ajouter un module = une ligne ici. */
object Modules {
    fun registerAll() {
        ModuleRegistry.register(PushModule)
        ModuleRegistry.register(MusicModule)
    }
}
