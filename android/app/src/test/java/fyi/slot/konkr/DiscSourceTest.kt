package fyi.slot.konkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscSourceTest {
    @Test fun aDocumentsFileIsOpenedAtItsStoragePath() {
        assertEquals("/storage/ABCD-1234/ROMs/psx/Raiden.chd",
            DiscSource.storagePath("/storage/ABCD-1234/ROMs/psx/Raiden.chd"))
        assertEquals("/storage/emulated/0/ROMs/psx/Raiden.chd",
            DiscSource.storagePath("/mnt/user/0/emulated/0/ROMs/psx/Raiden.chd"))
        assertEquals("/storage/ABCD-1234/ROMs/tg-cd/Ys.chd",
            DiscSource.storagePath("/mnt/user/0/ABCD-1234/ROMs/tg-cd/Ys.chd"))
        assertEquals("/storage/ABCD-1234/ROMs/psx/Raiden.chd",
            DiscSource.storagePath("/mnt/media_rw/ABCD-1234/ROMs/psx/Raiden.chd"))
        assertEquals("/storage/emulated/0/x.chd",
            DiscSource.storagePath("/mnt/pass_through/0/emulated/0/x.chd"))
        assertNull(DiscSource.storagePath("/data/user/0/fyi.slot.konkr/cache/x.chd"))
        assertNull(DiscSource.storagePath("/mnt/user/0"))
    }

    @Test fun aCueSheetsTracksPointWhereTheCoreCanOpenThem() {
        val sheet = "FILE \"Rondo (Track 01).bin\" BINARY\r\n  TRACK 01 MODE1/2352\r\n" +
            "    INDEX 01 00:00:00\r\nFILE Rondo_02.bin BINARY\r\n  TRACK 02 AUDIO\r\n" +
            "FILE \"sub\\Rondo (Track 03).wav\" WAVE\r\n"
        assertEquals(listOf("Rondo (Track 01).bin", "Rondo_02.bin", "sub/Rondo (Track 03).wav"),
            DiscSource.cueFiles(sheet))
        val out = DiscSource.rewriteCue(sheet) { "/storage/X/tg-cd/$it" }
        assertEquals("FILE \"/storage/X/tg-cd/Rondo (Track 01).bin\" BINARY", out.lines()[0])
        assertEquals("  TRACK 01 MODE1/2352", out.lines()[1])
        assertEquals("FILE \"/storage/X/tg-cd/Rondo_02.bin\" BINARY", out.lines()[3])
        assertEquals("FILE \"/storage/X/tg-cd/sub/Rondo (Track 03).wav\" WAVE", out.lines()[5])
    }

    @Test(expected = IllegalArgumentException::class)
    fun aCueTrackOutsideItsFolderIsRefused() {
        DiscSource.rewriteCue("FILE \"../secret.bin\" BINARY") { it }
    }
}
