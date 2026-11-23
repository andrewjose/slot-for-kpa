package android.util
object Log { @JvmStatic fun w(t: String, m: String, e: Throwable? = null): Int = 0; @JvmStatic fun i(t: String, m: String): Int = 0 }
class AtomicFile(f: java.io.File) { fun startWrite(): java.io.FileOutputStream = java.io.FileOutputStream("/dev/null"); fun finishWrite(s: java.io.FileOutputStream) {}; fun failWrite(s: java.io.FileOutputStream) {} }
