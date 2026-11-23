package fyi.slot.konkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiscGroupsTest {
    @Test fun discTags() {
        assertEquals(2, DiscGroups.discNumber("Fear Effect (USA) (Disc 2).chd"))
        assertEquals(1, DiscGroups.discNumber("Xenogears [Disc 1][Retranslated].chd"))
        assertEquals(3, DiscGroups.discNumber("Game (CD 3 of 4).pbp"))
        assertNull(DiscGroups.discNumber("Crash Bandicoot (USA).chd"))
        assertEquals("Fear Effect (USA)", DiscGroups.gameName("Fear Effect (USA) (Disc 2).chd"))
        assertEquals("Xenogears [Retranslated]", DiscGroups.gameName("Xenogears [Disc 1][Retranslated].chd"))
    }

    @Test fun playlists() {
        val text = "﻿#EXTM3U\r\n.hidden/Chrono Cross (Disc 1).chd\r\n\r\n.hidden\\Chrono Cross (Disc 2).chd\n"
        assertEquals(listOf(".hidden/Chrono Cross (Disc 1).chd", ".hidden\\Chrono Cross (Disc 2).chd"),
            DiscGroups.parseM3u(text))
        assertEquals(listOf(".hidden", "Chrono Cross (Disc 2).chd"),
            DiscGroups.segments(".hidden\\Chrono Cross (Disc 2).chd"))
        assertNull(DiscGroups.segments("../other/disc.chd"))
        assertNull(DiscGroups.segments("/sdcard/disc.chd"))
    }

    @Test fun theNewestDiscCardBecomesTheGames() {
        val dir = Files.createTempDirectory("discs").toFile()
        try {
            File(dir, "Fear Effect (USA) (Disc 1).srm").apply { writeBytes(byteArrayOf(1)); setLastModified(1_000_000) }
            File(dir, "Fear Effect (USA) (Disc 2).srm").apply { writeBytes(byteArrayOf(2)); setLastModified(2_000_000) }
            val save = File(dir, "Fear Effect (USA).srm")
            DiscGroups.adoptDiscSave(save, listOf("Fear Effect (USA) (Disc 1).chd", "Fear Effect (USA) (Disc 2).chd"))
            assertEquals(2, save.readBytes()[0].toInt())
            // Once the game has its own card, it stays.
            File(dir, "Fear Effect (USA) (Disc 1).srm").apply { writeBytes(byteArrayOf(9)); setLastModified(3_000_000) }
            DiscGroups.adoptDiscSave(save, listOf("Fear Effect (USA) (Disc 1).chd"))
            assertEquals(2, save.readBytes()[0].toInt())
        } finally {
            dir.deleteRecursively()
        }
    }
}
