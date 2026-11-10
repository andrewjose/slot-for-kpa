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
        MD(Systems.MD, "Mega Drive", "MD"),
        PSX(Systems.PSX, "PlayStation", "PSX"),
        TGCD(Systems.TGCD, "TurboGrafx-CD", "TGCD");

        /** Preference name suffix: gba, gbc and gb as in the first builds. */
        val suffix: String get() = platform.lowercase(Locale.ROOT)

        /** The RetroArch core folders of the cores that run this system. */
        val cores: Set<String> get() = Systems.coreFoldersFor(platform)

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
    fun resolve(read: (String) -> String?, base: String, ext: String, core: String? = null): String? {
        // A core of one system names it (a CHD is a PlayStation's or a
        // TurboGrafx-CD's); a shared core (mGBA) leaves it to the extension.
        val console = core?.let { folder -> Console.entries.filter { folder in it.cores }.singleOrNull() }
            ?: Console.forExtension(ext)
        val own = console?.let { read(key(base, it)) }
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

    /** A cached library game: its file name, the cart's extension inside a
     *  ZIP (home consoles) and its shelf. */
    data class Game(val filename: String, val romExt: String, val platform: String)

    /** Each library game's save name and system, as a launch files it: by
     *  the cart's extension, else (a GB family ZIP) by its shelf. */
    fun libraryConsoles(games: List<Game>): Map<String, Set<Console>> {
        val out = HashMap<String, MutableSet<Console>>()
        for (game in games) {
            val ext = game.romExt.ifEmpty { game.filename.substringAfterLast('.', "") }
            // A disc is filed by its core, which its shelf names (a CHD may
            // be either disc system's).
            val byShelf = Console.entries.firstOrNull { it.platform == game.platform }
            val console = (if (Systems.isDisc(ext)) byShelf else null)
                ?: Console.forExtension(ext) ?: byShelf ?: continue
            val stem = game.filename.substringBeforeLast('.', game.filename).trim().take(200)
            if (stem.isNotEmpty()) out.getOrPut(stem) { HashSet() }.add(console)
        }
        return out
    }

    /** A private save or state file's name without its kind, as RetroArch
     *  names them: Game.srm, Game.rtc, Game.state1, Game.state.auto.png. */
    fun stemOf(name: String, states: Boolean): String =
        if (states) name.substringBefore(".state") else name.substringBeforeLast('.')

    /** The systems a private file can belong to: its core's, when only one
     *  system runs that core, else those of the library games of its name
     *  (mGBA and Gambatte run several). Empty when unknown. */
    fun consolesOf(core: String, stem: String, library: Map<String, Set<Console>>): Set<Console> {
        val byCore = Console.entries.filter { core in it.cores }.toSet()
        if (byCore.size == 1) return byCore
        return library[stem].orEmpty() intersect byCore
    }

    /** Whether a newly chosen folder gets a file: a system's own folder only
     *  that system's; the All Systems folder everything but the files of
     *  systems with a folder of their own. Unknown files stay with All Systems. */
    fun transfers(folder: Console?, own: Set<Console>, consoles: Set<Console>): Boolean =
        if (folder != null) folder in consoles
        else consoles.isEmpty() || consoles.any { it !in own }
}
