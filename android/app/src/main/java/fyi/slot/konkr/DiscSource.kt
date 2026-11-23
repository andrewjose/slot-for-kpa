package fyi.slot.konkr

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
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
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val opened = ArrayList<ParcelFileDescriptor>()
        try {
            dir.listFiles()?.forEach { it.delete() }
            val placed = place(context, uri, ext, digest, dir, opened)
            open.forEach { it.close() }
            open = opened
            return File(dir, placed)
        } catch (error: Exception) {
            opened.forEach { runCatching { it.close() } }
            throw IllegalStateException("Cannot open this disc", error)
        }
    }

    /**
     * A multi-disc game (DiscGroups): every disc placed like a single one,
     * and a playlist (.m3u) naming them, which is what the core loads; its
     * disc controls then swap between them. All stay open until the next
     * game's discs are placed.
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
                require(ext in PLAYLIST_EXT) { "Not a disc image: $name" }
                lines.append(place(context, Uri.parse(uri), ext, "$digest-${i + 1}", dir, opened)).append('\n')
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

    /** The images a playlist may name. */
    private val PLAYLIST_EXT = setOf("chd", "pbp", "iso", "cue")

    /**
     * One disc in `dir`, named `base` (slot. keys a game's caches and states
     * by that name, a digest): a link to a single-file image, or for a cue
     * sheet a copy of the sheet whose tracks name their files by a path the
     * core can open. Returns the name placed.
     */
    private fun place(context: Context, uri: Uri, ext: String, base: String, dir: File,
                      opened: MutableList<ParcelFileDescriptor>): String {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot read this disc")
        opened.add(pfd)
        val name = linkName(base, ext)
        if (ext.lowercase(java.util.Locale.ROOT) != "cue") {
            Os.symlink(openable(pfd), File(dir, name).absolutePath)
            return name
        }
        // Read through a duplicate, so the open document keeps its offset.
        val sheet = ParcelFileDescriptor.AutoCloseInputStream(pfd.dup()).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < 256 * 1024) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        require(sheet.size < 256 * 1024) { "Cue sheet too large" }
        val own = realPath(pfd)?.parentFile
        var track = 0
        val text = rewriteCue(String(sheet, Charsets.ISO_8859_1)) { file ->
            track++
            // The track's file beside the sheet: by its own path with All
            // files access, else through the document it is in the library.
            own?.let { File(it, file) }?.takeIf { it.isFile && it.canRead() }?.absolutePath
                ?: run {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val parent = docId.substringBeforeLast('/', "")
                    require(parent.isNotEmpty()) { "Cue sheet's tracks can't be found" }
                    val child = DocumentsContract.buildDocumentUriUsingTree(uri, "$parent/$file")
                    val bin = context.contentResolver.openFileDescriptor(child, "r")
                        ?: error("Cannot read $file")
                    opened.add(bin)
                    val link = File(dir, "$base-t$track.${file.substringAfterLast('.', "bin")}")
                    Os.symlink(openable(bin), link.absolutePath)
                    link.absolutePath
                }
        }
        File(dir, name).writeBytes(text.toByteArray(Charsets.ISO_8859_1))
        return name
    }

    /** What a core opens for an open document: its own path with All files
     *  access, else the descriptor's link. */
    private fun openable(pfd: ParcelFileDescriptor) = realPath(pfd)?.absolutePath ?: fdPath(pfd.fd)

    /**
     * A cue sheet with each FILE line's file (relative to the sheet, as
     * written: quoted or bare, / or \) replaced by `path(file)`; every other
     * line as it was.
     */
    internal fun rewriteCue(sheet: String, path: (String) -> String): String =
        sheet.lines().joinToString("\n") { line ->
            val m = cueFile.find(line) ?: return@joinToString line
            val file = (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace('\\', '/')
            require(file.isNotEmpty() && file.split('/').none { it == ".." }) { "Unsafe cue track: $file" }
            "FILE \"${path(file)}\" ${m.groupValues[3]}"
        }

    /** The files a cue sheet's tracks are in, relative to the sheet. */
    fun cueFiles(sheet: String): List<String> = sheet.lines().mapNotNull { line ->
        cueFile.find(line)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).replace('\\', '/') }
    }

    private val cueFile = Regex("""^\s*FILE\s+(?:"([^"]*)"|(\S+))\s+(\S+)\s*$""", RegexOption.IGNORE_CASE)

    internal fun linkName(digest: String, ext: String) = "$digest.${ext.lowercase(java.util.Locale.ROOT)}"

    internal fun fdPath(fd: Int) = "/proc/self/fd/$fd"

    /**
     * The disc's own file, when slot. may open it by path. Since Android 11
     * an app can't reopen a document's /proc/self/fd link to a file that
     * isn't a photo, video or song (a CHD) without All files access, so the
     * core's own open of the link may fail. With that access the link points
     * at the real file instead. Null otherwise: the descriptor's link.
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
