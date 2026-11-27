package fyi.slot.konkr

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/**
 * Andrew's personal build carries his default wallpaper as
 * assets/personal/wallpaper.png, added after CI like his BIOS (it is not his
 * art, so it never goes in the public repo or a release). On a fresh install
 * it becomes Wallpapers/user.png before slot. reads its settings, and the
 * fresh-install settings then show it (Settings::load_for_device).
 */
internal object PersonalDefaults {
    private const val TAG = "SlotPersonal"
    const val WALLPAPER_ASSET = "personal/wallpaper.png"

    fun seed(context: Context) {
        val root = context.filesDir
        if (File(root, SlotOrientation.SETTINGS_FILE).exists()) return
        val target = File(root, "Wallpapers/user.png")
        if (target.exists()) return
        val bytes = try {
            context.assets.open(WALLPAPER_ASSET).use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            return // the public build: no personal wallpaper
        } catch (error: Exception) {
            Log.w(TAG, "Personal wallpaper unreadable", error)
            return
        }
        try {
            target.parentFile?.mkdirs()
            val file = AtomicFile(target)
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (error: Exception) {
                file.failWrite(out)
                throw error
            }
            Log.i(TAG, "Fresh install: personal wallpaper in place")
        } catch (error: Exception) {
            Log.w(TAG, "Personal wallpaper not written", error)
        }
    }
}
