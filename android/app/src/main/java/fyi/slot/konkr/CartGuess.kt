package fyi.slot.konkr

import android.content.Context
import android.util.Log

/**
 * A cart no checksum recognises (a hack, a translation, a renamed dump):
 * the games it most likely is, best first, as (system, official CRC), for the
 * label sets to be asked by that game's own CRC. ArtGuess does the scoring;
 * this reads the clues from the ROM's header.
 */
internal object CartGuess {
    private const val TAG = "SlotCartGuess"
    /** Guessed games asked per cart: the art sets don't hold every release. */
    const val TRIES = 6

    private fun ascii(head: ByteArray, from: Int, to: Int): String? {
        if (head.size < to) return null
        val text = (from until to).map { head[it].toInt() and 0xff }
            .takeWhile { it != 0 }
            .filter { it in 0x20..0x7e }
            .map(Int::toChar).joinToString("").trim()
        return text.takeIf { it.length >= 3 }
    }

    /** "POKEMON RED" (Game Boy 0x134), "POKEMON EMER" (GBA 0xA0). */
    fun headerTitle(platform: String, head: ByteArray?): String? = head?.let {
        when (platform) {
            Systems.GB, Systems.GBC -> ascii(it, 0x134, 0x143)
            Systems.GBA -> ascii(it, 0xA0, 0xAC)
            else -> null
        }
    }

    /** The GBA game code at 0xAC (BPEE): hacks almost never change it. */
    fun gameCode(platform: String, head: ByteArray?): String? {
        if (platform != Systems.GBA || head == null || head.size < 0xB0) return null
        val code = (0xAC..0xAF).map { head[it].toInt() and 0xff }
        if (!code.all { it in 48..57 || it in 65..90 }) return null
        return code.map(Int::toChar).joinToString("")
    }

    /**
     * The release the cart's own header names: a Game Boy's destination
     * byte (0x14A: 0 = Japan), a GBA game code's last letter (AFEJ = Japan).
     * A translation patch leaves both as they were.
     */
    fun headerRegion(platform: String, head: ByteArray?): String? = when (platform) {
        Systems.GB, Systems.GBC -> head?.takeIf { it.size > 0x14A && headerTitle(platform, it) != null }
            ?.let { if (it[0x14A].toInt() == 0) "JAPAN" else null }
        Systems.GBA -> when (gameCode(platform, head)?.last()) {
            'J' -> "JAPAN"
            'E' -> "USA"
            'P' -> "EUROPE"
            else -> null
        }
        else -> null
    }

    /**
     * The region whose box art a cart should wear: its file name's, else
     * Japan for a fan translation (they are of Japan-only games), else its
     * header's.
     */
    fun preferredRegion(platform: String, filename: String, head: ByteArray?): String? =
        ArtGuess.regionOf(filename)
            ?: if (ArtGuess.translated(filename)) "JAPAN" else headerRegion(platform, head)

    /**
     * A label found by the game's name for a cart its checksum doesn't match
     * shows the file name too when the cart is a translation: tagged as one,
     * or a Japanese cart renamed without its region (Andrew, 2026-11-27).
     */
    fun translationBand(platform: String, filename: String, head: ByteArray?): Boolean =
        ArtGuess.translated(filename) ||
            (ArtGuess.regionOf(filename) == null && headerRegion(platform, head) == "JAPAN")

    fun candidates(
        context: Context, platform: String, filename: String, head: ByteArray?,
        headerTitles: List<String> = emptyList(), region: String? = null,
        shouldContinue: () -> Boolean,
    ): List<Pair<String, String>>? {
        val known = ArtCatalogs.cartCatalog(context, platform, shouldContinue) ?: return null
        val clues = ArtGuess.Clues(
            filename = filename,
            titles = (listOfNotNull(headerTitle(platform, head)) + headerTitles).filter { it.isNotBlank() },
            serial = gameCode(platform, head),
            region = region?.uppercase()?.takeIf { it.isNotBlank() }
                ?: preferredRegion(platform, filename, head),
        )
        val picks = ArtGuess.rank(clues, known.filter { it.crc != null }, TRIES)
        if (picks.isNotEmpty()) {
            Log.i(TAG, "GUESS [$platform] ${filename.take(80)} -> " +
                picks.joinToString(" | ") { "${it.known.name} %.2f".format(it.score) })
        }
        return picks.map { (it.known.tag.ifBlank { platform }) to it.known.crc!! }
    }
}
