package fyi.slot.konkr

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log
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

    /**
     * Portrait must be in the manifest to hold from the first frame (the
     * starting window is drawn before onCreate): PortraitActivity is the
     * launcher entry while Portrait is chosen, MainActivity (landscape)
     * otherwise. Only one is enabled, so one slot. ever runs. A change takes
     * effect from the next launch; the running one turns at once.
     */
    fun useLauncher(context: Context, portrait: Boolean) {
        val pm = context.packageManager
        fun set(cls: Class<*>, on: Boolean) {
            val name = ComponentName(context, cls)
            val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                if (pm.getComponentEnabledSetting(name) != want) {
                    pm.setComponentEnabledSetting(name, want, PackageManager.DONT_KILL_APP)
                }
            } catch (error: Exception) {
                Log.w("SlotOrientation", "Launcher entry not switched", error)
            }
        }
        // Enable the new entry first, so there is never no launcher icon.
        if (portrait) {
            set(PortraitActivity::class.java, true)
            set(MainActivity::class.java, false)
        } else {
            set(MainActivity::class.java, true)
            set(PortraitActivity::class.java, false)
        }
    }

    /** The launcher entry in use (for the home app's hand-over). */
    fun launcherClass(context: Context): Class<out MainActivity> {
        val state = context.packageManager.getComponentEnabledSetting(
            ComponentName(context, PortraitActivity::class.java))
        return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) PortraitActivity::class.java
            else MainActivity::class.java
    }

    /** The saved choice; Landscape when there is none or it can't be read. */
    fun saved(filesDir: File): Int = try {
        val file = File(filesDir, SETTINGS_FILE)
        forName(if (file.isFile) JSONObject(file.readText()).optString("orientation", "landscape") else null)
    } catch (error: Exception) {
        forName(null)
    }
}
