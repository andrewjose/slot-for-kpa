package fyi.slot.konkr

import org.json.JSONObject
import java.io.File

/**
 * Library > Redo All Labels (Andrew, 2026-11-27): forget every automatic
 * label so the label passes fetch them all again with today's rules, and,
 * if asked, the labels he picked himself too.
 *
 * A label he picked (Cart Studio, or Import Label) has a `<key>.picked`
 * marker beside it since fork.36; picks from before that can't be told from
 * automatic ones. The real-label and disc-cover caches and the passes'
 * "already tried" records go either way, so nothing is skipped as done.
 */
internal object CartLabelReset {
    const val PICKED = "picked"
    private const val LIBRARY = "rom-library-cache-v1.json"
    private val TRIED = listOf("home-labels-v1.json", "disc-labels-v1.json")
    private val CACHES = listOf(HomeCartLabels.REAL_DIR, "disc-covers")

    /** The marker that keeps a label through a redo. */
    fun pickedMarker(label: File): File =
        File(label.parentFile, label.nameWithoutExtension + ".$PICKED")

    /** Labels he picked himself (with a marker). */
    fun pickedCount(filesDir: File): Int =
        File(filesDir, "Labels").listFiles { f -> f.name.endsWith(".$PICKED") }?.size ?: 0

    data class Result(val removed: Int, val kept: Int, val uris: List<String>)

    /**
     * Removes the labels (all, or all but his picks) with their title-bar
     * markers, and the caches and records behind them. `uris`: the library's
     * carts whose label went, for the shelf to redraw.
     */
    fun reset(filesDir: File, keepPicks: Boolean): Result {
        val labels = File(filesDir, "Labels")
        val picked = labels.listFiles { f -> f.name.endsWith(".$PICKED") }
            ?.map { it.nameWithoutExtension }?.toSet().orEmpty()
        val gone = HashSet<String>()
        var kept = 0
        labels.listFiles()?.forEach { file ->
            val key = file.nameWithoutExtension
            if (keepPicks && key in picked) {
                if (file.extension == "png") kept++
                return@forEach
            }
            if (file.extension == "png") gone.add(key)
            file.delete()
        }
        CACHES.forEach { File(filesDir, it).deleteRecursively() }
        TRIED.forEach { File(filesDir, it).delete() }
        // The cart faces drawn from the old labels.
        File(filesDir, "Cache").listFiles { f -> f.name.startsWith("cartridge-faces") }
            ?.forEach { it.deleteRecursively() }
        return Result(gone.size, kept, libraryUris(filesDir).filter {
            CartLabelPaths.file(filesDir, it).nameWithoutExtension in gone
        })
    }

    private fun libraryUris(filesDir: File): List<String> = try {
        val rows = JSONObject(File(filesDir, LIBRARY).readText(Charsets.UTF_8)).optJSONArray("games")
        (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optJSONObject(it)?.optString("uri") }
            .filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }
}
