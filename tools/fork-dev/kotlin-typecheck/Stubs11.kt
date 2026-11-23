package android.provider
object DocumentsContract { object Document { const val COLUMN_DOCUMENT_ID = "a"; const val COLUMN_DISPLAY_NAME = "b"; const val COLUMN_MIME_TYPE = "c"; const val COLUMN_SIZE = "d"; const val COLUMN_LAST_MODIFIED = "e"; const val MIME_TYPE_DIR = "f" }
  @JvmStatic fun getTreeDocumentId(u: android.net.Uri): String = ""; @JvmStatic fun buildChildDocumentsUriUsingTree(u: android.net.Uri, id: String) = u; @JvmStatic fun buildDocumentUriUsingTree(u: android.net.Uri, id: String) = u }
