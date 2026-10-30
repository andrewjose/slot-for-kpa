package fyi.slot.konkr

/**
 * Save and save state folders: one for all systems, and optionally a
 * Game Boy Advance, Game Boy Color or Game Boy folder that takes its place
 * for that system's games. Inside any chosen folder the layout stays
 * RetroArch's "sort by core" one: <folder>/mGBA/, /gpSP/ or /Gambatte/.
 */
internal object SaveFolders {
    const val SAVE_ROOT = "saves_root_uri"
    const val STATE_ROOT = "states_root_uri"
    const val SAVE_REQUEST = 4703
    const val STATE_REQUEST = 4704
    /** Six codes from here: GBA, GBC, GB saves, then GBA, GBC, GB states. */
    private const val SYSTEM_REQUEST = 4720

    enum class Console(val ext: String, val label: String, val short: String) {
        GBA("gba", "Game Boy Advance", "GBA"),
        GBC("gbc", "Game Boy Color", "GBC"),
        GB("gb", "Game Boy", "GB");

        companion object {
            fun forExtension(ext: String): Console? =
                entries.firstOrNull { it.ext.equals(ext, ignoreCase = true) }
        }
    }

    /** A folder picker request: saves or states, for one system or all. */
    data class Request(val states: Boolean, val console: Console?)

    fun base(states: Boolean): String = if (states) STATE_ROOT else SAVE_ROOT

    /** The preference holding a folder: all systems, or one system's own. */
    fun key(base: String, console: Console?): String =
        if (console == null) base else "${base}_${console.ext}"

    fun requestCode(states: Boolean, console: Console?): Int = when (console) {
        null -> if (states) STATE_REQUEST else SAVE_REQUEST
        else -> SYSTEM_REQUEST + (if (states) 3 else 0) + console.ordinal
    }

    fun request(code: Int): Request? = when (code) {
        SAVE_REQUEST -> Request(false, null)
        STATE_REQUEST -> Request(true, null)
        in SYSTEM_REQUEST until SYSTEM_REQUEST + 6 -> {
            val index = code - SYSTEM_REQUEST
            Request(index >= 3, Console.entries[index % 3])
        }
        else -> null
    }

    /** The folder for a game with this ROM extension: its system's own
     *  folder when one is chosen, otherwise the all-systems folder. */
    fun resolve(read: (String) -> String?, base: String, ext: String): String? {
        val own = Console.forExtension(ext)?.let { read(key(base, it)) }
        return own ?: read(base)
    }

    /** A short readable name for a folder from its SAF tree document id,
     *  such as "primary:Sync/GBA/saves": the last two path segments. */
    fun shortName(treeDocumentId: String): String {
        val volume = treeDocumentId.substringBefore(':', "")
        val path = treeDocumentId.substringAfter(':', treeDocumentId)
            .split('/').filter { it.isNotEmpty() }
        if (path.isEmpty()) {
            return if (volume == "primary" || volume.isEmpty()) "Internal storage" else "SD card"
        }
        return path.takeLast(2).joinToString("/")
    }
}
