package com.karelisio.bulle.modules.rules

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/** Autorisations utiles aux déclencheurs (demandées seulement à la création de la condition). */
object RulePermissions {
    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun location(ctx: Context): Boolean =
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun backgroundLocation(ctx: Context): Boolean = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun bluetooth(ctx: Context): Boolean = granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)
}

/**
 * Sources de l'environnement observé par les règles. Chaque source ne coûte presque rien
 * (diffusions système, rappels) ; la localisation et le Bluetooth ne sont écoutés que si une règle
 * activée les utilise.
 *
 * Géorepérage : [LocationManager] seul, sans Play services (Bulle n'en dépend pas et doit marcher
 * sans). Fournisseur « fused » d'Android 12+ en qualité basse consommation (réseau, pas de GPS
 * dédié), repli sur le fournisseur réseau, plus le fournisseur passif qui récupère gratuitement les
 * positions demandées par les autres applis. Hystérésis dans [Geo].
 */
class RuleWorldSources(
    context: Context,
    private val scope: CoroutineScope,
    private val rules: Flow<List<Rule>>,
) {
    private val context = context.applicationContext ?: context
    private val _world = MutableStateFlow(RuleWorld())
    val world: StateFlow<RuleWorld> = _world.asStateFlow()

    private val cleanups = mutableListOf<() -> Unit>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        // Une source indisponible (constructeur, test) ne doit pas empêcher les autres.
        listOf(::clock, ::battery, ::headset, ::wifi, ::darkMode, ::screen, ::call, ::dynamic)
            .forEach { runCatching { it() } }
    }

    fun stop() {
        started = false
        cleanups.asReversed().forEach { runCatching { it() } }
        cleanups.clear()
    }

    private fun receiver(filter: IntentFilter, onIntent: (Intent) -> Unit): Intent? {
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = onIntent(intent)
        }
        val sticky = runCatching { ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED) }.getOrNull()
        cleanups += { context.unregisterReceiver(r) }
        return sticky
    }

    private fun clock() {
        // Valeur initiale posée tout de suite : sinon la première émission voyait minuit et un
        // créneau de nuit (22 h–7 h) passait faussement actif au démarrage.
        fun now() = Calendar.getInstance().also { c ->
            _world.update {
                it.copy(
                    minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
                    dayOfWeek = RuleLogic.isoDay(c.get(Calendar.DAY_OF_WEEK)),
                )
            }
        }
        now()
        val j = scope.launch {
            while (isActive) {
                val c = now()
                delay(60_000L - (c.get(Calendar.SECOND) * 1000L + c.get(Calendar.MILLISECOND)) + 50)
            }
        }
        cleanups += { j.cancel() }
    }

    private fun battery() {
        val onBattery = { intent: Intent ->
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
            val pct = if (level < 0) -1 else (level * 100 + scale / 2) / scale
            _world.update { it.copy(charging = plugged, batteryLevel = pct) }
        }
        receiver(IntentFilter(Intent.ACTION_BATTERY_CHANGED), onBattery)?.let(onBattery)
    }

    private fun headset() {
        // Casque (filaire, USB, Bluetooth).
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val headsetTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
        fun refresh() {
            val on = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }
            _world.update { it.copy(headset = on) }
        }
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }
        audio.registerAudioDeviceCallback(cb, null)
        refresh()
        cleanups += { audio.unregisterAudioDeviceCallback(cb) }
    }

    private fun wifi() {
        // Le nom du réseau nécessite la localisation ; sinon seul « connecté » est connu.
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')?.takeUnless { it == "<unknown ssid>" }
                _world.update { it.copy(wifiConnected = true, wifiSsid = ssid) }
            }

            override fun onLost(network: Network) {
                _world.update { it.copy(wifiConnected = false, wifiSsid = null) }
            }
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
            cleanups += { cm.unregisterNetworkCallback(cb) }
        }
    }

    private fun darkMode() {
        fun isDark(c: Configuration) = c.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        _world.update { it.copy(darkMode = isDark(context.resources.configuration)) }
        val cb = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                _world.update { it.copy(darkMode = isDark(newConfig)) }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }
        context.registerComponentCallbacks(cb)
        cleanups += { context.unregisterComponentCallbacks(cb) }
    }

    private fun screen() {
        val km = context.getSystemService(KeyguardManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        // Écran éteint = verrouillé (le verrouillage effectif peut suivre avec un délai).
        fun refresh() {
            val locked = pm?.isInteractive == false || km?.isKeyguardLocked == true
            _world.update { it.copy(screenLocked = locked) }
        }
        refresh()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        receiver(filter) { refresh() }
    }

    private fun call() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        fun refresh(mode: Int) = _world.update { it.copy(inCall = isCallMode(mode)) }
        refresh(audio.mode)
        val listener = AudioManager.OnModeChangedListener { refresh(it) }
        runCatching {
            audio.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener)
            cleanups += { audio.removeOnModeChangedListener(listener) }
        }
    }

    // --- Sources écoutées seulement si une règle les utilise ---

    private var placeList: List<RuleTrigger.Place> = emptyList()
    private var lastFix: GeoFix? = null
    private var stopLocation: (() -> Unit)? = null
    private var stopBluetooth: (() -> Unit)? = null

    private fun dynamic() {
        val j = scope.launch {
            rules.map { r -> RuleLogic.places(r) to RuleLogic.usesBluetooth(r) }
                .distinctUntilChanged()
                .collect { (places, bt) ->
                    updatePlaces(places)
                    val wantBt = bt && RulePermissions.bluetooth(context)
                    if (wantBt && stopBluetooth == null) stopBluetooth = startBluetooth()
                    if (!wantBt) {
                        stopBluetooth?.invoke()
                        stopBluetooth = null
                    }
                }
        }
        cleanups += {
            j.cancel()
            stopLocation?.invoke()
            stopLocation = null
            stopBluetooth?.invoke()
            stopBluetooth = null
        }
    }

    private fun updatePlaces(places: List<RuleTrigger.Place>) {
        val changed = places != placeList
        placeList = places
        val ids = places.mapTo(HashSet()) { it.id }
        _world.update { w -> w.copy(insidePlaces = w.insidePlaces intersect ids) }
        lastFix?.let(::onFix)
        if (places.isEmpty()) {
            stopLocation?.invoke()
            stopLocation = null
        } else if (changed || stopLocation == null) {
            stopLocation?.invoke()
            stopLocation = if (RulePermissions.location(context)) startLocation() else null
        }
    }

    private fun onFix(fix: GeoFix) {
        lastFix = fix
        _world.update { it.copy(insidePlaces = Geo.update(it.insidePlaces, placeList, fix)) }
    }

    @SuppressLint("MissingPermission") // Vérifiée par RulePermissions.location avant l'appel.
    private fun startLocation(): (() -> Unit)? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val listener = LocationListener { loc: Location ->
            onFix(GeoFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 0f))
        }
        val executor = ContextCompat.getMainExecutor(context)
        val request = LocationRequest.Builder(LOCATION_INTERVAL_MS)
            .setQuality(LocationRequest.QUALITY_LOW_POWER)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_M)
            .build()
        val providers = buildList {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { runCatching { lm.hasProvider(it) }.getOrDefault(false) }?.let(::add)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val registered = providers.filter { p ->
            runCatching { lm.requestLocationUpdates(p, request, executor, listener) }.isSuccess
        }
        // Dernière position connue la plus récente, pour ne pas attendre la première mesure.
        providers.plus(LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { listener.onLocationChanged(it) }
        if (registered.isEmpty()) return null
        return { runCatching { lm.removeUpdates(listener) } }
    }

    @SuppressLint("MissingPermission") // Vérifiée par RulePermissions.bluetooth avant l'appel.
    private fun startBluetooth(): () -> Unit {
        // État initial : appareils appairés connectés (méthode masquée isConnected, tolérée).
        val initial = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .filter { d -> runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false) }
                .mapTo(HashSet()) { it.address }
        }.getOrDefault(emptySet())
        _world.update { it.copy(bluetoothDevices = initial) }
        val filter = IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device = IntentCompat.getParcelableExtra(
                    intent, android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java,
                ) ?: return
                val address = runCatching { device.address }.getOrNull() ?: return
                _world.update {
                    val set = if (intent.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) {
                        it.bluetoothDevices + address
                    } else {
                        it.bluetoothDevices - address
                    }
                    it.copy(bluetoothDevices = set)
                }
            }
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED)
        return {
            runCatching { context.unregisterReceiver(r) }
            _world.update { it.copy(bluetoothDevices = emptySet()) }
        }
    }

    companion object {
        private const val LOCATION_INTERVAL_MS = 5 * 60_000L
        private const val LOCATION_MIN_INTERVAL_MS = 60_000L
        private const val LOCATION_MIN_DISTANCE_M = 50f

        /** Modes audio d'un appel (téléphonique ou VoIP). */
        fun isCallMode(mode: Int): Boolean =
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_CALL_SCREENING || mode == MODE_CALL_REDIRECT || mode == MODE_COMMUNICATION_REDIRECT

        // Constantes d'Android 13 (valeurs stables), lues en dur pour rester compatibles API 31.
        private const val MODE_CALL_REDIRECT = 5
        private const val MODE_COMMUNICATION_REDIRECT = 6
    }
}
