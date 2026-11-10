package fyi.slot.konkr

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File

/**
 * A PlayStation disc is played where it is in the library: a CHD or PBP is
 * hundreds of MB, far too big to copy into the cache like a cartridge. The
 * document is opened read-only through SAF and the core is given a link to
 * that open file (/proc/self/fd/N), named with the image's extension, which
 * is how PCSX ReARMed tells a CHD from a PBP. The descriptor stays open until
 * the next disc is linked, so the core (and a Run-Ahead copy of it) can open
 * the link again at any time.
 */
internal object DiscSource {
    private const val DIR = "discs"
    private var open: ParcelFileDescriptor? = null

    /** The path the core opens for the disc at `uri`. */
    @Synchronized
    fun link(context: Context, uri: Uri, digest: String, ext: String): File {
        require(Systems.isDisc(ext)) { "Not a disc image" }
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot read this disc")
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val link = File(dir, linkName(digest, ext))
        try {
            dir.listFiles()?.forEach { it.delete() }
            Os.symlink(fdPath(pfd.fd), link.absolutePath)
        } catch (error: Exception) {
            pfd.close()
            throw IllegalStateException("Cannot open this disc", error)
        }
        open?.close()
        open = pfd
        return link
    }

    internal fun linkName(digest: String, ext: String) = "$digest.${ext.lowercase(java.util.Locale.ROOT)}"

    internal fun fdPath(fd: Int) = "/proc/self/fd/$fd"
}
