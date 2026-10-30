package fyi.slot.konkr

import org.junit.Assert.assertEquals
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
        assertNull(SaveFolders.request(4726))
    }

    @Test fun folderNamesAreShortAndReadable() {
        assertEquals("Sync/GBA", SaveFolders.shortName("primary:Sync/GBA"))
        assertEquals("saves/GBC", SaveFolders.shortName("primary:RetroArch/saves/GBC"))
        assertEquals("Saves", SaveFolders.shortName("1234-ABCD:Saves/"))
        assertEquals("Internal storage", SaveFolders.shortName("primary:"))
        assertEquals("SD card", SaveFolders.shortName("1234-ABCD:"))
    }
}
