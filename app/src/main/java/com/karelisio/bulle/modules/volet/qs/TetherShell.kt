package com.karelisio.bulle.modules.volet.qs

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Point d'accès Wi-Fi via le service système « tethering » (ITetheringConnector), exécuté dans le
 * service utilisateur Shizuku (identité shell, qui détient TETHER_PRIVILEGED) : c'est la même
 * demande que la tuile du système, avec la configuration enregistrée du point d'accès
 * (nom et mot de passe inchangés). Aucune API publique ne permet de le faire depuis l'appli.
 *
 * La commande « bulle-tether wifi on|off » est interceptée par [ShellUserService].
 */
object TetherShell {
    const val PREFIX = "bulle-tether"
    private const val TETHERING_WIFI = 0
    private const val SCOPE_GLOBAL = 1
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val LISTENER = "android.net.IIntResultListener"
    private const val TETHER_ERROR_NO_ERROR = 0

    fun command(enable: Boolean) = "$PREFIX wifi ${if (enable) "on" else "off"}"

    /** « bulle-tether wifi on|off » → vrai / faux ; null si ce n'est pas une commande de partage. */
    fun parse(command: String): Boolean? {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.size != 3 || parts[0] != PREFIX || parts[1] != "wifi") return null
        return when (parts[2]) {
            "on" -> true
            "off" -> false
            else -> null
        }
    }

    /** Commandes « bulle-tether » exécutées dans le processus shell ; null : commande shell ordinaire. */
    fun intercept(command: String): Pair<Int, String>? = when {
        command.trim() == STATE_COMMAND -> state()
        else -> parse(command)?.let(::run)
    }

    /** Lecture de l'état du point d'accès (état WIFI_AP_STATE_* renvoyé par « state N »). */
    const val STATE_COMMAND = "$PREFIX wifi state"

    /** Sortie « state N » → N ; null sinon. */
    fun parseState(output: String?): Int? =
        output?.trim()?.removePrefix("state ")?.trim()?.toIntOrNull()

    /** État du point d'accès lu dans le service Wi-Fi (processus shell) ; (0, « state N ») ou (-1, raison). */
    fun state(): Pair<Int, String> = try {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "wifi") as? IBinder
            ?: return -1 to "service wifi introuvable"
        val wifi = Class.forName("android.net.wifi.IWifiManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
        val s = wifi.javaClass.getMethod("getWifiApEnabledState").invoke(wifi) as Int
        0 to "state $s"
    } catch (t: Throwable) {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        -1 to "${cause.javaClass.simpleName}: ${cause.message}"
    }

    /** Rôle de chaque paramètre de startTethering / stopTethering (signatures selon la version d'Android). */
    enum class Arg { REQUEST, TYPE, PACKAGE, TAG, LISTENER, UNKNOWN }

    /**
     * Arguments à passer d'après les noms de types des paramètres (logique pure, testée) :
     * Android 11 : (requête|type, paquet, écouteur) ; 12+ : (requête|type, paquet, attribution, écouteur).
     */
    fun plan(paramTypes: List<String>): List<Arg> {
        var strings = 0
        return paramTypes.map { t ->
            when {
                t.endsWith("TetheringRequestParcel") -> Arg.REQUEST
                t == "int" -> Arg.TYPE
                t == "java.lang.String" -> if (strings++ == 0) Arg.PACKAGE else Arg.TAG
                t.endsWith("IIntResultListener") -> Arg.LISTENER
                else -> Arg.UNKNOWN
            }
        }
    }

    /** Active ou coupe le point d'accès ; (0, message) si le service l'accepte, (-1, raison) sinon. */
    fun run(enable: Boolean): Pair<Int, String> = try {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "tethering") as? IBinder
            ?: return -1 to "service tethering introuvable"
        val connector = Class.forName("android.net.ITetheringConnector\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
        val result = ResultBinder()
        val listener = Class.forName("$LISTENER\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, result)
        val method = connector.javaClass.methods.first { it.name == if (enable) "startTethering" else "stopTethering" }
        val args = plan(method.parameterTypes.map { it.name }).map { arg ->
            when (arg) {
                Arg.REQUEST -> request(method)
                Arg.TYPE -> TETHERING_WIFI
                Arg.PACKAGE -> SHELL_PACKAGE
                Arg.TAG, Arg.UNKNOWN -> null
                Arg.LISTENER -> listener
            }
        }
        method.invoke(connector, *args.toTypedArray())
        val code = result.await(6)
        when (code) {
            TETHER_ERROR_NO_ERROR -> 0 to "ok"
            null -> 0 to "sans réponse (demande envoyée)"
            else -> -1 to "erreur de partage $code"
        }
    } catch (t: Throwable) {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        -1 to "${cause.javaClass.simpleName}: ${cause.message}"
    }

    /** Requête de partage Wi-Fi, sans vérification d'abonnement ni écran du système. */
    private fun request(method: Method): Any {
        val type = method.parameterTypes.first { it.name.endsWith("TetheringRequestParcel") }
        // 1. Constructeur officiel (TetheringRequest.Builder), qui remplit les champs selon la version.
        runCatching {
            val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            val builder = builderClass.getConstructor(Int::class.javaPrimitiveType).newInstance(TETHERING_WIFI)
            runCatching { builderClass.getMethod("setExemptFromEntitlementCheck", Boolean::class.javaPrimitiveType).invoke(builder, true) }
            runCatching { builderClass.getMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType).invoke(builder, false) }
            val req = builderClass.getMethod("build").invoke(builder)!!
            val parcel = req.javaClass.getMethod("getParcel").invoke(req)
            if (parcel != null && type.isInstance(parcel)) return parcel
        }
        // 2. Parcelable rempli à la main.
        val p = type.getConstructor().newInstance()
        fun set(name: String, value: Any) = runCatching { type.getField(name).set(p, value) }
        set("tetheringType", TETHERING_WIFI)
        set("exemptFromEntitlementCheck", true)
        set("showProvisioningUi", false)
        set("connectivityScope", SCOPE_GLOBAL)
        return p
    }

    /** Écouteur IIntResultListener (oneway onResult(int)) écrit à la main. */
    private class ResultBinder : Binder() {
        private val latch = CountDownLatch(1)

        @Volatile
        private var code: Int? = null

        fun await(seconds: Long): Int? = if (latch.await(seconds, TimeUnit.SECONDS)) code else null

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
            INTERFACE_TRANSACTION -> {
                reply?.writeString(LISTENER)
                true
            }
            FIRST_CALL_TRANSACTION -> {
                runCatching { data.enforceInterface(LISTENER) }
                this.code = data.readInt()
                latch.countDown()
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }
}
