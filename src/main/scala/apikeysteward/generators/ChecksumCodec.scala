package apikeysteward.generators

import apikeysteward.generators.ChecksumCodec.ChecksumDecodingError.ProvidedEncodedChecksumTooLongError
import apikeysteward.generators.ChecksumCodec.{ChecksumDecodingError, EncodedChecksumLength}
import cats.implicits.catsSyntaxEitherId

class ChecksumCodec {

  private val paddingChar: Char = '0'

  def encode(checksum: Long): Either[Base62.Base62Error, String] =
    Base62.encode(checksum).map(addPadding).map(_.mkString)

  private def addPadding(chars: Array[Char]): Array[Char] = {
    val charsToAdd = EncodedChecksumLength - chars.length
    Array.fill(charsToAdd)(paddingChar) ++ chars
  }

  def decode(encodedChecksum: String): Either[ChecksumDecodingError, Long] =
    if (encodedChecksum.length > EncodedChecksumLength)
      ProvidedEncodedChecksumTooLongError(encodedChecksum).asLeft
    else
      Base62.decode(encodedChecksum.toCharArray.dropWhile(_ == paddingChar)).asRight
}

object ChecksumCodec {
  val EncodedChecksumLength: Int = 6

  sealed abstract class ChecksumDecodingError(val message: String)
  object ChecksumDecodingError {
    case class ProvidedEncodedChecksumTooLongError(encodedChecksum: String)
        extends ChecksumDecodingError(
          s"Checksum should have at most $EncodedChecksumLength characters, but received checksum of length ${encodedChecksum.length}: '$encodedChecksum'."
        )
  }

}
