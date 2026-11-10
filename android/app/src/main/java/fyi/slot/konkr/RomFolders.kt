package fyi.slot.konkr

import org.json.JSONArray

/**
 * Library > Choose ROM Folder: one folder for every system (All Systems),
 * and optionally a folder of one system's own. A system's own folder
 * replaces the All Systems folder for that system's games: the All Systems
 * scan drops them, and the system's folder gives only them. The library is
 * every folder's games together.
 */
internal object RomFolders {
    /** The All Systems folder: slot.'s original ROM folder setting. */
    const val ALL = "rom_root_uri"
    private const val REQUEST_BASE = 4740

    enum class System(val platform: String, val label: String, val short: String) {
        GBA(Systems.GBA, "Game Boy Advance", "GBA"),
        GBC(Systems.GBC, "Game Boy Color", "GBC"),
        GB(Systems.GB, "Game Boy", "GB"),
        NES(Systems.NES, "NES", "NES"),
        SNES(Systems.SNES, "Super Nintendo", "SNES"),
        MD(Systems.MD, "Mega Drive", "MD"),
        PSX(Systems.PSX, "PlayStation", "PSX"),
        TGCD(Systems.TGCD, "TurboGrafx-CD", "TGCD");

        companion object {
            fun of(platform: String): System? = entries.firstOrNull { it.platform == platform }
        }
    }

    /** The setting holding a system's own folder. */
    fun key(system: System) = "${ALL}_${system.platform}"

    /** The folder picker's request code for a system's own folder (the
     *  All Systems folder keeps slot.'s original one). */
    fun requestCode(system: System) = REQUEST_BASE + system.ordinal

    /** The system a picker result is for, or null when it isn't one. */
    fun request(code: Int): System? = System.entries.getOrNull(code - REQUEST_BASE)

    /** One folder to scan: All Systems (`system` null), or a system's own. */
    data class Root(val uri: String, val system: System?)

    /** Every folder to scan: each system's own, then All Systems. */
    fun roots(all: String?, own: Map<System, String>): List<Root> =
        System.entries.mapNotNull { system -> own[system]?.let { Root(it, system) } } +
            listOfNotNull(all?.let { Root(it, null) })

    /** What identifies a library and its cache. The All Systems folder
     *  alone keeps slot.'s original key, so existing caches stay valid. */
    fun layoutKey(roots: List<Root>): String? = when {
        roots.isEmpty() -> null
        roots.size == 1 && roots[0].system == null -> roots[0].uri
        else -> "folders:" + roots.joinToString("|") { "${it.system?.platform ?: "ALL"}=${it.uri}" }
    }

    /**
     * The system a game found in `root` is shelved as, or null when it
     * belongs to another folder. In a Game Boy system's own folder, a
     * cartridge that system plays is that system's: every GB/GBC cart in
     * the GBC folder plays in colour; the GB folder keeps the carts a
     * Game Boy runs (not GBC-only ones).
     */
    fun shelve(root: Root, platform: String, colorOnly: Boolean, own: Set<System>): String? {
        val system = root.system
        if (system == null) return platform.takeIf { p -> System.of(p)?.let { it !in own } ?: true }
        val shelved = when {
            system == System.GBC && platform == Systems.GB -> Systems.GBC
            system == System.GB && platform == Systems.GBC && !colorOnly -> Systems.GB
            else -> platform
        }
        return shelved.takeIf { it == system.platform }
    }

    /** Every folder's games as one library, each game once. A file inside
     *  two chosen folders (a system's folder within the All Systems one)
     *  has a different URI in each tree: the first folder's entry stays,
     *  and roots() lists the system folders first. */
    fun merge(parts: List<String>): String {
        val out = JSONArray()
        val seen = HashSet<String>()
        for (part in parts) {
            val games = JSONArray(part)
            for (i in 0 until games.length()) {
                val game = games.optJSONObject(i) ?: continue
                if (seen.add(document(game.optString("uri")))) out.put(game)
            }
        }
        return out.toString()
    }

    /** A SAF document URI without the tree it was found through. */
    internal fun document(uri: String): String {
        val authority = uri.substringAfter("content://").substringBefore('/')
        val id = uri.substringAfter("/document/", uri)
        return "$authority/$id"
    }

    /** A chosen folder's short name: its last two path parts. */
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
