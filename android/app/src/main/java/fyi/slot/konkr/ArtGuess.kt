package fyi.slot.konkr

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Best-guess art for a game no checksum recognises: a ROM hack, a
 * translation, a renamed or trimmed dump, a disc. Each official name of the
 * system (a No-Intro / Redump DAT, or the box art a thumbnail set holds) is
 * scored against what the file says about itself: its filename, the title in
 * its own header (Game Boy, SNES, Mega Drive), a GBA game code or a disc's
 * product code. A hack keeps most of these, so it gets the original game's
 * art. Guesses are allowed (the player prefers every cart labelled and fixes
 * a wrong one in Cart Studio), but a name with nothing in common is never
 * chosen.
 *
 * Pure Kotlin, no Android: unit-tested in ArtGuessTest.
 */
internal object ArtGuess {
    /** An official name and what identifies it. */
    data class Known(val name: String, val crc: String? = null, val serial: String? = null,
                     /** Which catalog it came from (a GBC hack may be a GB game's). */
                     val tag: String = "") {
        internal val key: String = titleKey(name)
        internal val words: List<String> = key.split(' ').filter { it.isNotEmpty() }
        internal val region: String? = regionOf(name)
        internal val disc: Int? = discOf(name)
    }

    /** What a file says about itself. */
    data class Clues(
        val filename: String,
        /** Titles from the ROM's own header, or a disc's official name(s). */
        val titles: List<String> = emptyList(),
        /** GBA game code (BPEE), a disc's product code (SLUS-00067). */
        val serial: String? = null,
        /** USA, EUROPE, JAPAN... when the file or header says. */
        val region: String? = null,
    )

    data class Pick(val known: Known, val score: Double)

    /** Below this, nothing in common: no label rather than a random one. */
    const val FLOOR = 0.42

