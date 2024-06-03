package apikeysteward.generators

import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ChecksumCodec {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def encode(checksum: Long): IO[Either[Base62.Base62Error, String]] =
    Base62
      .encode(checksum)
      .map(addPaddingZeros)
      .map(_.mkString)
      .fold(
        err => logger.warn(s"Error while encoding checksum: ${err.message}") >> IO.pure(err.asLeft),
        result => IO.pure(result.asRight)
      )

  private val MaxEncodedChecksumLength = 6
  private def addPaddingZeros(chars: Array[Char]): Array[Char] = {
    val zerosToAdd = MaxEncodedChecksumLength - chars.length
    Array.fill(zerosToAdd)('0') ++ chars
  }

  def decode(encodedChecksum: String): Long =
    Base62.decode(encodedChecksum.toCharArray)
}
