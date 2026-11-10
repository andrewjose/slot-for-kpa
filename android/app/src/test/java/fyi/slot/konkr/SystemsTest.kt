package fyi.slot.konkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemsTest {
    @Test fun extensionsFileCartsOnTheirShelf() {
        assertEquals("NES", Systems.platformFor("FC"))
        assertEquals("SNES", Systems.platformFor("smc"))
        assertEquals("MD", Systems.platformFor("gen"))
        assertEquals("GB", Systems.platformFor("gb"))
        assertEquals(null, Systems.platformFor("iso"))
    }

    @Test fun coreFoldersFollowTheNativePickOnlyWhenItRunsTheSystem() {
        assertEquals("bsnes", Systems.coreFolder("bsnes", "sfc"))
        assertEquals("Snes9x", Systems.coreFolder("mgba", "sfc"))
        assertEquals("Genesis Plus GX", Systems.coreFolder("", "md"))
        assertEquals("PicoDrive", Systems.coreFolder("picodrive", "bin"))
        assertEquals("Nestopia", Systems.coreFolder("nestopia", "nes"))
        assertEquals("mGBA", Systems.coreFolder("gambatte", "gba"))
        assertEquals("Gambatte", Systems.coreFolder("gambatte", "gbc"))
        // Private files use the same key as the native side.
        assertEquals("genesis_plus_gx", Systems.keyForFolder("Genesis Plus GX"))
        assertEquals("mgba", Systems.keyForFolder("mGBA"))
        assertEquals("FCEUmm", Systems.folderForKey("fceumm"))
    }

    @Test fun playStationDiscsArePlayedInPlace() {
        assertEquals("PSX", Systems.platformFor("chd"))
        assertEquals("PSX", Systems.platformFor("PBP"))
        assertEquals(null, Systems.platformFor("cue"))
        assertTrue(Systems.isDisc("CHD") && Systems.isHome("chd"))
        assertFalse(Systems.isDisc("bin"))
        assertFalse("PSX" in Systems.HOME_PLATFORMS)
        assertEquals("PCSX-ReARMed", Systems.coreFolder("", "chd"))
        assertEquals("PCSX-ReARMed", Systems.coreFolder("snes9x", "pbp"))
        assertEquals("pcsx_rearmed", Systems.keyForFolder("PCSX-ReARMed"))
        assertTrue("chd" in Systems.ROM_EXTENSIONS)
        assertEquals("ab12.chd", DiscSource.linkName("ab12", "CHD"))
        assertEquals("/proc/self/fd/57", DiscSource.fdPath(57))
        // ES-DE's psx folder is searched now that slot. plays it.
        assertFalse("psx" in RomLibrary.OTHER_SYSTEM_DIRECTORIES)
        assertTrue("psp" in RomLibrary.OTHER_SYSTEM_DIRECTORIES)
    }

    @Test fun aChdInATurboGrafxCdFolderIsATurboGrafxCdGame() {
        assertEquals("TGCD", Systems.platformFor("chd", tgcdFolder = true))
        assertEquals("PSX", Systems.platformFor("chd", tgcdFolder = false))
        assertEquals("a PBP is the PlayStation's", "PSX", Systems.platformFor("pbp", tgcdFolder = true))
        assertTrue(Systems.isTgcdFolder("tg-cd") && Systems.isTgcdFolder("PCEngineCD"))
        assertFalse(Systems.isTgcdFolder("psx"))
        assertFalse("tg-cd" in RomLibrary.OTHER_SYSTEM_DIRECTORIES)
        assertFalse("pcenginecd" in RomLibrary.OTHER_SYSTEM_DIRECTORIES)
        // The native side's pick names the core of a CHD.
        assertEquals("Beetle PCE Fast", Systems.coreFolder("mednafen_pce_fast", "chd"))
        assertEquals("PCSX-ReARMed", Systems.coreFolder("", "chd"))
        assertEquals(setOf("Beetle PCE Fast"), Systems.coreFoldersFor("TGCD"))
        assertEquals("mednafen_pce_fast", Systems.keyForFolder("Beetle PCE Fast"))
    }

    @Test fun binNeedsASegaHeader() {
        val sega = ByteArray(0x200).also { "SEGA GENESIS    ".toByteArray().copyInto(it, 0x100) }
        assertTrue(Systems.acceptsHeader("bin", sega))
        assertFalse(Systems.acceptsHeader("bin", ByteArray(0x200)))
        assertTrue(Systems.acceptsHeader("md", ByteArray(0x200)))
    }

    @Test fun nesBatteryComesFromTheInesHeader() {
        val rom = ByteArray(0x150).also {
            "NES".toByteArray().copyInto(it)
            it[3] = 0x1a
            it[6] = 0x02
        }
        assertTrue(Systems.homeHeader("nes", rom, "Zelda (USA).nes").battery)
        rom[6] = 0
        assertFalse(Systems.homeHeader("nes", rom, "Mario.nes").battery)
        assertEquals("J", Systems.homeHeader("nes", rom, "Mario (Japan).nes").region)
    }

    @Test fun snesHeaderGivesTitleRegionAndBattery() {
        val rom = ByteArray(0x10000)
        val base = 0xffc0
        "ZELDA".padEnd(21).toByteArray().copyInto(rom, base)
        rom[base + 0x15] = 0x21          // HiROM
        rom[base + 0x16] = 0x02          // ROM + RAM + battery
        rom[base + 0x18] = 0x03          // 8 KiB SRAM
        rom[base + 0x19] = 0x00          // Japan
        rom[base + 0x1c] = 0xff.toByte(); rom[base + 0x1d] = 0xff.toByte()
        val header = Systems.homeHeader("sfc", rom, "game.sfc")
        assertEquals("ZELDA", header.title)
        assertEquals("J", header.region)
        assertTrue(header.battery)
        // The file name's No-Intro region wins over the header.
        assertEquals("U", Systems.homeHeader("sfc", rom, "Zelda (USA).sfc").region)
    }

    @Test fun megaDriveHeaderGivesRegionAndBattery() {
        val rom = ByteArray(0x200)
        "SEGA MEGA DRIVE ".toByteArray().copyInto(rom, 0x100)
        "PHANTASY STAR".toByteArray().copyInto(rom, 0x150)
        "RA".toByteArray().copyInto(rom, 0x1b0)
        rom[0x1b2] = 0xf8.toByte()
        "J".toByteArray().copyInto(rom, 0x1f0)
        val header = Systems.homeHeader("md", rom, "ps.md")
        assertEquals("PHANTASY STAR", header.title)
        assertEquals("J", header.region)
        assertTrue(header.battery)
        "JUE".toByteArray().copyInto(rom, 0x1f0)
        assertEquals("U", Systems.homeHeader("md", rom, "ps.md").region)
        "E  ".toByteArray().copyInto(rom, 0x1f0)
        assertEquals("E", Systems.homeHeader("md", rom, "ps.md").region)
        "4  ".toByteArray().copyInto(rom, 0x1f0)
        assertEquals("U", Systems.homeHeader("md", rom, "ps.md").region)
    }
}
