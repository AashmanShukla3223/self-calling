package com.parallax.selfcalling.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Builds presets from the device address book or from a vCard (.vcf) export.
 * Pure framework code — no third-party vCard library.
 */
object PresetImporter {

    private val INVALID_NUMBER_CHARS = Regex("[^0-9+*#]")
    private val EXTENSION = Regex("(?i)(?:;ext\\.?=|;extension=)\\s*\\d+")
    private val CHARSET_PARAM = Regex("(?i)CHARSET\\s*=\\s*\"?([^;\"]+)")

    /** Every phone number in the address book, de-duplicated by number. */
    @JvmStatic
    fun fromContacts(c: Context): List<Preset> {
        val out = ArrayList<Preset>()
        val seen = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cur ->
            val nameIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIndex < 0 || numberIndex < 0) return@use
            while (cur.moveToNext()) {
                val number = normalizeNumber(cur.getString(numberIndex))
                if (number.isEmpty() || !seen.add(number)) continue
                val name = cur.getString(nameIndex)?.trim().orEmpty()
                out.add(Preset(name.ifEmpty { "Contact" }, number))
            }
        }
        return out
    }

    /** Parses every vCard in a .vcf document. Handles folded lines and quoted-printable values. */
    @JvmStatic
    fun fromVcf(c: Context, uri: Uri): List<Preset> {
        val input: InputStream = c.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open that file")
        val text = input.use { decodeText(it.readBytes()) }
        return parseVcards(text)
    }

    /** Keeps digits and + * #, drops extensions, converts 00-prefixed international numbers. */
    @JvmStatic
    fun normalizeNumber(raw: String?): String {
        if (raw == null) return ""
        var value = raw.trim()
        if (value.length >= 4 && value.substring(0, 4).equals("tel:", ignoreCase = true)) {
            value = value.substring(4)
        }
        value = Uri.decode(value)
        value = value.replace(EXTENSION, "")
        value = value.replace(INVALID_NUMBER_CHARS, "")
        if (value.startsWith("00") && value.length >= 8) {
            value = "+" + value.substring(2)
        }
        if (value.none { it.isDigit() }) return ""
        return value
    }

    // ---- vCard parsing ----

    private fun parseVcards(text: String): List<Preset> {
        val out = ArrayList<Preset>()
        val seen = HashSet<String>()
        val lines = unfold(text)
        var inCard = false
        var fullName: String? = null
        var structuredName: String? = null
        var numbers = ArrayList<String>()

        fun flush() {
            val name = fullName?.takeIf { it.isNotBlank() }
                ?: structuredName?.takeIf { it.isNotBlank() }
                ?: "Contact"
            for (raw in numbers) {
                val number = normalizeNumber(raw)
                if (number.isEmpty() || !seen.add(number)) continue
                out.add(Preset(name, number))
            }
            fullName = null
            structuredName = null
            numbers = ArrayList()
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            val keyword = line.trim().uppercase(Locale.US)
            if (keyword == "BEGIN:VCARD") {
                inCard = true
                fullName = null
                structuredName = null
                numbers = ArrayList()
                continue
            }
            if (keyword == "END:VCARD") {
                if (inCard) flush()
                inCard = false
                continue
            }
            if (!inCard) continue

            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val left = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val property = left.substringBefore(';').substringAfterLast('.').uppercase(Locale.US)
            val params = left.substringAfter(';', "")

            when (property) {
                "FN" -> fullName = decodeValue(value, params)
                "N" -> structuredName = nameFromStructured(decodeValue(value, params))
                "TEL" -> {
                    val decoded = decodeValue(value, params)
                    if (decoded.isNotBlank()) numbers.add(decoded)
                }
            }
        }
        return out
    }

    /** Removes vCard line folding and quoted-printable soft line breaks. */
    private fun unfold(text: String): List<String> {
        val raw = text.split(Regex("\\r\\n|\\n|\\r"))
        val logical = ArrayList<String>(raw.size)
        for (line in raw) {
            if (logical.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                logical[logical.size - 1] = logical[logical.size - 1] + line.substring(1)
            } else {
                logical.add(line)
            }
        }

        val joined = ArrayList<String>(logical.size)
        var i = 0
        while (i < logical.size) {
            var line = logical[i]
            if (isQuotedPrintable(line)) {
                while (line.endsWith("=") && i + 1 < logical.size) {
                    val next = logical[i + 1]
                    val nextKeyword = next.trim().uppercase(Locale.US)
                    if (nextKeyword == "BEGIN:VCARD" || nextKeyword == "END:VCARD") break
                    line = line.dropLast(1) + next
                    i++
                }
            }
            joined.add(line)
            i++
        }
        return joined
    }

    private fun isQuotedPrintable(line: String): Boolean {
        val colon = line.indexOf(':')
        val header = if (colon >= 0) line.substring(0, colon) else line
        return header.uppercase(Locale.US).contains("QUOTED-PRINTABLE")
    }

    private fun decodeValue(value: String, params: String): String {
        if (!params.uppercase(Locale.US).contains("QUOTED-PRINTABLE")) {
            return unescape(value)
        }

        val charsetName = CHARSET_PARAM.find(params)?.groupValues?.getOrNull(1)?.trim()
        val bytes = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 < value.length) {
                val hi = hexValue(value[i + 1])
                val lo = hexValue(value[i + 2])
                if (hi >= 0 && lo >= 0) {
                    bytes.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            // vCard quoted-printable uses "_" for a space (RFC 2045 style QP is a superset).
            bytes.write(if (c == '_') 0x20 else c.code)
            i++
        }
        return decodeBytes(bytes.toByteArray(), charsetName)
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /** Handles \n, \\, \;, \, and \: escapes used by vCards. */
    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> sb.append(' ')
                    '\\', ';', ',', ':' -> sb.append(next)
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString().trim().removeSurrounding("\"")
    }

    private fun nameFromStructured(value: String): String? {
        val parts = value.split(';')
        val family = parts.getOrNull(0)?.trim().orEmpty()
        val given = parts.getOrNull(1)?.trim().orEmpty()
        val middle = parts.getOrNull(2)?.trim().orEmpty()
        val name = listOf(given, middle, family).filter { it.isNotEmpty() }.joinToString(" ")
        return name.ifBlank { null }
    }

    // ---- text decoding ----

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte()
            && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        return decodeBytes(bytes, "UTF-8")
    }

    private fun decodeBytes(bytes: ByteArray, charsetName: String?): String {
        if (charsetName != null) {
            try {
                return String(bytes, Charset.forName(charsetName))
            } catch (_: Exception) {
                // Fall through to the auto-detection below.
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }
}
