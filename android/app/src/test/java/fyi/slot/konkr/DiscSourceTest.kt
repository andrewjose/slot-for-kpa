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
}
