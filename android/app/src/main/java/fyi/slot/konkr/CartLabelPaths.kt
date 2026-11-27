package fyi.slot.konkr

import java.io.File

/**
 * The same unsigned FNV-1a path identity as slot-android's core_selection::key.
 * Opaque SAF URIs must never be replaced with filename-only label keys.
 */
internal object CartLabelPaths {
    fun file(filesDir: File, romUri: String): File {
        var hash = -3750763034362895579L // 0xcbf29ce484222325
        for (byte in romUri.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toInt() and 0xff).toLong()) * 0x100000001b3L
        }
        val name = java.lang.Long.toUnsignedString(hash, 16).padStart(16, '0')
        return File(File(filesDir, "Labels"), "$name.png")
    }

    /**
     * Beside a label borrowed from another game (a hack's or a renamed
     * disc's guess): `<label>.title`, which makes the shelf print the cart's
     * own file name across the label's bottom (slot-ui `borrowed_label`).
     */
    fun markBorrowed(label: File, borrowed: Boolean) {
        val marker = File(label.parentFile, label.nameWithoutExtension + ".title")
        try {
            if (borrowed) marker.writeBytes(ByteArray(0)) else marker.delete()
        } catch (_: Exception) {}
    }

    /** Whether a label named `official` is another game's than `file`'s: the
     *  names differ once case, punctuation and standard dump tags are
     *  dropped. Standard tags are regions, languages, Rev N, vX.Y, Disc N,
     *  Track N, serials, re-release labels and the like; any other tag
     *  ("QoL v1.2", "T-En by ...", "Undub", "English") marks a hack or a
     *  translation, so it keeps the name apart (Andrew, 2026-11-24). */
    fun differs(file: String, official: String): Boolean = bare(file) != bare(official)

    private val REGIONS = setOf(
        "usa", "japan", "europe", "world", "asia", "korea", "australia", "brazil", "canada", "china",
        "france", "germany", "italy", "spain", "netherlands", "sweden", "taiwan", "hong kong", "uk",
        "russia", "scandinavia", "latin america",
        "en", "ja", "fr", "de", "es", "it", "nl", "pt", "sv", "no", "da", "fi", "zh", "ko", "pl", "ru",
        "pt-br", "u", "j", "e", "jp", "us", "eu", "ntsc", "pal", "ntsc-u", "ntsc-j")

    private val STANDARD = listOf(
        "rev\\s*[0-9a-z.]+", "v\\d+(\\.\\d+)*[a-z]?", "disc\\s*\\d+(\\s*of\\s*\\d+)?", "track\\s*\\d+",
        "beta\\s*\\d*", "proto\\s*\\d*", "demo", "sample", "kiosk", "unl", "alt\\s*\\d*", "!",
        "[abhfno]\\d*", "[a-z]{4}-?\\d{5}", "\\d{4}-\\d{2}-\\d{2}",
        "greatest hits", "platinum", "player's choice", "the best", "psone books", "sega classic", "np",
        "virtual console", "switch online", "genteiban", "limited edition", "reprint", "sgb enhanced",
        "gb compatible", "cgb\\+sgb enhanced", "rumble version", "aftermarket",
    ).map { Regex(it) }

    /** A dump tag that says nothing about which game it is. */
    internal fun standardTag(tag: String): Boolean {
        val t = tag.trim().lowercase()
        if (t.split(',').all { it.trim() in REGIONS }) return true
        return STANDARD.any { it.matches(t) }
    }

    private val EXTENSION = Regex("^(.*)\\.([A-Za-z0-9]{1,4})$")

    internal fun bare(name: String): String {
        // Only a real extension is dropped: "Dr. Mario" keeps its "Dr."
        val stem = EXTENSION.matchEntire(name)?.groupValues?.get(1) ?: name
        var depth = 0
        val out = StringBuilder()
        val tag = StringBuilder()
        for (ch in stem) {
            when (ch) {
                '(', '[' -> { if (depth == 0) tag.setLength(0); depth++ }
                ')', ']' -> {
                    depth = maxOf(0, depth - 1)
                    if (depth == 0 && !standardTag(tag.toString())) {
                        out.append('#')
                        out.append(tag.toString().lowercase().filter { it.isLetterOrDigit() })
                    }
                }
                else -> {
                    if (depth > 0) tag.append(ch)
                    else if (ch.isLetterOrDigit()) out.append(ch.lowercaseChar())
                }
            }
        }
        return out.toString()
    }
}
