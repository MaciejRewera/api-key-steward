package apikeysteward.generators

import apikeysteward.model.ApiKey
import apikeysteward.utils.{Logging, Retry}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, catsSyntaxTuple2Parallel}

class ApiKeyGenerator(
    apiKeyPrefixProvider: ApiKeyPrefixProvider,
    randomStringGenerator: RandomStringGenerator,
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec
) extends Logging {

  def generateApiKey: IO[ApiKey] =
    for {
      prefix <- apiKeyPrefixProvider.fetchPrefix.memoize
      apiKey <- Retry.retry(maxRetries = 3, (_: Base62.Base62Error) => true)(generateApiKeyWithChecksum(prefix))
    } yield ApiKey(apiKey)

  private def generateApiKeyWithChecksum(prefixIO: IO[String]): IO[Either[Base62.Base62Error, String]] =
    generateRandomFragmentWithPrefix(prefixIO).flatMap { randomFragmentWithPrefix =>
      val checksum = checksumCalculator.calcChecksumFor(randomFragmentWithPrefix)
      checksumCodec.encode(checksum) match {
        case Left(error)            => logger.warn(s"Error while encoding checksum: ${error.message}") >> IO(error.asLeft)
        case Right(encodedChecksum) => IO((randomFragmentWithPrefix + encodedChecksum).asRight)
      }
    }

  private def generateRandomFragmentWithPrefix(prefixIO: IO[String]): IO[String] =
    (
      prefixIO,
      randomStringGenerator.generate
    ).parMapN { case (prefix, randomFragment) => prefix + randomFragment }

}
