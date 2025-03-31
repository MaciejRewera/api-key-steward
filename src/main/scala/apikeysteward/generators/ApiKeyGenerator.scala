package apikeysteward.generators

import apikeysteward.generators.ApiKeyGenerator.ApiKeyGeneratorError
import apikeysteward.model.{ApiKey, ApiKeyTemplate}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.model.errors.CustomError
import apikeysteward.repositories.ApiKeyTemplateRepository
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class ApiKeyGenerator(
    apiKeyTemplateRepository: ApiKeyTemplateRepository,
    randomStringGenerator: RandomStringGenerator,
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec
) extends Logging {

  def generateApiKey(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): IO[Either[ApiKeyGeneratorError, ApiKey]] =
    (for {
      config         <- fetchConfig(publicTenantId, publicTemplateId)
      randomFragment <- EitherT.right[ApiKeyGeneratorError](randomStringGenerator.generate(config.randomSectionLength))

      randomFragmentWithPrefix = config.apiKeyPrefix + randomFragment
      checksum                 = checksumCalculator.calcChecksumFor(randomFragmentWithPrefix)
      encodedChecksum <- encodeChecksum(checksum)

      res = ApiKey(randomFragmentWithPrefix + encodedChecksum)
    } yield res).value

  private def fetchConfig(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[IO, ApiKeyGeneratorError, ApiKeyTemplate] =
    EitherT.fromOptionF(
      apiKeyTemplateRepository.getBy(publicTenantId, publicTemplateId),
      ApiKeyGeneratorError(ApiKeyTemplateNotFoundError(publicTemplateId.toString))
    )

  private def encodeChecksum(checksum: Long): EitherT[IO, ApiKeyGeneratorError, String] =
    EitherT {
      checksumCodec.encode(checksum) match {
        case Left(error) =>
          logger.warn(s"Error while encoding checksum: ${error.message}") >>
            IO(ApiKeyGeneratorError(error).asLeft)

        case Right(encodedChecksum) => IO(encodedChecksum.asRight)
      }
    }

}

object ApiKeyGenerator {

  case class ApiKeyGeneratorError(cause: CustomError) extends CustomError {
    override val message: String = cause.message
  }

}
