package com.karelisio.bulle.modules.music.karaoke

import android.content.Context
import android.content.Intent
import android.util.Log
import com.karelisio.bulle.modules.music.MusicContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pont entre l'île (service d'accessibilité) et la vue karaoké (activité), dans le même processus :
 * le module Musique y publie son contenu courant (morceau, état de lecture, paroles, pochette,
 * réglages) à chaque changement ; l'activité le suit en direct.
 */
object KaraokeBridge {
    private val _content = MutableStateFlow<MusicContent?>(null)

    /** Contenu musique courant (null = plus de session média). */
    val content: StateFlow<MusicContent?> get() = _content

    fun publish(content: MusicContent?) {
        _content.value = content
    }

    /** Ouvre la vue karaoké en plein écran (depuis l'île : nouvelle tâche). */
    fun open(context: Context, content: MusicContent) {
        publish(content)
        val intent = Intent(context, KaraokeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w("BulleMusic", "Vue karaoké refusée", it) }
    }
}
