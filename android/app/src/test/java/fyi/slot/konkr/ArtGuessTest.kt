package fyi.slot.konkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtGuessTest {
    private fun k(name: String, serial: String? = null) = ArtGuess.Known(name, "00000000", serial)

    private val gba = listOf(
        k("Pokemon - Emerald Version (USA, Europe)", "BPEE"),
        k("Pokemon - FireRed Version (USA, Europe)", "BPRE"),
        k("Pokemon - Version Emeraude (France)", "BPEF"),
        k("Castlevania - Aria of Sorrow (USA)", "A2CE"),
        k("Castlevania - Aria of Sorrow (Europe) (En,Fr,De)", "A2CP"),
    )
    private val gb = listOf(
        k("Super Mario Land (World)"),
        k("Super Mario Land 2 - 6 Golden Coins (USA, Europe)"),
        k("Wario Land II (USA, Europe) (SGB Enhanced) (GB Compatible)"),
        k("Donkey Kong Land 2 (USA, Europe) (SGB Enhanced)"),
    )
    private val psx = listOf(
        k("Fear Effect (USA) (Disc 1)"), k("Fear Effect (USA) (Disc 2)"),
        k("Colin McRae Rally (USA)"), k("Colin McRae Rally 2.0 (USA) (En,Fr,Es)"),
        k("Gundam Battle Assault (USA)"), k("Gundam Battle Assault 2 (USA)"),
        k("Ace Combat 3 - Electrosphere (USA)"), k("Ace Combat 3 - Electrosphere (Japan) (Disc 1)"),
    )

    private fun best(file: String, known: List<ArtGuess.Known>, serial: String? = null, titles: List<String> = emptyList()) =
        ArtGuess.rank(ArtGuess.Clues(file, titles, serial), known, 1).firstOrNull()?.known?.name

    @Test fun aHackKeepsItsGamesCover() {
        assertEquals("Castlevania - Aria of Sorrow (USA)",
            best("Castlevania Aria of Sorrow Definitive Edition (Tommy Edition) v1.0.gba", gba))
        assertEquals("Pokemon - Emerald Version (USA, Europe)", best("Pokemon Emerald Enhanced (v9.545).zip", gba))
    }

    @Test fun theGbaCodeNamesAHackWhateverItsFileIsCalled() {
        assertEquals("Pokemon - Emerald Version (USA, Europe)", best("lazarus-v2.gba", gba, "BPEE"))
        assertEquals("Pokemon - FireRed Version (USA, Europe)", best("Pokemon Unbound Battle Frontier.gba", gba, "BPRE"))
    }

    @Test fun aSequelsNumberDecides() {
        assertEquals("Super Mario Land 2 - 6 Golden Coins (USA, Europe)", best("Super Mario Land 2 DX.gbc", gb))
        assertEquals("Donkey Kong Land 2 (USA, Europe) (SGB Enhanced)", best("Donkey Kong Land 2 DX.gbc", gb))
        assertEquals("Gundam Battle Assault 2 (USA)", best("Gundam Battle Assault 2 (USA).chd", psx))
        assertEquals("Colin McRae Rally 2.0 (USA) (En,Fr,Es)", best("Colin McRae Rally 2.0 (USA) (En,Fr,Es).chd", psx))
    }

    @Test fun theDiscNumberAndRegionAreKept() {
        assertEquals("Fear Effect (USA) (Disc 2)", best("Fear Effect (USA) (Disc 2).chd", psx))
        assertEquals("Ace Combat 3 - Electrosphere (USA)",
            best("Ace Combat 3 - Electrosphere (English v1.1)(Disc 1)[LoadWordTeam].chd", psx))
    }

    @Test fun nothingInCommonIsNoLabel() {
        assertTrue(ArtGuess.rank(ArtGuess.Clues("Hues of Mana v1.1.zip"), gb).isEmpty())
    }

    @Test fun datsAndThumbnailNames() {
        val dat = "clrmamepro (\n\tname \"x\"\n)\n\ngame (\n\tname \"Castlevania - Symphony of the Night (USA)\"\n" +
            "\tserial \"SLUS-00067\"\n\trom ( name \"a.bin\" size 1 crc 1a2b3c4d )\n)\n"
        val parsed = ArtGuess.parseDat(dat)
        assertEquals(1, parsed.size)
        assertEquals("SLUS-00067", parsed[0].serial)
        assertEquals("1A2B3C4D", parsed[0].crc)
        assertEquals("Ys I _ II_ The Book", ArtGuess.thumbnailName("Ys I & II: The Book"))
    }
}
