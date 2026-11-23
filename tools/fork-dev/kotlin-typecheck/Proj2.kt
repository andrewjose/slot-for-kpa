package fyi.slot.konkr
internal object CartStudioCatalog {
  internal data class Rom(val uri: String, val platform: String, val title: String, val size: Long, val modified: Long, val filename: String = "")
  internal data class Fingerprint(val crc: Long, val head: ByteArray) { val hex: String get() = "" }
  fun games(c: android.content.Context): List<Rom> = emptyList()
  fun cached(c: android.content.Context, r: Rom): Fingerprint? = null
  fun identify(c: android.content.Context, r: Rom): Fingerprint = Fingerprint(0, ByteArray(0))
}
