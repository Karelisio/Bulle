package com.karelisio.bulle.core.style

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Partage d'un style : fichier `.bulle-style` (JSON versionné), texte collé ou QR code.
 *
 * - JSON : `{"format":"bulle-style","version":1,"name":…,"style":{…},"motion":{…}}`.
 * - QR / texte court : `bulle-style:1:<base64url sans remplissage du JSON compressé (deflate brut)>`.
 *
 * Tout est pur (aucun contexte Android) et testé. À l'import, la validation est stricte :
 * format et version reconnus, valeurs dans les bornes, énumérations connues ; les champs
 * inconnus (version future, ajouts) sont ignorés.
 */
object StyleShare {
    const val FORMAT = "bulle-style"
    const val VERSION = 1
    const val SCHEME = "bulle-style"
    const val FILE_EXTENSION = "bulle-style"
    const val MIME_TYPE = "application/octet-stream"

    /** Taille maximale acceptée pour un texte, un fichier ou un JSON décompressé (anti-bombe). */
    const val MAX_BYTES = 32 * 1024

    private val QrPattern = Regex("""bulle-style:(\d{1,3}):([A-Za-z0-9_-]+={0,2})""")

    @Serializable
    private data class StyleFile(
        val format: String = "",
        val version: Int = 0,
        val name: String = "",
        val style: PackStyle = PackStyle(),
        val motion: PackMotion = PackMotion(),
    )

    private val WriteJson = Json { encodeDefaults = true; prettyPrint = true; prettyPrintIndent = "  " }
    private val CompactJson = Json { encodeDefaults = false }

    /** Lecture stricte : énumérations inconnues et types faux = refus ; champs inconnus ignorés. */
    private val ReadJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
        isLenient = false
    }

    /** Contenu d'un fichier `.bulle-style` (lisible, toutes les valeurs écrites). */
    fun encodeJson(pack: StylePack): String = WriteJson.encodeToString(StyleFile.serializer(), pack.toFile())

    /** Texte court pour QR code et partage texte. */
    fun encodeQr(pack: StylePack): String {
        // Valeurs par défaut omises : QR plus petit, donc plus facile à scanner.
        // Le format et la version diffèrent des valeurs par défaut : ils sont toujours écrits.
        val json = CompactJson.encodeToString(StyleFile.serializer(), pack.toFile())
        return "$SCHEME:$VERSION:" + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(json.encodeToByteArray()))
    }

    /** Nom de fichier proposé à l'export. */
    fun fileName(pack: StylePack): String {
        val base = pack.name.ifBlank { "style" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .replace(' ', '-')
            .take(32)
            .ifBlank { "style" }
        return "$base.$FILE_EXTENSION"
    }

    /**
     * Décode un style depuis un texte quelconque : QR (`bulle-style:1:…`, éventuellement au
     * milieu d'un message partagé) ou JSON d'un fichier. Null si illisible ou invalide.
     */
    fun decode(text: String): StylePack? {
        if (text.length > MAX_BYTES * 2) return null
        val trimmed = text.trim().removePrefix("﻿")
        if (trimmed.startsWith("{")) return decodeJson(trimmed)
        val match = QrPattern.find(trimmed) ?: return null
        val version = match.groupValues[1].toIntOrNull() ?: return null
        if (version !in 1..VERSION) return null
        val bytes = runCatching { Base64.getUrlDecoder().decode(match.groupValues[2]) }.getOrNull() ?: return null
        val json = inflate(bytes) ?: return null
        return decodeJson(json.decodeToString())
    }

    /** Décode le JSON d'un fichier `.bulle-style`. */
    fun decodeJson(json: String): StylePack? {
        if (json.length > MAX_BYTES) return null
        val file = runCatching { ReadJson.decodeFromString(StyleFile.serializer(), json) }.getOrNull() ?: return null
        if (file.format != FORMAT || file.version !in 1..VERSION) return null
        val pack = StylePack(
            name = file.name.trim(),
            style = file.style,
            // Animations inconnues (version plus récente) ignorées, clés vérifiées par problems().
            motion = file.motion.copy(
                entryAnimations = file.motion.entryAnimations.filterValues { EntryAnimation.parse(it) != null },
            ),
        )
        return pack.takeIf { it.isValid() }
    }

    private fun StylePack.toFile() = StyleFile(FORMAT, VERSION, name, style, motion)

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            d.setInput(data)
            d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally {
            d.end()
        }
    }

    /** Décompression bornée à [MAX_BYTES] ; null si corrompu ou trop gros. */
    private fun inflate(data: ByteArray): ByteArray? {
        val inf = Inflater(true)
        return try {
            // Deflate brut (nowrap) : un octet factice final est requis par l'API Java.
            inf.setInput(data + 0.toByte())
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) return null
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) return null
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inf.end()
        }
    }
}
