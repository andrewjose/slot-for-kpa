package fyi.slot.konkr

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The official names ArtGuess chooses from, kept a week in the cache and
 * parsed once per app run:
 * - each cart system's No-Intro DAT (libretro-database), with CRCs, so a
 *   guessed game's label is asked for by that game's own CRC;
 * - the PlayStation's Redump DAT, for a disc's product code -> its name;
 * - the box art libretro-thumbnails holds for each disc system (its file
 *   list, from GitHub's API), which is what a disc's label is cut from.
 * A stale copy is used offline; nothing is fetched during gameplay (the
 * label passes only run on the idle shelf).
 */
internal object ArtCatalogs {
    private const val TAG = "SlotArtCatalogs"
    private const val DB = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/"
    private const val THUMBS_API = "https://api.github.com/repos/libretro-thumbnails/"
    const val THUMBS_RAW = "https://raw.githubusercontent.com/libretro-thumbnails/"
    private const val MAX_BYTES = 6 * 1024 * 1024
    private const val FRESH_MS = 7L * 24 * 60 * 60 * 1000

    private val dats = mapOf(
        Systems.GB to "no-intro/Nintendo - Game Boy.dat",
        Systems.GBC to "no-intro/Nintendo - Game Boy Color.dat",
        Systems.GBA to "no-intro/Nintendo - Game Boy Advance.dat",
        Systems.NES to "no-intro/Nintendo - Nintendo Entertainment System.dat",
        Systems.SNES to "no-intro/Nintendo - Super Nintendo Entertainment System.dat",
        Systems.MD to "no-intro/Sega - Mega Drive - Genesis.dat",
        Systems.PSX to "redump/Sony - PlayStation.dat",
    )

    /** libretro-thumbnails repository of each disc system. */
    val thumbRepos = mapOf(
        Systems.PSX to "Sony_-_PlayStation",
        Systems.TGCD to "NEC_-_PC_Engine_CD_-_TurboGrafx-CD",
    )

    private val parsed = HashMap<String, List<ArtGuess.Known>>()
    private val failedAt = HashMap<String, Long>()

    private fun dir(context: Context) = File(context.cacheDir, "slot-art-catalogs").apply { mkdirs() }

    private fun download(url: String, accept: String, shouldContinue: () -> Boolean): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "slot-for-android")
        }
        try {
            if (connection.responseCode != 200) {
                Log.w(TAG, "HTTP ${connection.responseCode} for ${url.takeLast(90)}")
                return null
            }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    if (!shouldContinue()) return null
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > MAX_BYTES) return null
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun save(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (error: Exception) {
            atomic.failWrite(out)
            Log.w(TAG, "Catalog cache not writable", error)
        }
    }

    /** A cached file, refreshed when older than a week; the old copy offline. */
    private fun fetched(context: Context, name: String, shouldContinue: () -> Boolean,
                        get: () -> ByteArray?): ByteArray? {
        val file = File(dir(context), name)
        val fresh = file.isFile && file.length() in 1..MAX_BYTES.toLong() &&
            System.currentTimeMillis() - file.lastModified() < FRESH_MS
        if (!fresh && shouldContinue()) {
            val last = failedAt[name] ?: 0L
            // An outage is asked again after ten minutes, not every cart.
            if (android.os.SystemClock.elapsedRealtime() - last > 600_000L || last == 0L) {
                val bytes = try { get() } catch (error: Exception) {
                    Log.w(TAG, "Catalog $name unavailable", error); null
                }
                if (bytes != null) {
                    save(file, bytes)
                    return bytes
                }
                failedAt[name] = android.os.SystemClock.elapsedRealtime()
            }
        }
        return if (file.isFile && file.length() in 1..MAX_BYTES.toLong())
            runCatching { file.readBytes() }.getOrNull() else null
    }

    /** A system's official names from its DAT; null when it can't be had yet. */
    @Synchronized
    fun dat(context: Context, platform: String, shouldContinue: () -> Boolean): List<ArtGuess.Known>? {
        parsed["dat:$platform"]?.let { return it }
        val path = dats[platform] ?: return null
        val bytes = fetched(context, "$platform.dat", shouldContinue) {
            download(DB + path.replace(" ", "%20"), "text/plain", shouldContinue)
                ?.takeIf { String(it, 0, minOf(12, it.size), Charsets.UTF_8) == "clrmamepro (" }
        } ?: return null
        val known = ArtGuess.parseDat(String(bytes, Charsets.UTF_8)).map { it.copy(tag = platform) }
        if (known.isEmpty()) return null
        parsed["dat:$platform"] = known
        return known
    }

    /** The games a cart may be: a Game Boy hack may be a Color one's and back. */
    fun cartCatalog(context: Context, platform: String, shouldContinue: () -> Boolean): List<ArtGuess.Known>? {
        val group = when (platform) {
            Systems.GB, Systems.GBC -> listOf(platform, if (platform == Systems.GB) Systems.GBC else Systems.GB)
            else -> listOf(platform)
        }
        val own = dat(context, group.first(), shouldContinue) ?: return null
        val other = group.drop(1).flatMap { dat(context, it, shouldContinue).orEmpty() }
        return own + other
    }

    /** A disc system's box art names (without ".png"), as libretro-thumbnails
     *  names them; null when the list can't be had yet. */
    @Synchronized
    fun boxArts(context: Context, platform: String, shouldContinue: () -> Boolean): List<ArtGuess.Known>? {
        parsed["box:$platform"]?.let { return it }
        val repo = thumbRepos[platform] ?: return null
        val bytes = fetched(context, "$platform.boxarts.txt", shouldContinue) {
            val root = download("$THUMBS_API$repo/git/trees/master", "application/vnd.github+json",
                shouldContinue) ?: return@fetched null
            val tree = JSONObject(String(root, Charsets.UTF_8)).optJSONArray("tree") ?: return@fetched null
            val sha = (0 until tree.length()).map { tree.getJSONObject(it) }
                .firstOrNull { it.optString("path") == "Named_Boxarts" }?.optString("sha")
                ?: return@fetched null
            val list = download("$THUMBS_API$repo/git/trees/$sha", "application/vnd.github+json",
                shouldContinue) ?: return@fetched null
            val files: JSONArray = JSONObject(String(list, Charsets.UTF_8)).optJSONArray("tree")
                ?: return@fetched null
            (0 until files.length()).map { files.getJSONObject(it).optString("path") }
                .filter { it.endsWith(".png") }
                .joinToString("\n") { it.removeSuffix(".png") }
                .takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        } ?: return null
        val known = String(bytes, Charsets.UTF_8).lines().filter { it.isNotBlank() }
            .map { ArtGuess.Known(it, tag = platform) }
        if (known.isEmpty()) return null
        parsed["box:$platform"] = known
        return known
    }

    /** A box art's URL in libretro-thumbnails. */
    fun boxArtUrl(platform: String, name: String): String? {
        val repo = thumbRepos[platform] ?: return null
        val file = java.net.URLEncoder.encode("$name.png", "UTF-8").replace("+", "%20")
        return "$THUMBS_RAW$repo/master/Named_Boxarts/$file"
    }

    /** Official names of a disc with this product code (all its discs). */
    fun discNames(context: Context, platform: String, serial: String,
                  shouldContinue: () -> Boolean): List<String> {
        val want = serial.uppercase().filter { it.isLetterOrDigit() }
        return dat(context, platform, shouldContinue).orEmpty().filter { k ->
            k.serial?.split(',', ' ', '/')?.any { s -> s.uppercase().filter { it.isLetterOrDigit() } == want } == true
        }.map { it.name }
    }
}
