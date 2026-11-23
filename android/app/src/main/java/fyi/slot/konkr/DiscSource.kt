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
    private var open: List<ParcelFileDescriptor> = emptyList()

    /** The path the core opens for the disc at `uri`. */
    @Synchronized
    fun link(context: Context, uri: Uri, digest: String, ext: String): File {
        require(Systems.isDisc(ext)) { "Not a disc image" }
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot read this disc")
        // The disc's own path, where the core may open it (All files access).
        realPath(pfd)?.let { own ->
            open.forEach { it.close() }
            open = listOf(pfd)
            return own
        }
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val link = File(dir, linkName(digest, ext))
        try {
            dir.listFiles()?.forEach { it.delete() }
            Os.symlink(fdPath(pfd.fd), link.absolutePath)
        } catch (error: Exception) {
            pfd.close()
            throw IllegalStateException("Cannot open this disc", error)
        }
        open.forEach { it.close() }
        open = listOf(pfd)
        return link
    }

    /**
     * A multi-disc game (DiscGroups): every disc opened and linked like a
     * single one, and a playlist (.m3u) naming the links, which is what the
     * core loads; its disc controls then swap between them. All stay open
     * until the next game's discs are linked.
     */
    @Synchronized
    fun linkSet(context: Context, discs: List<Pair<String, String>>, digest: String): File {
        require(discs.isNotEmpty()) { "No discs" }
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val opened = ArrayList<ParcelFileDescriptor>()
        try {
            dir.listFiles()?.forEach { it.delete() }
            val lines = StringBuilder()
            for ((i, disc) in discs.withIndex()) {
                val (uri, name) = disc
                val ext = name.substringAfterLast('.', "chd").lowercase(java.util.Locale.ROOT)
                require(ext == "chd" || ext == "pbp") { "Not a disc image: $name" }
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                    ?: error("Cannot read disc ${i + 1}")
                opened.add(pfd)
                val own = realPath(pfd)
                if (own != null) {
                    lines.append(own.absolutePath).append('\n')
                    continue
                }
                val link = linkName("$digest-${i + 1}", ext)
                Os.symlink(fdPath(pfd.fd), File(dir, link).absolutePath)
                lines.append(link).append('\n')
            }
            val playlist = File(dir, "$digest.m3u")
            playlist.writeText(lines.toString(), Charsets.UTF_8)
            open.forEach { it.close() }
            open = opened
            return playlist
        } catch (error: Exception) {
            opened.forEach { runCatching { it.close() } }
            throw IllegalStateException("Cannot open this game's discs", error)
        }
    }

    internal fun linkName(digest: String, ext: String) = "$digest.${ext.lowercase(java.util.Locale.ROOT)}"

    internal fun fdPath(fd: Int) = "/proc/self/fd/$fd"

    /**
     * The disc's own file, when slot. may open it by path. Since Android 11
     * an app can't reopen a document's /proc/self/fd link to a file that
     * isn't a photo, video or song (a CHD) without All files access, so the
     * core's own open of the link fails: PCSX ReARMed won't start, and the
     * TurboGrafx-CD System Card finds no disc and shows its CD player. With
     * that access the core opens the real file. Null otherwise: the link.
     */
    internal fun realPath(pfd: ParcelFileDescriptor): File? = try {
        val path = storagePath(Os.readlink(fdPath(pfd.fd)))
        path?.let(::File)?.takeIf { file ->
            file.isFile && file.length() == pfd.statSize &&
                runCatching { java.io.FileInputStream(file).close() }.isSuccess
        }
    } catch (error: Exception) {
        null
    }

    /** A provider's view of a file -> the path apps open it at:
     *  /mnt/user/0/emulated/0/x and /mnt/media_rw/ABCD-1234/x -> /storage/... */
    internal fun storagePath(target: String): String? = when {
        target.startsWith("/storage/") -> target
        target.startsWith("/mnt/user/") ->
            "/storage/" + target.removePrefix("/mnt/user/").substringAfter('/', "").ifEmpty { return null }
        target.startsWith("/mnt/pass_through/") ->
            "/storage/" + target.removePrefix("/mnt/pass_through/").substringAfter('/', "").ifEmpty { return null }
        target.startsWith("/mnt/media_rw/") -> "/storage/" + target.removePrefix("/mnt/media_rw/")
        else -> null
    }

    /** Whether discs can be read by path (All files access, Android 11+). */
    fun canOpenByPath(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ||
            android.os.Environment.isExternalStorageManager()
}
