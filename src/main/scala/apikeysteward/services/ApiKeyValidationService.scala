package apikeysteward.services

import apikeysteward.generators.{CRC32ChecksumCalculator, ChecksumCodec}
import apikeysteward.model.{ApiKey, ApiKeyData}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.definitions.ApiErrorMessages.ValidateApiKey
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.ApiKeyIncorrectError
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ApiKeyValidationService(
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec,
    apiKeyRepository: ApiKeyRepository
) {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def validateApiKey(apiKey: ApiKey): IO[Either[ApiKeyValidationError, ApiKeyData]] =
    (for {
      _ <- validateChecksum(apiKey)

      apiKeyDataEntity <- EitherT(
        apiKeyRepository
          .get(apiKey)
          .map(_.toRight[ApiKeyValidationError](ApiKeyIncorrectError))
      )
    } yield apiKeyDataEntity).value

  private def validateChecksum(apiKey: ApiKey): EitherT[IO, ApiKeyValidationError, ApiKey] = EitherT {
    val splitIndex = apiKey.value.length - ChecksumCodec.EncodedChecksumLength
    val (randomFragmentWithPrefix, checksum) = apiKey.value.splitAt(splitIndex)

    val calculatedChecksum = checksumCalculator.calcChecksumFor(randomFragmentWithPrefix)

    checksumCodec.decode(checksum) match {
      case Left(error) =>
        logger.warn(s"Error while decoding checksum: ${error.message}") >> IO.pure(ApiKeyIncorrectError.asLeft)

      case Right(decodedChecksum) =>
        if (calculatedChecksum == decodedChecksum)
          IO(apiKey.asRight)
        else
          logger.warn(s"Provided API Key: [${apiKey.value}] contains invalid checksum.") >>
            IO(ApiKeyIncorrectError.asLeft)
    }
  }

}

object ApiKeyValidationService {

  sealed abstract class ApiKeyValidationError(val message: String)
  object ApiKeyValidationError {

    case object ApiKeyIncorrectError extends ApiKeyValidationError(ValidateApiKey.ValidateApiKeyIncorrect)
  }

}
