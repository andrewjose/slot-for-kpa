package fyi.slot.konkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CartLabelResetTest {
    private fun install(): Triple<File, File, File> {
        val root = Files.createTempDirectory("slot-reset").toFile()
        val auto = CartLabelPaths.file(root, "content://roms/auto.gba")
        val mine = CartLabelPaths.file(root, "content://roms/mine.gba")
        auto.parentFile!!.mkdirs()
        auto.writeText("png"); mine.writeText("png")
        CartLabelPaths.markBorrowed(auto, true)
        CartLabelPaths.markBorrowed(mine, true)
        CartLabelReset.pickedMarker(mine).writeText("")
        File(root, "home-real-labels").mkdirs(); File(root, "home-real-labels/done.json").writeText("{}")
        File(root, "home-labels-v1.json").writeText("{}")
        File(root, "Cache/cartridge-faces-qa102").mkdirs()
        File(root, "rom-library-cache-v1.json").writeText(
            """{"games":[{"uri":"content://roms/auto.gba"},{"uri":"content://roms/mine.gba"}]}""")
        return Triple(root, auto, mine)
    }

    @Test fun keepingPicksRedoesOnlyTheAutomaticLabels() {
        val (root, auto, mine) = install()
        assertEquals(1, CartLabelReset.pickedCount(root))
        val result = CartLabelReset.reset(root, keepPicks = true)
        assertFalse(auto.exists())
        assertFalse(File(auto.parentFile, auto.nameWithoutExtension + ".title").exists())
        assertTrue(mine.exists())
        assertTrue("his title bar stays", File(mine.parentFile, mine.nameWithoutExtension + ".title").exists())
        assertEquals(listOf("content://roms/auto.gba"), result.uris)
        assertEquals(1 to 1, result.removed to result.kept)
        // Nothing is left marked as already tried.
        assertFalse(File(root, "home-real-labels").exists())
        assertFalse(File(root, "home-labels-v1.json").exists())
        assertFalse(File(root, "Cache/cartridge-faces-qa102").exists())
        root.deleteRecursively()
    }

    @Test fun redoingEverythingTakesHisPicksToo() {
        val (root, auto, mine) = install()
        val result = CartLabelReset.reset(root, keepPicks = false)
        assertFalse(auto.exists() || mine.exists())
        assertEquals(0, CartLabelReset.pickedCount(root))
        assertEquals(2, result.uris.size)
        root.deleteRecursively()
    }
}
