package apikeysteward.generators

import java.util.zip.CRC32

class CRC32ChecksumCalculator {

  def calcChecksumFor(apiKey: String): Long = {
    val crc32 = new CRC32
    crc32.update(apiKey.toCharArray.map(_.toByte))

    crc32.getValue
  }
}
