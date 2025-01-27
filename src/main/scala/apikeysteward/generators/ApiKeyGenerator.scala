package apikeysteward.generators

import apikeysteward.generators.ApiKeyGenerator.ApiKeyGeneratorError
import apikeysteward.model.ApiKey
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.CustomError
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class ApiKeyGenerator(
    apiKeyPrefixProvider: ApiKeyPrefixProvider,
    randomStringGenerator: RandomStringGenerator,
    checksumCalculator: CRC32ChecksumCalculator,
    checksumCodec: ChecksumCodec
) extends Logging {

  def generateApiKey(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): IO[Either[ApiKeyGeneratorError, ApiKey]] =
    (for {
      prefix <- fetchPrefix(publicTenantId, publicTemplateId)
      randomFragment <- EitherT.right[ApiKeyGeneratorError](randomStringGenerator.generate)
      randomFragmentWithPrefix = prefix + randomFragment

      checksum = checksumCalculator.calcChecksumFor(randomFragmentWithPrefix)
      encodedChecksum <- encodeChecksum(checksum)

      res = ApiKey(randomFragmentWithPrefix + encodedChecksum)
    } yield res).value

  private def fetchPrefix(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[IO, ApiKeyGeneratorError, String] =
    EitherT(apiKeyPrefixProvider.fetchPrefix(publicTenantId, publicTemplateId))
      .leftMap(ApiKeyGeneratorError)

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
