package com.karelisio.bulle.modules.battery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.IntentCompat
import com.karelisio.bulle.modules.alerts.AlertRules
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.alerts.hasPermission
import com.karelisio.bulle.modules.alerts.sources.BtKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Accessoire Bluetooth connecté (montre, écouteurs…) et sa batterie si connue. */
data class BtAccessory(val address: String, val name: String?, val kind: BtKind, val level: Int?)

/** Règles pures de la liste des accessoires. */
object BtAccessories {

    /** Diffusion système masquée annonçant la batterie d'un appareil connecté (et son extra). */
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    /** Niveau lu dans l'extra ou par réflexion : 0..100, sinon null (-1 = inconnu côté système). */
    fun parseLevel(raw: Any?): Int? = (raw as? Int)?.let(AlertRules::bluetoothBattery)

    /** Met à jour (ou ajoute) un accessoire (liste triée par nom, accents compris), en gardant le niveau connu si le nouveau est absent. */
    fun upsert(list: List<BtAccessory>, device: BtAccessory): List<BtAccessory> {
        val old = list.firstOrNull { it.address == device.address }
        val merged = if (old != null && device.level == null) device.copy(level = old.level) else device
        return (list.filterNot { it.address == device.address } + merged).sortedWith(compareBy(java.text.Collator.getInstance()) { it.name.orEmpty() })
    }

    fun withLevel(list: List<BtAccessory>, address: String, level: Int?): List<BtAccessory> =
        if (level == null) list else list.map { if (it.address == address) it.copy(level = level) else it }

    fun remove(list: List<BtAccessory>, address: String): List<BtAccessory> = list.filterNot { it.address == address }

    /** Garde les appareils « personnels » (audio, montre), ignore claviers, ordinateurs… */
    fun kindOf(majorClass: Int?, deviceClass: Int?): BtKind? = when (majorClass) {
        null -> BtKind.OTHER
        BluetoothClass.Device.Major.AUDIO_VIDEO ->
            if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) BtKind.CAR else BtKind.HEADPHONES
        BluetoothClass.Device.Major.WEARABLE, BluetoothClass.Device.Major.HEALTH -> BtKind.WATCH
        BluetoothClass.Device.Major.UNCATEGORIZED -> BtKind.OTHER
        else -> null
    }
}

/**
 * Batteries des accessoires Bluetooth connectés. Uniquement si BLUETOOTH_CONNECT est déjà
 * accordée (jamais demandée ici) ; repli silencieux sur une liste vide sinon.
 */
object BluetoothBatteries {

    fun granted(context: Context): Boolean = context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    @SuppressLint("MissingPermission") // Permission vérifiée avant tout accès.
    fun accessories(context: Context): Flow<List<BtAccessory>> {
        val ctx = context.applicationContext
        if (!granted(ctx)) return flowOf(emptyList())
        return flow {
            var list = connected(ctx)
            emit(list)
            val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BtAccessories.ACTION_BATTERY_LEVEL_CHANGED)
            }
            broadcasts(ctx, filter).collect { i ->
                val device = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return@collect
                list = when (i.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> accessory(ctx, device)?.let { BtAccessories.upsert(list, it) } ?: list
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> BtAccessories.remove(list, device.address)
                    else -> {
                        val level = BtAccessories.parseLevel(i.getIntExtra(BtAccessories.EXTRA_BATTERY_LEVEL, -1))
                        if (list.none { it.address == device.address }) {
                            accessory(ctx, device)?.let { BtAccessories.upsert(list, it.copy(level = level)) } ?: list
                        } else {
                            BtAccessories.withLevel(list, device.address, level)
                        }
                    }
                }
                emit(list)
            }
        }.catch { emit(emptyList()) }.distinctUntilChanged()
    }

    /** Appareils appairés actuellement connectés (méthode masquée isConnected, tolérée). */
    @SuppressLint("MissingPermission")
    private fun connected(ctx: Context): List<BtAccessory> = runCatching {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        adapter.bondedDevices.orEmpty()
            .filter { d -> runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false) }
            .mapNotNull { accessory(ctx, it) }
            .fold(emptyList<BtAccessory>()) { acc, a -> BtAccessories.upsert(acc, a) }
    }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    private fun accessory(ctx: Context, device: BluetoothDevice): BtAccessory? {
        if (!granted(ctx)) return null
        val cls = runCatching { device.bluetoothClass }.getOrNull()
        val kind = BtAccessories.kindOf(cls?.majorDeviceClass, cls?.deviceClass) ?: return null
        val name = runCatching { device.alias ?: device.name }.getOrNull()
        val level = runCatching { BtAccessories.parseLevel(BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device)) }.getOrNull()
        return BtAccessory(device.address, name, kind, level)
    }
}
