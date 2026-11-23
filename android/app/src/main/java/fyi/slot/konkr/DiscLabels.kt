package fyi.slot.konkr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Labels for PlayStation and TurboGrafx-CD discs: the game's front cover from
 * libretro-thumbnails, which the shelf fits into the cart's label like any
 * other picture.
 *
 * Which cover: a PlayStation disc's product code (read from the CHD or PBP
 * itself, DiscId) names the game in Redump's list, so a renamed, translated
 * or hacked disc still gets its game's cover. Otherwise, and for every
 * TurboGrafx-CD disc (they carry no code), the closest box art name to the
 * filename (ArtGuess). A wrong guess is replaced in Cart Studio (slot. label,
 * Picture...), and a label Cart Studio saved is never overwritten.
 *
 * Bounded like the cart passes: a few discs per pass, only on the idle shelf,
 * and a disc with no cover close enough isn't asked again until its file
 * changes.
 */
internal object DiscLabels {
    private const val TAG = "SlotDiscLabels"
    private const val LIBRARY = "rom-library-cache-v1.json"
    private const val STATE = "disc-labels-v1.json"
    /** The covers as downloaded, for Cart Studio's Real Label. */
    private const val COVERS = "disc-covers"
    private const val MAX_LIBRARY_BYTES = RomLibrary.CACHE_MAX_BYTES.toLong()
    private const val MAX_IMAGE = 8 * 1024 * 1024
    private const val MAX_PER_PASS = 24
    /** Covers are kept this tall: the label well is far smaller. */
    private const val COVER_H = 720

    private val retry = AutoLabelRetryPolicy()

    internal data class Disc(
        val uri: String, val platform: String, val title: String, val filename: String,
        val size: Long, val modified: Long,
    )

