package fyi.slot.konkr

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * slot. as the home app: brings back the slot. already running instead of
 * Android starting a second one in its home task. MainActivity is
 * singleTask, so it comes to the front with onNewIntent.
 * (Backported from upstream v2.4.0, #109.)
 */
class SlotHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SlotOrientation.launcherClass(this))
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
