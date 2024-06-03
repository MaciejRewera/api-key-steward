package apikeysteward.generators

import apikeysteward.model.ApiKey
import apikeysteward.utils.Retry
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxTuple2Parallel

class ApiKeyGenerator(
    apiKeyPrefixProvider: ApiKeyPrefixProvider,
    randomStringGenerator: RandomStringGenerator,
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec
) {

  def generateApiKey: IO[ApiKey] =
    for {
      prefix <- apiKeyPrefixProvider.fetchPrefix.memoize
      apiKey <- Retry.retry(maxRetries = 3, (_: Base62.Base62Error) => true)(generateApiKeyWithChecksum(prefix))
    } yield ApiKey(apiKey)

  private def generateApiKeyWithChecksum(prefixIO: IO[String]): IO[Either[Base62.Base62Error, String]] =
    generateRandomFragmentWithPrefix(prefixIO).flatMap { randomFragmentWithPrefix =>
      val checksum = checksumCalculator.calcChecksumFor(randomFragmentWithPrefix)
      EitherT(checksumCodec.encode(checksum)).map(randomFragmentWithPrefix + _).value
    }

  private def generateRandomFragmentWithPrefix(prefixIO: IO[String]): IO[String] =
    (
      prefixIO,
      randomStringGenerator.generate
    ).parMapN { case (prefix, randomFragment) => prefix + randomFragment }

}
