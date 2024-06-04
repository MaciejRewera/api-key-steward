package apikeysteward.generators

import apikeysteward.generators.ChecksumCodec.ChecksumDecodingError.ProvidedEncodedChecksumTooLongError
import apikeysteward.generators.ChecksumCodec.{ChecksumDecodingError, EncodedChecksumLength}
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ChecksumCodec {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private val paddingChar: Char = '0'

  def encode(checksum: Long): IO[Either[Base62.Base62Error, String]] =
    Base62
      .encode(checksum)
      .map(addPadding)
      .map(_.mkString)
      .fold(
        err => logger.warn(s"Error while encoding checksum: ${err.message}") >> IO(err.asLeft),
        result => IO(result.asRight)
      )

  private def addPadding(chars: Array[Char]): Array[Char] = {
    val charsToAdd = EncodedChecksumLength - chars.length
    Array.fill(charsToAdd)(paddingChar) ++ chars
  }

  def decode(encodedChecksum: String): IO[Either[ChecksumDecodingError, Long]] =
    if (encodedChecksum.length > EncodedChecksumLength) {
      val error = ProvidedEncodedChecksumTooLongError(encodedChecksum)
      logger.warn(s"Error while decoding checksum: ${error.message}") >> IO(error.asLeft)
    } else
      IO(Base62.decode(encodedChecksum.toCharArray.dropWhile(_ == paddingChar)).asRight)
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
