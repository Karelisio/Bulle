package com.karelisio.bulle.modules.capture.qr

import java.util.Locale

/** Contenu d'un code QR, interprété selon son type. Fonction pure : aucune dépendance Android. */
sealed interface QrPayload {
    /** Texte brut d'origine (toujours disponible pour « copier »). */
    val raw: String

    data class Url(override val raw: String, val url: String) : QrPayload

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String?,
        val security: WifiSecurity,
        val hidden: Boolean,
    ) : QrPayload

    data class Contact(
        override val raw: String,
        val name: String?,
        val organization: String?,
        val phones: List<String>,
        val emails: List<String>,
        val urls: List<String>,
        val address: String?,
        val note: String?,
    ) : QrPayload

    data class Phone(override val raw: String, val number: String) : QrPayload

    data class Sms(override val raw: String, val number: String, val body: String?) : QrPayload

    data class Email(override val raw: String, val address: String, val subject: String?, val body: String?) : QrPayload

    data class Geo(override val raw: String, val latitude: Double, val longitude: Double, val query: String?) : QrPayload

    data class Text(override val raw: String) : QrPayload
}

enum class WifiSecurity { OPEN, WEP, WPA, WPA3 }

/** Analyse le texte d'un code QR (formats usuels : URL, WIFI:, vCard, MECARD, tel:, SMSTO:, mailto:, MATMSG:, geo:). */
object QrParser {

    fun parse(input: String): QrPayload {
        val raw = input.trim()
        if (raw.isEmpty()) return QrPayload.Text(input)
        val upper = raw.uppercase(Locale.ROOT)
        return when {
            upper.startsWith("WIFI:") -> parseWifi(raw) ?: QrPayload.Text(raw)
            upper.startsWith("BEGIN:VCARD") -> parseVCard(raw)
            upper.startsWith("MECARD:") -> parseMeCard(raw)
            upper.startsWith("TEL:") -> raw.substring(4).trim().takeIf { it.isNotEmpty() }
                ?.let { QrPayload.Phone(raw, it) } ?: QrPayload.Text(raw)
            upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> parseSms(raw)
            upper.startsWith("MAILTO:") -> parseMailto(raw)
            upper.startsWith("MATMSG:") -> parseMatMsg(raw)
            upper.startsWith("GEO:") -> parseGeo(raw) ?: QrPayload.Text(raw)
            isUrl(raw) -> QrPayload.Url(raw, normalizeUrl(raw))
            else -> QrPayload.Text(raw)
        }
    }

    // ---- URL ----------------------------------------------------------------------------------

    private val schemeUrl = Regex("^(https?|ftp)://\\S+$", RegexOption.IGNORE_CASE)
    private val bareUrl = Regex("^(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(:\\d+)?(/\\S*)?$", RegexOption.IGNORE_CASE)

    private fun isUrl(s: String): Boolean =
        !s.contains(' ') && !s.contains('\n') && (schemeUrl.matches(s) || (s.startsWith("www.", ignoreCase = true) && bareUrl.matches(s)))

    private fun normalizeUrl(s: String): String =
        if (schemeUrl.matches(s)) s else "https://$s"

    // ---- Wi-Fi : WIFI:T:WPA;S:Mon réseau;P:secret;H:false;; ------------------------------------

    private fun parseWifi(raw: String): QrPayload.Wifi? {
        val fields = splitFields(raw.substring(5))
        val ssid = fields["S"]?.takeIf { it.isNotEmpty() } ?: return null
        val type = fields["T"]?.uppercase(Locale.ROOT).orEmpty()
        val password = fields["P"]?.takeIf { it.isNotEmpty() }
        val security = when {
            type.startsWith("WPA3") || type == "SAE" -> WifiSecurity.WPA3
            type.startsWith("WPA") -> WifiSecurity.WPA
            type == "WEP" -> WifiSecurity.WEP
            type == "NOPASS" || password == null -> WifiSecurity.OPEN
            else -> WifiSecurity.WPA
        }
        val hidden = fields["H"]?.equals("true", ignoreCase = true) == true
        return QrPayload.Wifi(raw, ssid, if (security == WifiSecurity.OPEN) null else password, security, hidden)
    }

    /**
     * Découpe « K:valeur;K2:valeur2;; » en tenant compte des échappements (\; \, \: \\ \").
     * Les clés sont mises en majuscules ; la première occurrence d'une clé l'emporte.
     */
    internal fun splitFields(body: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var i = 0
        val cur = StringBuilder()
        val parts = mutableListOf<String>()
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> { cur.append('\\').append(body[i + 1]); i += 2; continue }
                c == ';' -> { parts += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) parts += cur.toString()
        for (p in parts) {
            val colon = indexOfUnescaped(p, ':')
            if (colon <= 0) continue
            val key = p.substring(0, colon).uppercase(Locale.ROOT)
            if (key !in out) out[key] = unescape(p.substring(colon + 1))
        }
        return out
    }

