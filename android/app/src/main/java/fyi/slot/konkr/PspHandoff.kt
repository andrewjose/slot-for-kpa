package fyi.slot.konkr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * The PSP shelf is a launcher: slot. shows the games and hands each one to
 * the PPSSPP app (Andrew, 2026-11-23). PPSSPP draws with the GPU, which
 * slot.'s libretro host can't run, and his PPSSPP fork has its own frame
 * generation. The game opens the way ES-DE starts it: PpssppActivity with
 * the file's URI, read permission granted.
 */
internal object PspHandoff {
    private const val TAG = "SlotPspHandoff"

    /** His PPSSPP test builds (the Legacy package) first, then Gold, then
     *  the store app. */
    val PACKAGES = listOf("org.ppsspp.ppsspplegacy", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspp")

    /** PPSSPP's activity keeps its class name in every package. */
    const val ACTIVITY = "org.ppsspp.ppsspp.PpssppActivity"

    /** The first PPSSPP installed, or null. */
    fun installed(context: Context): String? = PACKAGES.firstOrNull { name ->
        try {
            context.packageManager.getPackageInfo(name, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun intent(uri: Uri, packageName: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setClassName(packageName, ACTIVITY)
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    /** Starts the game in PPSSPP; false when no PPSSPP can take it. */
    fun launch(context: Context, uri: Uri): Boolean {
        val packageName = installed(context) ?: return false
        return try {
            context.startActivity(intent(uri, packageName))
            Log.i(TAG, "PSP game handed to $packageName")
            true
        } catch (error: Exception) {
            Log.w(TAG, "PPSSPP refused the game", error)
            false
        }
    }
}
