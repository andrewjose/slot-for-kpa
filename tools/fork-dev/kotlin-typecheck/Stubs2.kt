package android.os
class ParcelFileDescriptor : java.io.Closeable { val fd: Int = 0; override fun close() {} }
object SystemClock { @JvmStatic fun elapsedRealtime(): Long = 0 }
