package fyi.slot.konkr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/**
 * "AJ EDITION" under the slot. wordmark on the boot screen (Andrew,
 * 2026-11-27, mockup B2): spaced bold capitals in light blue, the right edge
 * under the "t" (not the period), just below the baseline.
 *
 * It is placed from the wordmark vector's own frame: slot_boot_wordmark_vector
 * is a 108x72 viewport drawn FIT_CENTER, so the same scale and offsets hold on
 * the KONKR, the Retroid Pocket Classic and the RG Rotate in portrait.
 */
internal class BootEditionTag(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
        letterSpacing = 0.25f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val scale = min(width / VIEWPORT_W, height / VIEWPORT_H)
        val left = (width - VIEWPORT_W * scale) / 2f
        val top = (height - VIEWPORT_H * scale) / 2f
        paint.textSize = TEXT_SIZE * scale
        canvas.drawText(TEXT, left + T_RIGHT * scale, top + BASELINE * scale, paint)
    }

    companion object {
        const val TEXT = "AJ EDITION"
        const val COLOR = 0xFF7FB8FF.toInt()
        // slot_boot_wordmark_vector's viewport and the wordmark inside it
        // (its group transform applied): the t's right edge, the letters'
        // bottom and their height.
        const val VIEWPORT_W = 108f
        const val VIEWPORT_H = 72f
        const val T_RIGHT = 80.45f
        const val WORD_BOTTOM = 48.48f
        const val WORD_HEIGHT = 25.23f
        /** 17% of the wordmark's height, its baseline 1.8 sizes below. */
        const val TEXT_SIZE = WORD_HEIGHT * 0.17f
        const val BASELINE = WORD_BOTTOM + TEXT_SIZE * 1.8f
    }
}
