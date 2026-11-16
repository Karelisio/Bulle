package com.karelisio.bulle.modules.music

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import com.karelisio.bulle.modules.music.ui.NextUpLine
import com.karelisio.bulle.modules.music.ui.QueueEntry
import com.karelisio.bulle.modules.music.ui.QueueList
import com.karelisio.bulle.modules.music.ui.QueueTabs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** File d'attente de la bulle Média : aperçu « Ensuite », onglets Paroles | File et liste, sans plantage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueUiRenderTest {
    @Test
    fun rendersNextUpTabsAndList() {
        val entries = (1L..25L).map { QueueEntry(it, "Titre $it", if (it % 2 == 0L) "Artiste $it" else null) }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            Column {
                NextUpLine(entries.first(), Color.Cyan, onPlay = {}, onShowQueue = {})
                QueueTabs(queueSelected = true, accent = Color.Cyan) {}
                QueueList(entries, Color.Cyan) {}
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}
