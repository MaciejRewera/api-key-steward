package apikeysteward.services

import apikeysteward.generators.{CRC32ChecksumCalculator, ChecksumCodec}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ApiKey, ApiKeyData}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

import java.time.{Clock, Instant}

class ApiKeyValidationService(
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec,
    apiKeyRepository: ApiKeyRepository
)(implicit clock: Clock)
    extends Logging {

  def validateApiKey(publicTenantId: TenantId, apiKey: ApiKey): IO[Either[ApiKeyValidationError, ApiKeyData]] =
    (for {
      _ <- validateChecksum(apiKey)

      apiKeyData <- EitherT(
        apiKeyRepository
          .get(publicTenantId, apiKey)
          .map(_.toRight[ApiKeyValidationError](ApiKeyIncorrectError))
      )
      _ <- validateExpiryDate(apiKeyData)
        .leftSemiflatTap(_ =>
          logger.info(
            s"Provided API Key with key ID: [${apiKeyData.publicKeyId}] is expired since: ${apiKeyData.expiresAt}."
          )
        )

    } yield apiKeyData)
      .biSemiflatTap(
        _ => logger.info("Provided API Key is incorrect or does not exist."),
        apiKeyData => logger.info(s"Provided API Key with key ID: [${apiKeyData.publicKeyId}] is valid.")
      )
      .value

  private def validateChecksum(apiKey: ApiKey): EitherT[IO, ApiKeyValidationError, ApiKey] = EitherT {
    val splitIndex                           = apiKey.value.length - ChecksumCodec.EncodedChecksumLength
    val (randomFragmentWithPrefix, checksum) = apiKey.value.splitAt(splitIndex)

    val calculatedChecksum = checksumCalculator.calcChecksumFor(randomFragmentWithPrefix)

    checksumCodec.decode(checksum) match {
      case Left(error) =>
        logger.warn(s"Error while decoding checksum: ${error.message}") >> IO.pure(ApiKeyIncorrectError.asLeft)

      case Right(decodedChecksum) =>
        IO {
          if (calculatedChecksum == decodedChecksum)
            apiKey.asRight
          else
            ApiKeyIncorrectError.asLeft
        }
    }
  }

  private def validateExpiryDate(apiKeyData: ApiKeyData): EitherT[IO, ApiKeyValidationError, ApiKeyData] =
    EitherT.fromEither[IO](
      Either.cond(
        apiKeyData.expiresAt.isAfter(Instant.now(clock)),
        apiKeyData,
        ApiKeyExpiredError(apiKeyData.expiresAt)
      )
    )

}

object ApiKeyValidationService {

  sealed abstract class ApiKeyValidationError

  object ApiKeyValidationError {
    case object ApiKeyIncorrectError                     extends ApiKeyValidationError
    case class ApiKeyExpiredError(expiredSince: Instant) extends ApiKeyValidationError
  }

}
