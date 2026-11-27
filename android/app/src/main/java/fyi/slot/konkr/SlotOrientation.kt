package fyi.slot.konkr

import android.content.pm.ActivityInfo
import org.json.JSONObject
import java.io.File

/**
 * Interface > Orientation (Andrew, 2026-11-26): Landscape (slot.'s own way),
 * Portrait (handhelds like the RG Rotate, which otherwise start landscape and
 * then turn) or the device's way. The choice lives in slot.'s settings file
 * ("orientation": "landscape" / "portrait" / "device"); the native menu sends
 * a change as UI action 41, 42 or 43.
 */
internal object SlotOrientation {
    const val SETTINGS_FILE = "slot-konkr-settings.json"

    fun forName(name: String?): Int = when (name) {
        "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        "device" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    fun fromAction(action: Int): Int = when (action) {
        42 -> forName("portrait")
        43 -> forName("device")
        else -> forName("landscape")
    }

    /** The saved choice; Landscape when there is none or it can't be read. */
    fun saved(filesDir: File): Int = try {
        val file = File(filesDir, SETTINGS_FILE)
        forName(if (file.isFile) JSONObject(file.readText()).optString("orientation", "landscape") else null)
    } catch (error: Exception) {
        forName(null)
    }
}
