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
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
            serialFromFd(pfd.fd, ext)?.takeIf { it.isNotBlank() }
        }
    } catch (error: Throwable) {
        Log.w("SlotDiscId", "No product code for ${uri.takeLast(60)}", error)
        null
    }
}
