package android.graphics
class Canvas(b: Bitmap) { fun drawBitmap(b: Bitmap, s: Rect?, d: RectF, p: Paint?) {}; fun drawBitmap(b: Bitmap, s: Rect?, d: Rect, p: Paint?) {}; fun drawColor(c: Int) {} }
class Paint(f: Int = 0) { var isFilterBitmap = false; var isAntiAlias = false; companion object { const val FILTER_BITMAP_FLAG = 2; const val ANTI_ALIAS_FLAG = 1 } }
class RectF(l: Float, t: Float, r: Float, b: Float)
class Rect(l: Int, t: Int, r: Int, b: Int)
object Color { const val TRANSPARENT = 0; const val WHITE = -1; const val BLACK = 0 }
