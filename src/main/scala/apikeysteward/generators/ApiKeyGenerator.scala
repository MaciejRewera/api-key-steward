package apikeysteward.generators

import apikeysteward.utils.Retry
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ApiKeyGenerator(
    apiKeyPrefixProvider: ApiKeyPrefixProvider,
    stringApiKeyGenerator: RandomStringGenerator,
    checksumCalculator: CRC32ChecksumCalculator
) {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def generateApiKey: IO[String] =
    Retry.retry(maxRetries = 3, (_: Base62.Base62Error) => true)(generateApiKeyAction)

  private def generateApiKeyAction: IO[Either[Base62.Base62Error, String]] =
    (for {
      apiKey <- EitherT(stringApiKeyGenerator.generate.map(_.asRight))
      checksum = checksumCalculator.calcChecksumFor(apiKey)
      checksumEncoded <- EitherT(encodeChecksum(checksum))
      prefix <- EitherT(apiKeyPrefixProvider.fetchPrefix.map(_.asRight[Base62.Base62Error]))

      result = prefix + apiKey + checksumEncoded
    } yield result).value

  private def encodeChecksum(checksum: Long): IO[Either[Base62.Base62Error, String]] =
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
}
