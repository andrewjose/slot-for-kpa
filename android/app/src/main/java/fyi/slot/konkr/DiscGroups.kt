package fyi.slot.konkr

import java.io.File
import java.util.Locale

/**
 * Multi-disc games, one cart each.
 *
 * The library scan (RomLibrary) files an .m3u playlist as one cart holding
 * the discs it lists (often in a hidden folder, as ES-DE and RetroArch users
 * keep them), and groups the "(Disc N)" images that sit side by side without
 * one: "Fear Effect (USA) (Disc 1..4).chd" become "Fear Effect (USA)". The
 * cart's index entry carries its discs' URIs ("discs") and names
 * ("disc_names"); the launch links them all and hands the core a playlist,
 * so its disc controls (Settings > Change Disc) can swap them.
 *
 * The game's memory card is named after the game ("Fear Effect (USA).srm"),
 * as RetroArch names an .m3u's, so every disc sees the same saves.
 */
internal object DiscGroups {
    private val discTag = Regex("""\s*[(\[]\s*(?:Disc|Disk|CD)\s*(\d+)(?:\s*of\s*\d+)?\s*[)\]]""",
        RegexOption.IGNORE_CASE)

    /** 2 for "Fear Effect (USA) (Disc 2).chd", null without a disc tag. */
    fun discNumber(name: String): Int? = discTag.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** "Fear Effect (USA) (Disc 2).chd" -> "Fear Effect (USA)". */
    fun gameName(name: String): String {
        val stem = name.substringBeforeLast('.', name)
        return discTag.replace(stem, " ").replace(Regex("\\s{2,}"), " ").trim()
    }

    /** The disc paths an .m3u lists, in order: blank and # lines skipped. */
    fun parseM3u(text: String): List<String> = text.lineSequence()
        .map { it.trim().trimStart('﻿') }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .take(16)
        .toList()

    /** Path segments of an .m3u line, relative to the playlist's folder. */
    fun segments(line: String): List<String>? {
        val parts = line.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || line.startsWith("/")) return null
        return parts
    }

    /** A grouped cart's discs: its title and (uri, name) of each disc. */
    data class DiscSet(val title: String, val discs: List<Pair<String, String>>)

    /** The discs of the cart at `uri`, from the library index; null for a
     *  single disc. */
    fun discsOf(filesDir: File, uri: String): DiscSet? {
        val cache = File(filesDir, "rom-library-cache-v1.json")
        if (!cache.isFile || cache.length() > RomLibrary.CACHE_MAX_BYTES) return null
        val games = try {
            org.json.JSONObject(cache.readText(Charsets.UTF_8)).optJSONArray("games")
        } catch (_: Exception) { null } ?: return null
        for (i in 0 until games.length()) {
            val game = games.optJSONObject(i) ?: continue
            if (game.optString("uri") != uri) continue
            val uris = game.optJSONArray("discs") ?: return null
            val names = game.optJSONArray("disc_names")
            val discs = (0 until uris.length()).mapNotNull { k ->
                val disc = uris.optString(k)
                if (!disc.startsWith("content://")) null
                else disc to (names?.optString(k)?.takeIf { it.isNotBlank() } ?: "disc${k + 1}.chd")
            }
            return if (discs.isEmpty()) null else DiscSet(game.optString("title"), discs)
        }
        return null
    }

    /**
     * The first time a grouped game starts: its memory card is the newest
     * of the per-disc cards a single disc used to have, so a game already
     * saved on disc 1 keeps its save.
     */
    fun adoptDiscSave(save: File, discNames: List<String>) {
        if (save.exists()) return
        val dir = save.parentFile ?: return
        val old = discNames.map { File(dir, it.substringBeforeLast('.', it) + ".srm") }
            .filter { it.isFile && it.length() > 0 }
            .maxByOrNull { it.lastModified() } ?: return
        try {
            old.copyTo(save, overwrite = false)
        } catch (error: Exception) {
            android.util.Log.w("SlotDiscGroups", "Couldn't carry over ${old.name}", error)
        }
    }

    internal fun lower(s: String) = s.lowercase(Locale.ROOT)
}
