package com.karelisio.bulle.modules.standby

import android.app.KeyguardManager
import android.app.Notification
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.karelisio.bulle.island.AddonEnv
import com.karelisio.bulle.island.ForegroundApp
import com.karelisio.bulle.island.HomeApp
import com.karelisio.bulle.island.IslandAddon
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.battery.BatteryReader
import com.karelisio.bulle.modules.battery.PowerSource
import com.karelisio.bulle.modules.echo.EchoModule
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Détection de Veille, dans le service de l'île : en charge (ACTION_BATTERY_CHANGED) et à
 * l'horizontale (capteur de gravité, écouté seulement pendant la charge), téléphone au repos.
 * Après le délai réglé, lance l'activité de secours — sauf si l'économiseur d'écran du système
 * affiche déjà Veille. L'activité se ferme d'elle-même quand les conditions cessent.
 */
object StandbyAddon : IslandAddon {
    const val ID = "standby"
    override val id = ID
    override fun start(env: AddonEnv): AutoCloseable = StandbyRuntime(env).also { it.start() }
}

private class StandbyRuntime(private val env: AddonEnv) : AutoCloseable {
    private val service = env.service
    private val jobs = mutableListOf<Job>()
    private val source = MutableStateFlow(PowerSource.NONE)
    private val posture = MutableStateFlow(Posture.UNKNOWN)
    private val sensors = service.getSystemService(SensorManager::class.java)
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.values.size < 3) return
            posture.value = StandbyLogic.posture(e.values[0], e.values[1], e.values[2], posture.value)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val scope = env.scope
        jobs += scope.launch {
            broadcasts(service, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                .map { BatteryReader.parse(it)?.source ?: PowerSource.NONE }
                .onStart { emit(BatteryReader.current(service)?.source ?: PowerSource.NONE) }
                .distinctUntilChanged()
                .collect { s ->
                    source.value = s
                    if (s != PowerSource.NONE) IslandLog.log("Veille : branché ($s)")
                }
        }
        // Capteur de gravité seulement pendant la charge, module activé.
        jobs += scope.launch {
            combine(source, env.controller.settings.map { it.standby.enabled }) { s, on -> s != PowerSource.NONE && on }
                .distinctUntilChanged()
                .collect { want -> if (want) listen() else stopListening() }
        }
        jobs += scope.launch {
            combine(
                env.controller.settings.map { it.standby },
                source,
                posture,
                env.controller.screenEnv.map { it.locked },
                // Revérifié chaque minute : l'entrée dans la plage horaire suffit à déclencher.
                combine(ForegroundApp.packageName, kotlinx.coroutines.flow.flow { while (true) { emit(Unit); delay(60_000L) } }) { fg, _ -> fg },
            ) { s, src, p, locked, fg ->
                val interactive = service.getSystemService(PowerManager::class.java)?.isInteractive != false
                // Veille elle-même au premier plan compte comme « au repos » (sinon elle se fermerait).
                val idle = locked || !interactive || (fg != null && fg == HomeApp.packageName(service)) ||
                    StandbyState.active.value ||
                    service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                val minute = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
                val scheduled = s.scheduleEnabled && StandbyLogic.inWindow(minute, s.scheduleStart, s.scheduleEnd)
                Triple(StandbyLogic.conditions(s.enabled, src, s.charger, p, idle, scheduled), s.delaySeconds, s.autoLaunch)
            }.distinctUntilChanged().collectLatest { (ok, delaySec, auto) ->
                StandbyState.setConditions(ok)
                if (!ok || !auto) return@collectLatest
                delay(delaySec.coerceIn(StandbySettings.DELAY) * 1000L)
                if (StandbyState.active.value || StandbyState.dreaming.value) return@collectLatest
                IslandLog.log("Veille : conditions réunies (charge + horizontale)")
                StandbyLauncher.launchAuto(service)
            }
        }
        // Dernier « pense à toi » d'Écho (page Écho).
        jobs += scope.launch {
            NotificationRepository.events.filterIsInstance<NotificationEvent.Posted>()
                .filter { it.sbn.packageName == EchoModule.ECHO_PACKAGE && it.sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .collect { e ->
                    val x = e.sbn.notification.extras
                    StandbyState.setEcho(
                        EchoThought(
                            x.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                            x.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                            System.currentTimeMillis(),
                        ),
                    )
                }
        }
    }

    private fun listen() {
        if (listening) return
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopListening() {
        if (!listening) return
        sensors?.unregisterListener(listener)
        listening = false
        posture.value = Posture.UNKNOWN
    }

    override fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        stopListening()
        StandbyState.setConditions(false)
    }
}
