package apikeysteward.generators

import apikeysteward.model.ApiKey
import apikeysteward.utils.Retry
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, catsSyntaxTuple2Parallel}
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ApiKeyGenerator(
    apiKeyPrefixProvider: ApiKeyPrefixProvider,
    stringApiKeyGenerator: RandomStringGenerator,
    checksumCalculator: CRC32ChecksumCalculator
) {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def generateApiKey: IO[ApiKey] =
    (
      apiKeyPrefixProvider.fetchPrefix,
      Retry.retry(maxRetries = 3, (_: Base62.Base62Error) => true)(generateApiKeyWithChecksum)
    ).parMapN { case (prefix, apiKeyWithChecksum) =>
      ApiKey(prefix + apiKeyWithChecksum)
    }

  private def generateApiKeyWithChecksum: IO[Either[Base62.Base62Error, String]] =
    (for {
      apiKey <- EitherT.right(stringApiKeyGenerator.generate)
      checksum = checksumCalculator.calcChecksumFor(apiKey)
      checksumEncoded <- EitherT(encodeChecksum(checksum))

      result = apiKey + checksumEncoded
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
