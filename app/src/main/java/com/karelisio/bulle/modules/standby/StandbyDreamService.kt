package com.karelisio.bulle.modules.standby

import android.service.dreams.DreamService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.island.IslandOcclusion
import com.karelisio.bulle.island.overlay.OverlayOwner
import com.karelisio.bulle.modules.standby.ui.StandbyInputs
import com.karelisio.bulle.modules.standby.ui.StandbyScreen
import com.karelisio.bulle.modules.standby.ui.rememberNow
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar

/**
 * Veille comme économiseur d'écran Android (Paramètres › Affichage › Économiseur d'écran,
 * quand le système le propose). Même écran que l'activité de secours ; le cycle de vie Compose
 * est celui des overlays de l'île ([OverlayOwner]).
 */
class StandbyDreamService : DreamService() {

    private val owner = OverlayOwner()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        owner.onCreate()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BulleTheme(dark = true) {
                    val settings by remember { SettingsRepository.flow(this@StandbyDreamService) }.collectAsState(BulleSettings())
                    val controller by IslandAccessibilityService.controller.collectAsState()
                    val contents by remember(controller) { controller?.contents ?: flowOf(emptyList()) }.collectAsState(emptyList())
                    val echo by StandbyState.echo.collectAsState()
                    val now = rememberNow(seconds = false)
                    val s = settings.standby
                    val c = Calendar.getInstance().apply { timeInMillis = now }
                    val night = StandbyLogic.isNight(
                        s.nightMode, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), s.nightStart, s.nightEnd, null, s.darkLux,
                    )
                    StandbyScreen(StandbyInputs(s, night, contents, controller, echo), exiting = false, onExited = {})
                }
            }
        }
        owner.attachTo(view)
        setContentView(view)
        owner.onResume()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        StandbyState.setDreaming(true)
        StandbyState.setActive(true)
        IslandOcclusion.set(StandbyActivity.OCCLUSION_TAG, true)
        IslandLog.log("Veille : économiseur d'écran")
    }

    override fun onDreamingStopped() {
        StandbyState.setDreaming(false)
        StandbyState.setActive(false)
        IslandOcclusion.set(StandbyActivity.OCCLUSION_TAG, false)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        owner.onDestroy()
        super.onDetachedFromWindow()
    }
}
