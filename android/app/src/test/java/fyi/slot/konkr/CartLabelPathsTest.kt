package fyi.slot.konkr

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CartLabelPathsTest {
    @Test fun matchesNativeUnsignedFnvForOpaqueUri() {
        val expected = File("/data/data/fyi.slot.konkr/files/Labels/dbb1dc49de8ac75d.png")
        assertEquals(expected,
            CartLabelPaths.file(File("/data/data/fyi.slot.konkr/files"),
                "content://roms/Pokemon.gba"))
    }

    @Test fun identicalFileNamesInDifferentFoldersDoNotShareArt() {
        val files = File("/tmp/app-files")
        assertNotEquals(
            CartLabelPaths.file(files, "content://storage/a/Pokemon.gba"),
            CartLabelPaths.file(files, "content://storage/b/Pokemon.gba"))
    }

    @Test fun officialArtworkMustBePlatformMatchedAndSafe() {
        assertTrue(AutoCartLabels.validArtworkPath("GBA/real/12ABCDEF.png", "GBA"))
        assertTrue(AutoCartLabels.validArtworkPath("GB/real/12345678.png", "GB"))
        assertFalse(AutoCartLabels.validArtworkPath("GBA/real/12ABCDEF.png", "GB"))
        assertFalse(AutoCartLabels.validArtworkPath("../GBA/real/12345678.png", "GBA"))
        assertFalse(AutoCartLabels.validArtworkPath("GBA/../evil.png", "GBA"))
        assertFalse(AutoCartLabels.validArtworkPath("https://example.com/evil.png", "GBA"))
        assertFalse(AutoCartLabels.validArtworkPath("GBA/real/photo.jpg", "GBA"))
    }

    /** Disc hacks and translations keep the file name band; regions, discs,
     *  revisions and re-release labels don't make another game. Real names
     *  from Andrew's collection (2026-11-24). */
    @Test fun onlyNonStandardTagsMakeAnotherGame() {
        assertEquals("Castlevania - Symphony of the Night (QoL v1.2).chd", true, CartLabelPaths.differs("Castlevania - Symphony of the Night (QoL v1.2).chd", "Castlevania - Symphony of the Night (USA)"))
        assertEquals("Castlevania - Symphony of the Night (USA).chd", false, CartLabelPaths.differs("Castlevania - Symphony of the Night (USA).chd", "Castlevania - Symphony of the Night (USA)"))
        assertEquals("Metal Gear Solid (USA) (Disc 1).chd", false, CartLabelPaths.differs("Metal Gear Solid (USA) (Disc 1).chd", "Metal Gear Solid (USA) (Disc 1)"))
        assertEquals("Metal Gear Solid (Disc 2).chd", false, CartLabelPaths.differs("Metal Gear Solid (Disc 2).chd", "Metal Gear Solid (USA) (Disc 2)"))
        assertEquals("Echo Night - The Lord of Nightmares (Japan) [T-En by Devil Hackers v1.0a] [n].chd", true, CartLabelPaths.differs("Echo Night - The Lord of Nightmares (Japan) [T-En by Devil Hackers v1.0a] [n].chd", "Echo Night - The Lord of Nightmares (Japan)"))
        assertEquals("Mega Man 8 (USA) [Undub by Acediez v1.4].chd", true, CartLabelPaths.differs("Mega Man 8 (USA) [Undub by Acediez v1.4].chd", "Mega Man 8 (USA)"))
        assertEquals("Breath of Fire III (Better Descriptions v1.1.1).chd", true, CartLabelPaths.differs("Breath of Fire III (Better Descriptions v1.1.1).chd", "Breath of Fire III (USA)"))
        assertEquals("Dr. Mario (USA).chd", false, CartLabelPaths.differs("Dr. Mario (USA).chd", "Dr. Mario (USA)"))
        assertEquals("Vagrant Story (USA).cue", false, CartLabelPaths.differs("Vagrant Story (USA).cue", "Vagrant Story (USA)"))
        assertEquals("Parasite Eve (USA) (Disc 1) (Rev 1).chd", false, CartLabelPaths.differs("Parasite Eve (USA) (Disc 1) (Rev 1).chd", "Parasite Eve (USA) (Disc 1)"))
        assertEquals("Ace Combat 3 - Electrosphere (Japan, Asia) (Rev 1) [T-En by Load Word Team v1.1].chd", true, CartLabelPaths.differs("Ace Combat 3 - Electrosphere (Japan, Asia) (Rev 1) [T-En by Load Word Team v1.1].chd", "Ace Combat 3 - Electrosphere (Japan, Asia) (Rev 1)"))
        assertEquals("Castlevania - Rondo of Blood (T-En + English PSP Audio v1.01).chd", true, CartLabelPaths.differs("Castlevania - Rondo of Blood (T-En + English PSP Audio v1.01).chd", "Akumajou Dracula X - Chi no Rondo (Japan)"))
        assertEquals("Tomba! (USA).chd", false, CartLabelPaths.differs("Tomba! (USA).chd", "Tomba! (USA)"))
        assertEquals("Crash Bandicoot.chd", false, CartLabelPaths.differs("Crash Bandicoot.chd", "Crash Bandicoot (USA)"))
        assertEquals("Spyro 2 - Ripto's Rage!.chd", false, CartLabelPaths.differs("Spyro 2 - Ripto's Rage!.chd", "Spyro 2 - Ripto's Rage! (USA)"))
        assertEquals("Baroque - Yuganda Mousou (Japan) (Genteiban) [T-En by Plissken v1.0.2].chd", true, CartLabelPaths.differs("Baroque - Yuganda Mousou (Japan) (Genteiban) [T-En by Plissken v1.0.2].chd", "Baroque - Yuganda Mousou (Japan) (Genteiban)"))
        assertEquals("Thrill Kill (USA) (Beta).chd", false, CartLabelPaths.differs("Thrill Kill (USA) (Beta).chd", "Thrill Kill (USA) (Beta)"))
        assertEquals("Snatcher CD-ROMantic (English).cue", true, CartLabelPaths.differs("Snatcher CD-ROMantic (English).cue", "Snatcher (Japan)"))
        assertEquals("Final Fantasy VII (USA) (Disc 2).chd", false, CartLabelPaths.differs("Final Fantasy VII (USA) (Disc 2).chd", "Final Fantasy VII (USA) (Disc 2)"))
        assertTrue(CartLabelPaths.standardTag("En,Fr,De"))
        assertTrue(CartLabelPaths.standardTag("SLUS-00067"))
        assertFalse(CartLabelPaths.standardTag("QoL v1.2"))
    }
}
