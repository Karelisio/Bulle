package com.karelisio.bulle.modules.search

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un contact trouvé, avec son numéro principal (s'il en a un). */
data class ContactHit(val id: Long, val lookupKey: String, val name: String, val phone: String?)

/** Recherche locale dans les contacts (ContactsContract), uniquement si READ_CONTACTS est accordée. */
object ContactSearch {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Contacts dont le nom correspond à [query] (au plus [limit]). Vide sans autorisation ou en cas d'erreur. */
    suspend fun search(context: Context, query: String, limit: Int = 3): List<ContactHit> {
        val q = query.trim()
        if (q.length < 2 || !hasPermission(context)) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
                val projection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                )
                val hits = mutableListOf<Triple<Long, String, String>>()
                val withPhone = mutableSetOf<Long>()
                context.contentResolver.query(uri, projection, null, null, ContactsContract.Contacts.SORT_KEY_PRIMARY)?.use { c ->
                    while (c.moveToNext() && hits.size < limit) {
                        val id = c.getLong(0)
                        val name = c.getString(2) ?: continue
                        hits += Triple(id, c.getString(1).orEmpty(), name)
                        if (c.getInt(3) > 0) withPhone += id
                    }
                }
                hits.map { (id, key, name) -> ContactHit(id, key, name, if (id in withPhone) phoneOf(context, id) else null) }
            }.getOrDefault(emptyList())
        }
    }

    private fun phoneOf(context: Context, contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** Composeur prérempli (aucune permission d'appel nécessaire). */
    fun dialIntent(phone: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun smsIntent(phone: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openIntent(hit: ContactHit): Intent {
        val uri = if (hit.lookupKey.isNotEmpty()) {
            ContactsContract.Contacts.getLookupUri(hit.id, hit.lookupKey)
        } else {
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, hit.id)
        }
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Écran « Autorisations » de Bulle dans les Réglages système (pour accorder Contacts). */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
