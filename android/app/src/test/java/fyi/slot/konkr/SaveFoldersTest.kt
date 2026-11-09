package fyi.slot.konkr

import fyi.slot.konkr.SaveFolders.Console
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFoldersTest {
    private val prefs = HashMap<String, String>()
    private fun folder(base: String, ext: String) =
        SaveFolders.resolve({ prefs[it] }, base, ext)

    @Test fun aSystemsOwnFolderWinsOverTheAllSystemsFolder() {
        assertNull(folder(SaveFolders.SAVE_ROOT, "gba"))
        prefs[SaveFolders.SAVE_ROOT] = "content://all"
        for (ext in listOf("gba", "gbc", "gb", "GBA")) {
            assertEquals("content://all", folder(SaveFolders.SAVE_ROOT, ext))
        }
        prefs["saves_root_uri_gbc"] = "content://gbc"
        assertEquals("content://gbc", folder(SaveFolders.SAVE_ROOT, "gbc"))
        assertEquals("content://all", folder(SaveFolders.SAVE_ROOT, "gb"))
        assertEquals("content://all", folder(SaveFolders.SAVE_ROOT, "gba"))
        // States are chosen separately from saves.
        assertNull(folder(SaveFolders.STATE_ROOT, "gbc"))
        prefs["states_root_uri_gb"] = "content://gb-states"
        assertEquals("content://gb-states", folder(SaveFolders.STATE_ROOT, "gb"))
    }

    @Test fun aSystemFolderWorksWithoutAnAllSystemsFolder() {
        prefs["saves_root_uri_gba"] = "content://gba"
        assertEquals("content://gba", folder(SaveFolders.SAVE_ROOT, "gba"))
        assertNull(folder(SaveFolders.SAVE_ROOT, "gb"))
    }

    @Test fun existingFolderChoicesKeepTheirPreferenceNames() {
        assertEquals("saves_root_uri", SaveFolders.key(SaveFolders.SAVE_ROOT, null))
        assertEquals("states_root_uri", SaveFolders.key(SaveFolders.STATE_ROOT, null))
        assertEquals("saves_root_uri_gb",
            SaveFolders.key(SaveFolders.SAVE_ROOT, SaveFolders.Console.GB))
        assertEquals(4703, SaveFolders.SAVE_REQUEST)
        assertEquals(4704, SaveFolders.STATE_REQUEST)
    }

    @Test fun everyPickerRequestRoundTrips() {
        val seen = HashSet<Int>()
        for (states in listOf(false, true)) {
            for (console in listOf<SaveFolders.Console?>(null) + SaveFolders.Console.entries) {
                val code = SaveFolders.requestCode(states, console)
                assertEquals(SaveFolders.Request(states, console), SaveFolders.request(code))
                assertTrue("duplicate request code $code", seen.add(code))
            }
        }
        assertNull(SaveFolders.request(4701))
        assertNull(SaveFolders.request(4719))
        assertNull(SaveFolders.request(4732))
    }

    @Test fun homeConsolesHaveTheirOwnFoldersForEveryExtension() {
        prefs[SaveFolders.SAVE_ROOT] = "content://all"
        prefs["saves_root_uri_nes"] = "content://nes"
        prefs["saves_root_uri_snes"] = "content://snes"
        prefs["saves_root_uri_md"] = "content://md"
        for (ext in listOf("nes", "fc", "unf", "unif")) {
            assertEquals("content://nes", folder(SaveFolders.SAVE_ROOT, ext))
        }
        for (ext in listOf("sfc", "smc")) {
            assertEquals("content://snes", folder(SaveFolders.SAVE_ROOT, ext))
        }
        for (ext in listOf("md", "gen", "smd", "bin")) {
            assertEquals("content://md", folder(SaveFolders.SAVE_ROOT, ext))
        }
        // The handhelds still use the all-systems folder.
        assertEquals("content://all", folder(SaveFolders.SAVE_ROOT, "gba"))
        assertEquals(SaveFolders.Console.SNES, SaveFolders.Console.forExtension("SMC"))
        assertNull(SaveFolders.Console.forExtension("zip"))
    }

    @Test fun folderNamesAreShortAndReadable() {
        assertEquals("Sync/GBA", SaveFolders.shortName("primary:Sync/GBA"))
        assertEquals("saves/GBC", SaveFolders.shortName("primary:RetroArch/saves/GBC"))
        assertEquals("Saves", SaveFolders.shortName("1234-ABCD:Saves/"))
        assertEquals("Internal storage", SaveFolders.shortName("primary:"))
        assertEquals("SD card", SaveFolders.shortName("1234-ABCD:"))
    }

    @Test fun eachSystemKnowsItsCoreFolders() {
        assertEquals(setOf("mGBA", "gpSP"), SaveFolders.Console.GBA.cores)
        assertEquals(setOf("mGBA", "Gambatte"), SaveFolders.Console.GBC.cores)
        assertEquals(setOf("mGBA", "Gambatte"), SaveFolders.Console.GB.cores)
        assertEquals(setOf("FCEUmm", "Nestopia"), SaveFolders.Console.NES.cores)
        assertEquals(setOf("Snes9x", "bsnes"), SaveFolders.Console.SNES.cores)
        assertEquals(setOf("Genesis Plus GX", "PicoDrive"), SaveFolders.Console.MD.cores)
    }

    private val library = SaveFolders.libraryConsoles(listOf(
        SaveFolders.Game("Pokemon Emerald.gba", "", "GBA"),
        SaveFolders.Game("Link's Awakening DX.gbc", "", "GBC"),
        SaveFolders.Game("Tetris.gb", "", "GB"),
        SaveFolders.Game("Tetris.gbc", "", "GBC"),
        SaveFolders.Game("Wario Land.zip", "", "GB"),
        SaveFolders.Game("Chrono Trigger.zip", "sfc", "SNES"),
    ))

    @Test fun aSaveBelongsToItsCoresSystemOrItsLibraryGame() {
        fun of(core: String, name: String, states: Boolean = false) =
            SaveFolders.consolesOf(core, SaveFolders.stemOf(name, states), library)
        // One system per core: the core decides, library or not.
        assertEquals(setOf(Console.GBA), of("gpSP", "Anything.srm"))
        assertEquals(setOf(Console.SNES), of("Snes9x", "Chrono Trigger.srm"))
        assertEquals(setOf(Console.MD), of("Genesis Plus GX", "Phantasy Star IV.srm"))
        // mGBA and Gambatte: the library game of that name.
        assertEquals(setOf(Console.GBA), of("mGBA", "Pokemon Emerald.srm"))
        assertEquals(setOf(Console.GBC), of("mGBA", "Link's Awakening DX.rtc"))
        assertEquals(setOf(Console.GBC), of("Gambatte", "Link's Awakening DX.state1.png", true))
        assertEquals(setOf(Console.GB), of("Gambatte", "Wario Land.state.auto", true))
        assertEquals(setOf(Console.GB, Console.GBC), of("mGBA", "Tetris.srm"))
        // A GBA game's name in Gambatte's folder can't be a GBA save.
        assertEquals(emptySet<SaveFolders.Console>(), of("Gambatte", "Pokemon Emerald.srm"))
        assertEquals(emptySet<SaveFolders.Console>(), of("mGBA", "Deleted Game.srm"))
    }

    @Test fun aNewFolderGetsOnlyItsOwnSystemsFiles() {
        val own = setOf(Console.GBA, Console.SNES)
        // A system's own folder: only that system's files.
        assertTrue(SaveFolders.transfers(Console.GBA, own, setOf(Console.GBA)))
        assertFalse(SaveFolders.transfers(Console.GBA, own, setOf(Console.GB)))
        assertFalse(SaveFolders.transfers(Console.GBA, own, emptySet()))
        assertTrue(SaveFolders.transfers(Console.GB, own, setOf(Console.GB, Console.GBC)))
        // All Systems: everything except systems with their own folder.
        assertFalse(SaveFolders.transfers(null, own, setOf(Console.GBA)))
        assertTrue(SaveFolders.transfers(null, own, setOf(Console.GB)))
        assertTrue(SaveFolders.transfers(null, own, emptySet()))
        assertTrue(SaveFolders.transfers(null, emptySet(), setOf(Console.SNES)))
    }
}
