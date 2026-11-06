package fyi.slot.konkr

import java.util.Locale

/**
 * Save and save state folders: one for all systems, and optionally a
 * folder of each system's own that takes its place for that system's
 * games. Inside any chosen folder the layout stays RetroArch's "sort by
 * core" one: <folder>/mGBA/, /Gambatte/, /Snes9x/ and so on.
 */
internal object SaveFolders {
    const val SAVE_ROOT = "saves_root_uri"
    const val STATE_ROOT = "states_root_uri"
    const val SAVE_REQUEST = 4703
    const val STATE_REQUEST = 4704
    /** One code per system from here for saves, then one each for states. */
    private const val SYSTEM_REQUEST = 4720

    enum class Console(val platform: String, val label: String, val short: String) {
        GBA(Systems.GBA, "Game Boy Advance", "GBA"),
        GBC(Systems.GBC, "Game Boy Color", "GBC"),
        GB(Systems.GB, "Game Boy", "GB"),
        NES(Systems.NES, "NES", "NES"),
        SNES(Systems.SNES, "Super Nintendo", "SNES"),
        MD(Systems.MD, "Mega Drive", "MD");

        /** Preference name suffix: gba, gbc and gb as in the first builds. */
        val suffix: String get() = platform.lowercase(Locale.ROOT)

        companion object {
            /** The system of a ROM extension, as Systems files it. */
            fun forExtension(ext: String): Console? =
                Systems.platformFor(ext)?.let { platform ->
                    entries.firstOrNull { it.platform == platform }
                }
        }
    }

    private val CONSOLES = Console.entries.size

    /** A folder picker request: saves or states, for one system or all. */
    data class Request(val states: Boolean, val console: Console?)

    fun base(states: Boolean): String = if (states) STATE_ROOT else SAVE_ROOT

    /** The preference holding a folder: all systems, or one system's own. */
    fun key(base: String, console: Console?): String =
        if (console == null) base else "${base}_${console.suffix}"

    fun requestCode(states: Boolean, console: Console?): Int = when (console) {
        null -> if (states) STATE_REQUEST else SAVE_REQUEST
        else -> SYSTEM_REQUEST + (if (states) CONSOLES else 0) + console.ordinal
    }

    fun request(code: Int): Request? = when (code) {
        SAVE_REQUEST -> Request(false, null)
        STATE_REQUEST -> Request(true, null)
        in SYSTEM_REQUEST until SYSTEM_REQUEST + 2 * CONSOLES -> {
            val index = code - SYSTEM_REQUEST
            Request(index >= CONSOLES, Console.entries[index % CONSOLES])
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
