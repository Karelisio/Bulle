package com.karelisio.bulle.modules.capture.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.karelisio.bulle.R
import java.util.Locale

/** Actions proposées pour un code QR scanné (lancées depuis l'île, donc en nouvelle tâche). */
object QrIntents {
    private const val TAG = "QrIntents"

    fun open(context: Context, payload: QrPayload) {
        when (payload) {
            is QrPayload.Url -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse(payload.url)))
            is QrPayload.Wifi -> connectWifi(context, payload)
            is QrPayload.Contact -> addContact(context, payload)
            is QrPayload.Phone -> start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(payload.number))))
            is QrPayload.Sms -> start(
                context,
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(payload.number)))
                    .apply { payload.body?.let { putExtra("sms_body", it) } },
            )
            is QrPayload.Email -> start(
                context,
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(payload.address))
                    payload.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    payload.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                },
            )
            is QrPayload.Geo -> {
                val q = payload.query?.let { "?q=" + Uri.encode(it) } ?: "?q=${payload.latitude},${payload.longitude}"
                start(context, Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.ROOT, "geo:%f,%f", payload.latitude, payload.longitude) + q)))
            }
            is QrPayload.Text -> share(context, payload.raw)
        }
    }

    /**
     * Wi-Fi : boîte de dialogue système « Enregistrer ce réseau ? » (Settings.ACTION_WIFI_ADD_NETWORKS,
     * Android 11+, aucune permission). Le réseau est construit comme une WifiNetworkSuggestion.
     */
    fun connectWifi(context: Context, wifi: QrPayload.Wifi) {
        val suggestion = runCatching {
            WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).apply {
                val pass = wifi.password
                when (wifi.security) {
                    WifiSecurity.WPA -> if (pass != null) setWpa2Passphrase(pass)
                    WifiSecurity.WPA3 -> if (pass != null) setWpa3Passphrase(pass)
                    // WEP n'est plus accepté par les suggestions : le mot de passe est copié à la place.
                    WifiSecurity.WEP, WifiSecurity.OPEN -> Unit
                }
                if (wifi.hidden) setIsHiddenSsid(true)
            }.build()
        }.getOrNull()
        if (suggestion == null || wifi.security == WifiSecurity.WEP) {
            wifi.password?.let { copy(context, it, R.string.capture_qr_copied_password) }
            start(context, Intent(Settings.Panel.ACTION_WIFI))
            return
        }
        val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
        if (!start(context, intent)) {
            wifi.password?.let { copy(context, it, R.string.capture_qr_copied_password) }
            start(context, Intent(Settings.Panel.ACTION_WIFI))
        }
    }

    fun addContact(context: Context, c: QrPayload.Contact) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            c.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
            c.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            c.address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
            c.note?.let { putExtra(ContactsContract.Intents.Insert.NOTES, it) }
            // Téléphones, e-mails et sites (plusieurs valeurs) via la liste DATA.
            val data = ArrayList<ContentValues>()
            c.phones.forEach { p ->
                data += ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Phone.NUMBER, p)
                }
            }
            c.emails.forEach { e ->
                data += ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Email.ADDRESS, e)
                }
            }
            c.urls.forEach { u ->
                data += ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Website.URL, u)
                }
            }
            if (data.isNotEmpty()) putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
        }
        start(context, intent)
    }

    fun copy(context: Context, text: String, messageRes: Int = R.string.capture_qr_copied) {
        runCatching {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("QR", text))
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, null))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Action QR impossible", e)
        false
    }
}
