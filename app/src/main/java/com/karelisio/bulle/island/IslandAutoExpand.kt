package com.karelisio.bulle.island

/** Ouverture automatique d'un contenu (logique pure, testée). */
object IslandAutoExpand {
    /** En paysage (île verticale, invisible), seuls les appels s'ouvrent d'eux-mêmes. */
    fun allowed(vertical: Boolean, moduleId: String): Boolean =
        !vertical || moduleId == com.karelisio.bulle.modules.call.CallModule.ID
}
