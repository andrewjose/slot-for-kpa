package fyi.slot.konkr

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Safely stages user-owned BIOS into the cores' private libretro system directory.
 * The selected SAF tree is read-only; only known original Slot BIOS names
 * are considered. Called from the same single-thread executor as ROM staging.
 */
internal object BiosLibrary {
    private const val PS1_BIOS = 524_288
    private const val SYSTEM_CARD = 262_144

    /** Each known BIOS and the sizes a good dump has. */
    private val lengths: Map<String, Set<Int>> = mapOf(
        "gba_bios.bin" to setOf(16_384),
        "gbc_bios.bin" to setOf(2_304),
        "gb_bios.bin" to setOf(256)
    ) +
        // PlayStation (PCSX ReARMed finds these by name; optional, it has
        // its own BIOS otherwise).
        listOf("scph5500.bin", "scph5501.bin", "scph5502.bin", "scph1001.bin",
            "scph7001.bin", "scph7502.bin", "scph101.bin", "scph1000.bin",
            "scph3000.bin", "scph3500.bin", "psxonpsp660.bin")
            .associateWith { setOf(PS1_BIOS) } +
        // TurboGrafx-CD System Cards (Beetle PCE Fast needs syscard3.pce),
        // with or without a 512-byte copier header.
        listOf("syscard3.pce", "syscard2.pce", "syscard1.pce", "gexpress.pce")
            .associateWith { setOf(SYSTEM_CARD, SYSTEM_CARD + 512) }

    fun importFrom(context: Context, folder: Uri): List<String> {
        val resolver = context.contentResolver
        val found = linkedMapOf<String, ByteArray>()
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder, DocumentsContract.getTreeDocumentId(folder)
        )
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(columns[0])
            val nameCol = cursor.getColumnIndexOrThrow(columns[1])
            val mimeCol = cursor.getColumnIndexOrThrow(columns[2])
            val sizeCol = cursor.getColumnIndexOrThrow(columns[3])
            while (cursor.moveToNext()) {
                if (cursor.getString(mimeCol) ==
                    DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = cursor.getString(nameCol)?.lowercase() ?: continue
                val sizes = lengths[name] ?: continue
                val expected = sizes.max()
                if (cursor.isNull(sizeCol).not() &&
                    cursor.getLong(sizeCol) > expected.toLong()) {
                    continue
                }
                val uri = DocumentsContract.buildDocumentUriUsingTree(
                    folder, cursor.getString(idCol)
                )
                val bytes = resolver.openInputStream(uri)?.use { stream ->
                    // Android 12 compatibility: bounded read, no Java 9
                    // InputStream.readNBytes API dependency.
                    val data = ByteArray(expected + 1)
                    var count = 0
                    while (count < data.size) {
                        val n = stream.read(data, count, data.size - count)
                        if (n < 0) break
                        if (n == 0) continue
                        count += n
                    }
                    data.copyOf(count)
                } ?: continue
                if (bytes.size !in sizes) continue
                // Original Slot also recognizes the standard GBA BIOS prefix.
                if (name == "gba_bios.bin" && bytes[0] != 0x18.toByte()) continue
                found[name] = bytes
            }
        } ?: error("Cannot read selected BIOS folder")

        require(found.isNotEmpty()) {
            "No known BIOS found (gba_bios.bin, gb_bios.bin, gbc_bios.bin, " +
                "a PlayStation scph*.bin or a TurboGrafx-CD syscard3.pce)"
        }
        val dest = File(context.filesDir, "BIOS").apply { mkdirs() }
        check(dest.isDirectory) { "BIOS destination unavailable" }

        // Stage every available BIOS first; only publish complete validated
        // files. Never write to the user-owned folder or erase the ROM library.
        val pending = linkedMapOf<String, File>()
        try {
            for ((name, bytes) in found) {
                val temp = File(dest, ".$name.tmp")
                temp.outputStream().use { out -> out.write(bytes) }
                pending[name] = temp
            }
            for ((name, temp) in pending) {
                val target = File(dest, name)
                check(temp.renameTo(target)) { "Could not install $name" }
            }
            // Switching folders should not silently retain a BIOS that no
            // longer exists in the newly selected folder.
            for (name in lengths.keys - found.keys) {
                File(dest, name).delete()
            }
        } finally {
            pending.values.forEach { it.delete() }
        }
        return found.keys.toList()
    }
}
