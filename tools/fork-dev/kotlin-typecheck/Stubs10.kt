package android.database
interface Cursor : java.io.Closeable { fun moveToNext(): Boolean; fun getString(i: Int): String?; fun getLong(i: Int): Long; fun isNull(i: Int): Boolean; fun getColumnIndexOrThrow(n: String): Int; fun getColumnIndex(n: String): Int }