    private fun discs(context: Context): List<Disc> {
        val file = File(context.filesDir, LIBRARY)
        if (!file.isFile || file.length() !in 1..MAX_LIBRARY_BYTES) return emptyList()
        val root = try { JSONObject(file.readText(Charsets.UTF_8)) } catch (_: Exception) { return emptyList() }
        val rows = root.optJSONArray("games") ?: return emptyList()
        return (0 until rows.length().coerceAtMost(RomLibrary.MAX_ROMS)).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val platform = row.optString("platform")
            val uri = row.optString("uri")
            if (platform !in ArtCatalogs.thumbRepos.keys || !uri.startsWith("content://")) return@mapNotNull null
            Disc(uri, platform, row.optString("title"), row.optString("filename"),
                row.optLong("size", -1), row.optLong("modified", -1))
        }
    }

    private fun state(context: Context): JSONObject = try {
        File(context.filesDir, STATE).takeIf { it.isFile && it.length() < 4 * 1024 * 1024 }
            ?.readText(Charsets.UTF_8)?.let(::JSONObject) ?: JSONObject()
    } catch (_: Exception) { JSONObject() }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (error: Exception) {
            atomic.failWrite(out)
            throw error
        }
    }

    /** The disc was looked at and had no cover close enough (until it changes). */
    private fun known(state: JSONObject, disc: Disc): Boolean {
        val old = state.optJSONObject(disc.uri) ?: return false
        return old.optLong("size") == disc.size && old.optLong("modified") == disc.modified
    }

    private fun coverFile(context: Context, uri: String): File =
        File(File(context.filesDir, COVERS), CartLabelPaths.file(context.filesDir, uri).name)

    /** Which box art: by product code first, then the closest name. */
    internal fun choose(context: Context, disc: Disc, boxes: List<ArtGuess.Known>,
                        shouldContinue: () -> Boolean): String? {
        val name = disc.filename.ifBlank { disc.title }
        val titles = ArrayList<String>()
        if (disc.platform == Systems.PSX) {
            val ext = name.substringAfterLast('.', "").lowercase()
            val serial = DiscId.serial(context, disc.uri, ext)
            if (serial != null) {
                val official = ArtCatalogs.discNames(context, disc.platform, serial, shouldContinue)
                val have = boxes.map { it.name }.toHashSet()
                val exact = official.map(ArtGuess::thumbnailName).filter { it in have }
                if (exact.isNotEmpty()) {
                    // Several discs of one game: the file's own disc number.
                    val number = ArtGuess.discOf(name)
                    val pick = exact.firstOrNull { number != null && ArtGuess.discOf(it) == number } ?: exact.first()
                    Log.i(TAG, "BY_CODE $serial ${name.take(70)} -> $pick")
                    return pick
                }
                titles += official
            }
        }
        val best = ArtGuess.rank(ArtGuess.Clues(name, titles), boxes, 1).firstOrNull() ?: return null
        Log.i(TAG, "GUESS ${name.take(70)} -> ${best.known.name} %.2f".format(best.score))
        return best.known.name
    }

    private fun get(url: String, shouldContinue: () -> Boolean): Pair<Int, ByteArray?> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "slot-for-android")
        }
        try {
            val code = connection.responseCode
            if (code != 200) return code to null
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    if (!shouldContinue()) return -1 to null
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > MAX_IMAGE) return -1 to null
                    out.write(buf, 0, n)
                }
            }
            return 200 to out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun isImage(b: ByteArray) = b.size >= 8 &&
        ((b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) ||
            (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()))

    /** The cover as a PNG at most COVER_H tall; null on a 404. Throws on a
     *  network error (the disc is asked again later). */
    private fun cover(platform: String, name: String, shouldContinue: () -> Boolean): ByteArray? {
        var target = name
        repeat(3) {
            val url = ArtCatalogs.boxArtUrl(platform, target) ?: return null
            val (code, body) = get(url, shouldContinue)
            if (code == 404) return null
            if (code != 200 || body == null) throw IllegalStateException("Cover HTTP $code")
            if (isImage(body)) return shrink(body)
            // A git symlink: raw.githubusercontent serves the linked path.
            val link = String(body, Charsets.UTF_8).trim()
            if (body.size > 400 || !link.endsWith(".png")) return null
            target = link.substringAfterLast('/').removeSuffix(".png")
        }
        return null
    }

    private fun shrink(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= COVER_H) sample *= 2
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return null
        val scaled = if (source.height > COVER_H) Bitmap.createScaledBitmap(source,
            (source.width * COVER_H / source.height).coerceAtLeast(1), COVER_H, true) else source
        return try {
            val out = ByteArrayOutputStream()
            if (scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
        } finally {
            if (scaled !== source) scaled.recycle()
            source.recycle()
        }
    }

    /**
     * Cart Studio opened on a disc: its system's box art names for Search
     * game... (ArtNames/<system>.tsv, "name<TAB>name": the first column is
     * what a pick sends back), and its downloaded cover as the Real Label.
     */
    fun studio(context: Context, uri: String): Pair<ByteArray?, ByteArray?> {
        val disc = discs(context).firstOrNull { it.uri == uri } ?: return null to null
        try {
            val names = File(File(context.filesDir, "ArtNames").apply { mkdirs() }, "${disc.platform}.tsv")
            if (!names.isFile || System.currentTimeMillis() - names.lastModified() > 24L * 60 * 60 * 1000) {
                ArtCatalogs.boxArts(context, disc.platform) { true }?.let { boxes ->
                    writeAtomic(names, boxes.joinToString("") { "${it.name}\t${it.name}\n" }
                        .toByteArray(Charsets.UTF_8))
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Box art names unavailable for Cart Studio", error)
        }
        return studioArt(context, uri)
    }

    /** Search game... picked a box art name: that cover, or null. */
    fun studioCover(platform: String, name: String): ByteArray? = try {
        cover(platform, name) { true }
    } catch (error: Exception) {
        Log.w(TAG, "Cover unavailable: $name", error); null
    }

    /** Cart Studio's Real Label for a disc: its downloaded cover. */
    fun studioArt(context: Context, uri: String): Pair<ByteArray?, ByteArray?> {
        val file = coverFile(context, uri)
        val cover = if (file.isFile && file.length() in 8L..MAX_IMAGE.toLong())
            runCatching { file.readBytes() }.getOrNull() else null
        return cover to null
    }

    /** Returns true when more discs are waiting (another pass is wanted). */
    fun fill(context: Context, shouldContinue: () -> Boolean, onSaved: (String) -> Unit): Boolean {
        if (!shouldContinue()) return false
        val all = discs(context)
        if (all.isEmpty()) return false
        val state = state(context)
        val missing = all.filter {
            !CartLabelPaths.file(context.filesDir, it.uri).exists() && !known(state, it) && retry.pending(it.uri)
        }
        if (missing.isEmpty()) return false
        val now = android.os.SystemClock.elapsedRealtime()
        var dirty = false
        var done = 0
        for (disc in missing.filter { retry.ready(it.uri, now) }) {
            if (!shouldContinue() || done >= MAX_PER_PASS) break
            done++
            var outcome = "NOT_FOUND"
            try {
                val boxes = ArtCatalogs.boxArts(context, disc.platform, shouldContinue)
                if (boxes == null) {
                    outcome = "NETWORK_ERROR"
                    continue
                }
                val name = choose(context, disc, boxes, shouldContinue) ?: continue
                val png = cover(disc.platform, name, shouldContinue) ?: continue
                if (!shouldContinue()) { outcome = "INTERRUPTED"; break }
                writeAtomic(coverFile(context, disc.uri), png)
                val target = CartLabelPaths.file(context.filesDir, disc.uri)
                // Never over a label Cart Studio saved meanwhile.
                if (target.exists()) { outcome = "EXISTS"; continue }
                writeAtomic(target, png)
                outcome = "SAVED"
                onSaved(disc.uri)
            } catch (error: Exception) {
                outcome = "NETWORK_ERROR"
                Log.w(TAG, "Disc label deferred for ${disc.title.take(80)}", error)
            } finally {
                when (outcome) {
                    "SAVED", "EXISTS" -> retry.finished(disc.uri)
                    "NOT_FOUND" -> {
                        retry.finished(disc.uri)
                        state.put(disc.uri, JSONObject().put("size", disc.size).put("modified", disc.modified))
                        dirty = true
                    }
                    "NETWORK_ERROR" -> retry.transient(disc.uri, android.os.SystemClock.elapsedRealtime())
                }
                Log.i(TAG, "DISC_ART_RESULT $outcome [${disc.platform}] ${disc.title.take(90)}")
            }
            Thread.sleep(100L)
        }
        if (dirty) try {
            writeAtomic(File(context.filesDir, STATE), state.toString().toByteArray(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "Could not remember disc label misses", error)
        }
        return missing.any { retry.pending(it.uri) } && shouldContinue()
    }
}