    private fun indexOfUnescaped(s: String, ch: Char): Int {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') { i += 2; continue }
            if (s[i] == ch) return i
            i++
        }
        return -1
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { sb.append(s[i + 1]); i += 2 } else { sb.append(c); i++ }
        }
        // Les SSID entre guillemets (format ancien) sont débarrassés de leurs guillemets.
        val r = sb.toString()
        return if (r.length >= 2 && r.startsWith('"') && r.endsWith('"')) r.substring(1, r.length - 1) else r
    }

    // ---- vCard --------------------------------------------------------------------------------

    private fun parseVCard(raw: String): QrPayload.Contact {
        // Dépliage des lignes (RFC 6350 : une ligne qui commence par un espace prolonge la précédente).
        val lines = raw.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        var fn: String? = null
        var n: String? = null
        var org: String? = null
        var adr: String? = null
        var note: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).substringBefore(';').substringAfterLast('.').uppercase(Locale.ROOT)
            val value = vcardUnescape(line.substring(colon + 1).trim())
            if (value.isEmpty()) continue
            when (name) {
                "FN" -> fn = value
                "N" -> n = value.split(';').let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ") }
                "ORG" -> org = value.replace(';', ' ').trim()
                "TEL" -> phones += value.removePrefix("tel:")
                "EMAIL" -> emails += value
                "URL" -> urls += value
                "ADR" -> adr = value.split(';').filter { it.isNotBlank() }.joinToString(", ")
                "NOTE" -> note = value
            }
        }
        return QrPayload.Contact(raw, fn ?: n?.takeIf { it.isNotBlank() }, org, phones, emails, urls, adr, note)
    }

    private fun vcardUnescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    // ---- MECARD:N:Dupont,Jean;TEL:0102;EMAIL:a@b.c;; -----------------------------------------

    private fun parseMeCard(raw: String): QrPayload.Contact {
        val f = splitFieldsMulti(raw.substring(7))
        val name = f["N"]?.firstOrNull()?.split(',')?.let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ") }
        return QrPayload.Contact(
            raw,
            name?.takeIf { it.isNotBlank() },
            f["ORG"]?.firstOrNull(),
            f["TEL"].orEmpty(),
            f["EMAIL"].orEmpty(),
            f["URL"].orEmpty(),
            f["ADR"]?.firstOrNull(),
            f["NOTE"]?.firstOrNull(),
        )
    }

    private fun splitFieldsMulti(body: String): Map<String, List<String>> {
        val out = linkedMapOf<String, MutableList<String>>()
        var i = 0
        val cur = StringBuilder()
        val parts = mutableListOf<String>()
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> { cur.append('\\').append(body[i + 1]); i += 2; continue }
                c == ';' -> { parts += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) parts += cur.toString()
        for (p in parts) {
            val colon = indexOfUnescaped(p, ':')
            if (colon <= 0) continue
            out.getOrPut(p.substring(0, colon).uppercase(Locale.ROOT)) { mutableListOf() } += unescape(p.substring(colon + 1))
        }
        return out
    }

    // ---- SMS : SMSTO:+33600000000:Bonjour / sms:+336…?body=… ----------------------------------

    private fun parseSms(raw: String): QrPayload {
        val rest = raw.substringAfter(':')
        val (number, body) = if (rest.contains('?')) {
            rest.substringBefore('?') to queryParams(rest.substringAfter('?'))["body"]
        } else {
            rest.substringBefore(':') to rest.substringAfter(':', "").takeIf { it.isNotEmpty() }
        }
        val n = number.trim()
        return if (n.isEmpty()) QrPayload.Text(raw) else QrPayload.Sms(raw, n, body)
    }

    // ---- E-mail -------------------------------------------------------------------------------

    private fun parseMailto(raw: String): QrPayload {
        val rest = raw.substring(7)
        val address = decode(rest.substringBefore('?')).trim()
        val q = if (rest.contains('?')) queryParams(rest.substringAfter('?')) else emptyMap()
        return if (address.isEmpty() && q["to"].isNullOrBlank()) {
            QrPayload.Text(raw)
        } else {
            QrPayload.Email(raw, address.ifEmpty { q["to"].orEmpty() }, q["subject"], q["body"])
        }
    }

    private fun parseMatMsg(raw: String): QrPayload {
        val f = splitFields(raw.substring(7))
        val to = f["TO"]?.trim().orEmpty()
        return if (to.isEmpty()) QrPayload.Text(raw) else QrPayload.Email(raw, to, f["SUB"], f["BODY"])
    }

    // ---- Géo : geo:48.8566,2.3522?q=Paris -----------------------------------------------------

    private fun parseGeo(raw: String): QrPayload.Geo? {
        val rest = raw.substring(4)
        val coords = rest.substringBefore('?').substringBefore(';').split(',')
        val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lon = coords.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val q = if (rest.contains('?')) queryParams(rest.substringAfter('?'))["q"] else null
        return QrPayload.Geo(raw, lat, lon, q?.takeIf { it.isNotBlank() })
    }

    // ---- Outils -------------------------------------------------------------------------------

    private fun queryParams(q: String): Map<String, String> =
        q.split('&').mapNotNull { kv ->
            val k = kv.substringBefore('=').lowercase(Locale.ROOT)
            if (k.isEmpty()) null else k to decode(kv.substringAfter('=', ""))
        }.toMap()

    /** Décodage %XX (UTF-8) et « + » : sans dépendance Android pour rester testable. */
    internal fun decode(s: String): String = runCatching {
        java.net.URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrDefault(s)
}