    private val mark = Regex("\\p{M}+")
    private val separator = Regex("[^a-z0-9]+")
    private val bracketed = Regex("""\([^)]*\)|\[[^]]*]|\{[^}]*}""")
    private val regionTag = Regex("""\((USA|Europe|Japan|World|Asia|Korea|Brazil|Australia|France|Germany|Spain|Italy)\b""",
        RegexOption.IGNORE_CASE)
    private val shortRegion = Regex("""\((U|E|J|UE|JU|JUE|W)\)""")
    private val extension = Regex("""\.[A-Za-z0-9]{1,4}$""")
    private val stop = setOf("the", "a", "an", "of", "and", "version", "edition")
    /** "v1.2.1", "v3": a hack's own version, not the game's. A bare "2.0"
     *  stays: it may be the game's ("Colin McRae Rally 2.0"). */
    private val version = Regex("""\b[vV]\d+(?:\.\d+)*[a-z]?\b""")
    private val languages = Regex("""\(((?:[A-Z][a-z](?:-[A-Za-z]+)?,)*[A-Z][a-z](?:-[A-Za-z]+)?)\)""")
    private val discTag = Regex("""[(\[]\s*Dis[ck]\s*(\d+)""", RegexOption.IGNORE_CASE)
    /** Words a hack adds to its name; they say nothing about the game. */
    private val hackWords = setOf("hack", "romhack", "translation", "translated", "english", "patched",
        "patch", "fix", "fixed", "en", "eng", "t", "remix", "redux", "deluxe", "improvement",
        "improved", "restoration", "restored", "uncensored", "qol", "final", "beta", "dx", "color",
        "colour", "redone", "remake", "remastered", "plus", "gbc", "enhanced", "definitive", "expanded")
    private val roman = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6",
        "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10")

    /** "Legend of Zelda, The - A Link to the Past (USA) [!].sfc" -> "legend zelda link to past". */
    fun titleKey(name: String): String {
        var base = extension.replace(name.trim(), "")
        base = bracketed.replace(base, " ")
        base = version.replace(base, " ")
        val flat = Normalizer.normalize(base.replace("&", " and "), Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT).replace(mark, "")
        return flat.replace(separator, " ").trim().split(' ')
            .filter { it.isNotEmpty() && it !in stop }
            .map { roman[it] ?: it }
            .joinToString(" ")
    }

    fun discOf(name: String): Int? = discTag.find(name)?.groupValues?.get(1)?.toIntOrNull()

    fun regionOf(name: String): String? {
        regionTag.find(name)?.let { return it.groupValues[1].uppercase(Locale.ROOT) }
        return when (shortRegion.find(name)?.groupValues?.get(1)) {
            "U", "UE", "JU", "JUE" -> "USA"
            "E" -> "EUROPE"
            "J" -> "JAPAN"
            "W" -> "WORLD"
            else -> null
        }
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.length > 120 || b.length > 120) return 0.0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1)
            }
            val t = previous; previous = current; current = t
        }
        return 1.0 - previous[b.length].toDouble() / max(a.length, b.length)
    }

    private fun isNumber(word: String) = word.all { it.isDigit() }

    /** How well one title (already a key) names `known`, 0..1. */
    internal fun titleScore(title: String, known: Known): Double {
        if (title.isEmpty() || known.key.isEmpty()) return 0.0
        if (title == known.key) return 1.0
        val words = title.split(' ').filter { it.isNotEmpty() }
        val kw = known.words
        val wanted = words.filter { it !in hackWords }
        val lead = if (wanted.isEmpty()) words else wanted
        // The official title starts the file's: "super mario world kaizo".
        val prefix = kw.size <= lead.size && lead.subList(0, kw.size) == kw
        // The file's title starts the official one: "super mario land 2"
        // for "super mario land 2 6 golden coins".
        val shorter = lead.size < kw.size && kw.subList(0, lead.size) == lead
        val common = kw.count { it in words }
        val cover = common.toDouble() / kw.size
        val mine = if (wanted.isEmpty()) 0.0 else wanted.count { it in kw }.toDouble() / wanted.size
        var score = maxOf(
            if (prefix) 0.8 + 0.15 * kw.size / lead.size else 0.0,
            if (shorter) 0.7 + 0.25 * lead.size / kw.size else 0.0,
            0.62 * cover + 0.28 * mine,
            0.85 * similarity(title, known.key),
        )
        // A sequel's number decides the game: "gundam battle assault 2" is
        // never "gundam battle assault" when the 2 has its own name.
        val numbers = words.filter(::isNumber).toSet()
        val theirs = kw.filter(::isNumber).toSet()
        // Its own number missing from the official name weighs most; an
        // official subtitle's number ("6 golden coins") a little.
        score -= 0.2 * (numbers - theirs).size + 0.06 * (theirs - numbers).size
        return score.coerceIn(0.0, 1.0)
    }

    private fun preference(known: Known, region: String?, disc: Int?): Double {
        val n = known.name
        var p = 0.0
        if (disc != null && known.disc != null) p += if (disc == known.disc) 0.02 else -0.03
        // An English release over another language's.
        languages.find(n)?.let { p += if (it.groupValues[1].split(',').contains("En")) 0.005 else -0.01 }
        if (region != null) {
            p += if (known.region == region) 0.04 else -0.02
        } else {
            p += when (known.region) { "USA" -> 0.03; "WORLD" -> 0.025; "EUROPE" -> 0.02; "JAPAN" -> 0.0; else -> 0.01 }
        }
        if (n.contains("(Rev", true)) p -= 0.01
        for (bad in listOf("(Beta", "(Proto", "(Demo", "(Sample", "(Virtual Console)", "(Switch Online)",
                "(Aftermarket)", "(Unl)", "(Pirate)", "Kiosk", "(Program)", "(Debug)", "(Hack)")) {
            if (n.contains(bad, true)) p -= 0.05
        }
        return p
    }

    private fun serialScore(serial: String?, known: Known): Double {
        val a = serial?.uppercase(Locale.ROOT)?.filter { it.isLetterOrDigit() } ?: return 0.0
        val b = known.serial?.uppercase(Locale.ROOT) ?: return 0.0
        val codes = b.split(',', ' ', '/').map { s -> s.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        return when {
            codes.any { it == a } -> 0.6
            // A GBA code's region letter differs: same game, other release.
            a.length == 4 && codes.any { it.length == 4 && it.take(3) == a.take(3) } -> 0.25
            else -> 0.0
        }
    }

    /** The best names for the file, best first, at most `limit`, none under FLOOR. */
    fun rank(clues: Clues, known: List<Known>, limit: Int = 5): List<Pick> {
        val titles = (listOf(titleKey(clues.filename)) + clues.titles.map(::titleKey))
            .filter { it.isNotBlank() }.distinct()
        val region = clues.region ?: regionOf(clues.filename)
        val disc = discOf(clues.filename)
        val scored = ArrayList<Pick>()
        for (k in known) {
            if (k.key.isEmpty()) continue
            var best = 0.0
            for ((i, t) in titles.withIndex()) {
                // The filename leads; a header title is a little less sure.
                val s = titleScore(t, k) * if (i == 0) 1.0 else 0.95
                best = max(best, s)
            }
            val serial = serialScore(clues.serial, k)
            if (serial > 0) best = min(1.0, max(best, 0.5) + serial)
            if (best < FLOOR) continue
            scored.add(Pick(k, best + preference(k, region, disc)))
        }
        scored.sortByDescending { it.score }
        return scored.distinctBy { it.known.name }.take(limit)
    }

    private val gameStart = Regex("""(?m)^game\s*\(\s*$""")
    private val nameLine = Regex("""(?m)^\s*name\s+"([^"\r\n]+)"""")
    private val crcLine = Regex("""\bcrc\s+([0-9a-fA-F]{8})\b""")
    private val serialLine = Regex("""(?m)^\s*serial\s+"([^"\r\n]+)"""")

    /** Every game of a clrmamepro DAT (libretro-database's No-Intro and
     *  Redump ones): its name, first ROM's CRC and serial. */
    fun parseDat(dat: String): List<Known> {
        if (!dat.startsWith("clrmamepro (")) return emptyList()
        val out = ArrayList<Known>()
        for (block in dat.split(gameStart).drop(1)) {
            val name = nameLine.find(block)?.groupValues?.get(1) ?: continue
            out.add(Known(name, crcLine.find(block)?.groupValues?.get(1)?.uppercase(Locale.ROOT),
                serialLine.find(block)?.groupValues?.get(1)))
        }
        return out
    }

    /** "Castlevania: SotN" -> "Castlevania_ SotN": libretro thumbnail file names. */
    fun thumbnailName(name: String): String = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")
}
