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

    /** Whether a label named `official` is another game's than `file`'s:
     *  the names differ once tags, case and punctuation are dropped. */
    fun differs(file: String, official: String): Boolean = bare(file) != bare(official)

    private fun bare(name: String): String {
        var depth = 0
        val out = StringBuilder()
        for (ch in name.substringBeforeLast('.').let { if (it.isEmpty()) name else it }) {
            when (ch) {
                '(', '[' -> depth++
                ')', ']' -> depth = maxOf(0, depth - 1)
                else -> if (depth == 0 && ch.isLetterOrDigit()) out.append(ch.lowercaseChar())
            }
        }
        return out.toString()
    }
}
