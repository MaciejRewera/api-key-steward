package apikeysteward.services

import apikeysteward.generators.{CRC32ChecksumCalculator, ChecksumCodec}
import apikeysteward.model.{ApiKey, ApiKeyData}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.{Clock, Instant}

class ApiKeyValidationService(
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec,
    apiKeyRepository: ApiKeyRepository
)(implicit clock: Clock) {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def validateApiKey(apiKey: ApiKey): IO[Either[ApiKeyValidationError, ApiKeyData]] =
    (for {
      _ <- validateChecksum(apiKey)

      apiKeyData <- EitherT(
        apiKeyRepository
          .get(apiKey)
          .map(_.toRight[ApiKeyValidationError](ApiKeyIncorrectError))
      )
      _ <- validateExpiryDate(apiKeyData)
        .leftSemiflatTap(_ =>
          logger.info(s"Provided API Key: [${apiKey.value}] is expired since: ${apiKeyData.expiresAt}.")
        )

    } yield apiKeyData).value

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
          logger.info(s"Provided API Key: [${apiKey.value}] contains invalid checksum.") >>
            IO(ApiKeyIncorrectError.asLeft)
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
    case object ApiKeyIncorrectError extends ApiKeyValidationError
    case class ApiKeyExpiredError(expiredSince: Instant) extends ApiKeyValidationError
  }

}
