package fyi.slot.konkr

import android.content.Context
import android.net.Uri
import android.util.Log

/** A disc's product code (SLUS-00594...), read natively from the CHD or PBP
 *  itself (crates/slot-android/src/disc_serial.rs). Null when the image has
 *  none (an early disc booting PSX.EXE, a TurboGrafx-CD) or can't be read. */
internal object DiscId {
    external fun serialFromFd(fd: Int, ext: String): String?

    fun serial(context: Context, uri: String, ext: String): String? = try {
        if (ext.equals("cue", ignoreCase = true)) cueSerial(context, Uri.parse(uri))
        else context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
            serialFromFd(pfd.fd, ext)?.takeIf { it.isNotBlank() }
        }
    } catch (error: Throwable) {
        Log.w("SlotDiscId", "No product code for ${uri.takeLast(60)}", error)
        null
    }

    /** A cue sheet's disc: its first track's file (the data track), beside
     *  the sheet in the library folder. */
    private fun cueSerial(context: Context, cue: Uri): String? {
        val sheet = context.contentResolver.openInputStream(cue)?.use { input ->
            val buf = ByteArray(64 * 1024)
            var n = 0
            while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
            String(buf, 0, n, Charsets.ISO_8859_1)
        } ?: return null
        val first = DiscSource.cueFiles(sheet).firstOrNull() ?: return null
        val docId = android.provider.DocumentsContract.getDocumentId(cue)
        val parent = docId.substringBeforeLast('/', "").ifEmpty { return null }
        val bin = android.provider.DocumentsContract.buildDocumentUriUsingTree(cue, "$parent/$first")
        return context.contentResolver.openFileDescriptor(bin, "r")?.use { pfd ->
            serialFromFd(pfd.fd, "bin")?.takeIf { it.isNotBlank() }
        }
    }
}
