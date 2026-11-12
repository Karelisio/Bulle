package com.karelisio.bulle.modules.volet.qs

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.annotation.Keep
import com.karelisio.bulle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Exécution de commandes shell via Shizuku (facultatif). Sans Shizuku installé ou démarré,
 * [status] vaut UNAVAILABLE et rien n'est tenté : aucune exception ne remonte.
 *
 * Shizuku 13 ne permet plus de lancer un processus directement (newProcess est privé) :
 * les commandes passent par un « service utilisateur » ([ShellUserService]) que Shizuku
 * exécute avec l'identité shell (adb) ou root.
 */
object ShizukuShell {
    private const val REQUEST_CODE = 0x5B11
    private const val TIMEOUT_MS = 8_000L

    @Volatile
    private var binder: IBinder? = null
    private val bindLock = Mutex()

    fun status(): ShizukuStatus = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShizukuStatus.UNAVAILABLE
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
            else -> ShizukuStatus.NEEDS_PERMISSION
        }
    } catch (_: Throwable) {
        ShizukuStatus.UNAVAILABLE
    }

    /** Vrai si la permission Shizuku est accordée (la demande au besoin). */
    suspend fun ensurePermission(): Boolean = when (status()) {
        ShizukuStatus.READY -> true
        ShizukuStatus.UNAVAILABLE -> false
        ShizukuStatus.NEEDS_PERMISSION -> requestPermission()
    }

    private suspend fun requestPermission(): Boolean = withTimeoutOrNull(60_000L) {
        suspendCancellableCoroutine { cont ->
            try {
                // Refus définitif (« ne plus demander ») : inutile de redemander.
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    cont.resume(false)
                    return@suspendCancellableCoroutine
                }
                val listener = object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != REQUEST_CODE) return
                        Shizuku.removeRequestPermissionResultListener(this)
                        if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                cont.invokeOnCancellation { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
                Shizuku.requestPermission(REQUEST_CODE)
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(false)
            }
        }
    } ?: false

    /** Exécute une commande (sh -c) ; null en cas d'échec (Shizuku absent, refus, délai, code ≠ 0). */
    suspend fun run(context: Context, command: String): String? {
        if (!ensurePermission()) return null
        val service = withContext(Dispatchers.Main) { bind(context) } ?: return null
        return withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ShellUserService.DESCRIPTOR)
                data.writeString(command)
                service.transact(ShellUserService.TX_EXEC, data, reply, 0)
                reply.readException()
                val exit = reply.readInt()
                val out = reply.readString().orEmpty()
                if (exit == 0) out else null
            } catch (_: Throwable) {
                binder = null
                null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private suspend fun bind(context: Context): IBinder? = bindLock.withLock {
        binder?.takeIf { it.pingBinder() }?.let { return@withLock it }
        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        binder = service
                        if (cont.isActive) cont.resume(service)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        binder = null
                    }
                }
                try {
                    Shizuku.bindUserService(userServiceArgs(context), connection)
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun userServiceArgs(context: Context) =
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
}

/**
 * Service utilisateur Shizuku : exécuté dans un processus à part, avec l'identité shell.
 * Binder écrit à la main (pas d'AIDL) : une seule transaction, « exécuter une commande ».
 */
@Keep
class ShellUserService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
        TX_EXEC -> {
            data.enforceInterface(DESCRIPTOR)
            val (exit, out) = exec(data.readString().orEmpty())
            reply?.writeNoException()
            reply?.writeInt(exit)
            reply?.writeString(out)
            true
        }
        // Transaction « destroy » envoyée par Shizuku à la libération du service.
        TX_DESTROY -> {
            Runtime.getRuntime().exit(0)
            true
        }
        INTERFACE_TRANSACTION -> {
            reply?.writeString(DESCRIPTOR)
            true
        }
        else -> super.onTransact(code, data, reply, flags)
    }

    private fun exec(command: String): Pair<Int, String> = try {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        // Lecture de la sortie dans un fil à part : une commande qui ne ferme jamais sa sortie
        // ne doit pas bloquer le service au-delà du délai maximal.
        val output = StringBuilder()
        val reader = Thread { runCatching { p.inputStream.bufferedReader().use { output.append(it.readText()) } } }
        reader.start()
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            reader.join(500)
            -1 to synchronized(output) { output.toString() }
        } else {
            reader.join(2_000)
            p.exitValue() to synchronized(output) { output.toString() }
        }
    } catch (t: Throwable) {
        -1 to t.toString()
    }

    companion object {
        const val DESCRIPTOR = "com.karelisio.bulle.volet.IShell"
        const val TX_EXEC = FIRST_CALL_TRANSACTION
        const val TX_DESTROY = 16_777_115
    }
}
