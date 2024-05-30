package apikeysteward.model

case class HashedApiKey(value: String) extends AnyVal

object HashedApiKey {

  def apply(bytes: Array[Byte]): HashedApiKey = HashedApiKey(bytesToHex(bytes))

  private def bytesToHex(bytes: Array[Byte]): String =
    bytes.foldLeft("")((acc, b) => acc + String.format("%02X", b))
}
