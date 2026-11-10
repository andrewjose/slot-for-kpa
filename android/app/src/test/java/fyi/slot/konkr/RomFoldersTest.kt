package fyi.slot.konkr

import fyi.slot.konkr.RomFolders.Root
import fyi.slot.konkr.RomFolders.System
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomFoldersTest {
    private val all = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    private val card = "content://com.android.externalstorage.documents/tree/1234-5678%3AGBA"

    @Test fun theAllSystemsFolderAloneKeepsItsOriginalSettingAndCache() {
        assertEquals("rom_root_uri", RomFolders.ALL)
        val roots = RomFolders.roots(all, emptyMap())
        assertEquals(listOf(Root(all, null)), roots)
        assertEquals(all, RomFolders.layoutKey(roots))
        assertNull(RomFolders.layoutKey(RomFolders.roots(null, emptyMap())))
    }

    @Test fun aSystemFolderIsScannedBeforeAllSystemsAndChangesTheKey() {
        val roots = RomFolders.roots(all, mapOf(System.GBA to card))
        assertEquals(listOf(Root(card, System.GBA), Root(all, null)), roots)
        assertEquals("folders:GBA=$card|ALL=$all", RomFolders.layoutKey(roots))
        // A system folder alone is a library too.
        assertEquals(listOf(Root(card, System.GBA)), RomFolders.roots(null, mapOf(System.GBA to card)))
        assertEquals("rom_root_uri_GBC", RomFolders.key(System.GBC))
    }

    @Test fun eachSystemHasItsOwnPickerRequest() {
        for (system in System.entries) {
            assertEquals(system, RomFolders.request(RomFolders.requestCode(system)))
        }
        assertNull(RomFolders.request(RomFolders.requestCode(System.GBA) - 1))
        assertNull(RomFolders.request(RomFolders.requestCode(System.entries.last()) + 1))
        assertEquals(System.MD, System.of(Systems.MD))
        assertEquals(System.PSX, System.of(Systems.PSX))
    }

    @Test fun aSystemsOwnFolderReplacesAllSystemsForItsGames() {
        val own = setOf(System.GBA)
        val allRoot = Root(all, null)
        val gbaRoot = Root(card, System.GBA)
        assertNull("GBA comes from its own folder", RomFolders.shelve(allRoot, "GBA", false, own))
        assertEquals("SNES", RomFolders.shelve(allRoot, "SNES", false, own))
        assertEquals("GBA", RomFolders.shelve(gbaRoot, "GBA", false, own))
        assertNull("only GBA from the GBA folder", RomFolders.shelve(gbaRoot, "SNES", false, own))
        // A system slot. doesn't list here still passes through All Systems.
        assertEquals("PSX", RomFolders.shelve(allRoot, "PSX", false, own))
    }

    @Test fun gameBoyFoldersKeepTheCartsTheirSystemPlays() {
        val gbc = Root(all, System.GBC)
        val gb = Root(all, System.GB)
        val own = setOf(System.GBC, System.GB)
        assertEquals("a GB cart plays in colour", "GBC", RomFolders.shelve(gbc, "GB", false, own))
        assertEquals("GBC", RomFolders.shelve(gbc, "GBC", true, own))
        assertEquals("GB", RomFolders.shelve(gb, "GB", false, own))
        assertEquals("dual-mode .gbc", "GB", RomFolders.shelve(gb, "GBC", false, own))
        assertNull("GBC-only carts aren't the GB's", RomFolders.shelve(gb, "GBC", true, own))
    }

    @Test fun foldersMergeIntoOneLibraryWithEachGameOnce() {
        fun game(uri: String, platform: String) = JSONObject().put("uri", uri).put("platform", platform)
        val first = JSONArray().put(game("content://a", "GBA")).put(game("content://b", "GBA")).toString()
        val second = JSONArray().put(game("content://b", "GBA")).put(game("content://c", "NES")).toString()
        val merged = JSONArray(RomFolders.merge(listOf(first, second)))
        assertEquals(3, merged.length())
        assertEquals(listOf("content://a", "content://b", "content://c"),
            (0 until merged.length()).map { merged.getJSONObject(it).getString("uri") })
        // The same file through the GBC folder and through All Systems.
        val docs = "content://com.android.externalstorage.documents"
        val viaGbc = "$docs/tree/primary%3AROMs%2Fgbc/document/primary%3AROMs%2Fgbc%2FZelda.gb"
        val viaAll = "$docs/tree/primary%3AROMs/document/primary%3AROMs%2Fgbc%2FZelda.gb"
        val once = JSONArray(RomFolders.merge(listOf(
            JSONArray().put(game(viaGbc, "GBC")).toString(),
            JSONArray().put(game(viaAll, "GB")).toString())))
        assertEquals(1, once.length())
        assertEquals("GBC", once.getJSONObject(0).getString("platform"))
    }

    @Test fun folderNamesAreShort() {
        assertEquals("ROMs/psx", RomFolders.shortName("primary:Emulation/ROMs/psx"))
        assertEquals("SD card", RomFolders.shortName("1234-5678:"))
        assertEquals("Internal storage", RomFolders.shortName("primary:"))
    }
}
