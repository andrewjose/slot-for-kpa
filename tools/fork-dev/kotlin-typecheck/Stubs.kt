package android.content
open class ContentResolver { fun openFileDescriptor(u: android.net.Uri, m: String): android.os.ParcelFileDescriptor? = null; fun openInputStream(u: android.net.Uri): java.io.InputStream? = null; fun query(u: android.net.Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): android.database.Cursor? = null }
open class Context { val cacheDir: java.io.File = java.io.File("/tmp"); val filesDir: java.io.File = java.io.File("/tmp"); val contentResolver = ContentResolver() }
