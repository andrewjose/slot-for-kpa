package android.graphics
class Bitmap { val width = 0; val height = 0; enum class Config { ARGB_8888 }; enum class CompressFormat { PNG }
  fun compress(f: CompressFormat, q: Int, o: java.io.OutputStream): Boolean = true; fun recycle() {}
  fun getPixels(a: IntArray, o: Int, st: Int, x: Int, y: Int, w: Int, h: Int) {}; fun getPixel(x: Int, y: Int) = 0; fun setPixels(a: IntArray, o: Int, st: Int, x: Int, y: Int, w: Int, h: Int) {}
  companion object { @JvmStatic fun createBitmap(w: Int, h: Int, c: Config): Bitmap = Bitmap(); @JvmStatic fun createBitmap(b: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap = b; @JvmStatic fun createScaledBitmap(b: Bitmap, w: Int, h: Int, f: Boolean): Bitmap = b } }
object BitmapFactory { class Options { var inJustDecodeBounds = false; var outWidth = 0; var outHeight = 0; var inSampleSize = 1; var inPreferredConfig: Bitmap.Config? = null }
  @JvmStatic fun decodeByteArray(b: ByteArray, o: Int, l: Int, opts: Options? = null): Bitmap? = null }
