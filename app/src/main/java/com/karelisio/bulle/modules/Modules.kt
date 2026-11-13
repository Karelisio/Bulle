package com.karelisio.bulle.modules

import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.core.island.Panels
import com.karelisio.bulle.modules.alerts.AlertsModule
import com.karelisio.bulle.modules.call.CallModule
import com.karelisio.bulle.modules.deck.DeckPanel
import com.karelisio.bulle.modules.echo.EchoModule
import com.karelisio.bulle.modules.live.LiveModule
import com.karelisio.bulle.modules.music.MusicModule
import com.karelisio.bulle.modules.push.PushModule

/** Liste des modules de l'île. Ajouter un module = une ligne ici. */
object Modules {
    fun registerAll() {
        ModuleRegistry.register(PushModule)
        ModuleRegistry.register(MusicModule)
        ModuleRegistry.register(LiveModule)
        ModuleRegistry.register(CallModule)
        ModuleRegistry.register(AlertsModule)
        ModuleRegistry.register(EchoModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.calendar.CalendarModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.weather.WeatherModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.focus.FocusModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.volume.VolumeModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.external.ExternalModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.messages.MessagesModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.network.NetworkModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.battery.BatteryModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.countdown.CountdownModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.screentime.ScreenTimeModule)
        ModuleRegistry.register(com.karelisio.bulle.modules.capture.CaptureModule)
        Panels.deck = DeckPanel
    }
}
