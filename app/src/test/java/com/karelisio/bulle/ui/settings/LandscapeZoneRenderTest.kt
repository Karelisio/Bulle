package com.karelisio.bulle.ui.settings

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import com.karelisio.bulle.modules.volet.media.OutputButton
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Aperçu de la zone tactile du paysage et bouton de sortie audio : rendu sans plantage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LandscapeZoneRenderTest {
    @Test
    fun rendersPreviewAndOutputButton() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            Column {
                LandscapeZoneSetting(8f) {}
                OutputButton(Icons.Rounded.Headphones, Color.DarkGray, Color.White, onClick = {}, onLongClick = {})
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}
