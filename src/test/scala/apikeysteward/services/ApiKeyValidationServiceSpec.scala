package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.generators.ChecksumCodec.ChecksumDecodingError.ProvidedEncodedChecksumTooLongError
import apikeysteward.generators.{CRC32ChecksumCalculator, ChecksumCodec}
import apikeysteward.model.ApiKey
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyValidationServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with FixedClock {

  private val checksumCalculator = mock[CRC32ChecksumCalculator]
  private val checksumCodec      = mock[ChecksumCodec]
  private val apiKeyRepository   = mock[ApiKeyRepository]

  private val apiKeyValidationService = new ApiKeyValidationService(checksumCalculator, checksumCodec, apiKeyRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(checksumCalculator, checksumCodec, apiKeyRepository)
  }

  "ApiKeyValidationService on validateApiKey" when {

    "provided with a valid key" should {

      "call CRC32ChecksumCalculator, ChecksumCodec and ApiKeyRepository" in {
        val checksum = 42L
        checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
        checksumCodec.decode(any[String]).returns(checksum.asRight)
        apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(Some(apiKeyData_1)))

        for {
          _ <- apiKeyValidationService.validateApiKey(publicTenantId_1, apiKey_1)

          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomSection_1))
          _ = verify(checksumCodec).decode(eqTo(checksum_1))
          _ = verify(apiKeyRepository).get(eqTo(publicTenantId_1), eqTo(apiKey_1))
        } yield ()
      }

      "return ApiKeyData returned from ApiKeyRepository" when {

        "the key's expiresAt is 1 nanosecond in the future" in {
          val checksum   = 42L
          val apiKeyData = apiKeyData_1.copy(expiresAt = nowInstant.plusNanos(1))

          checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
          checksumCodec.decode(any[String]).returns(checksum.asRight)
          apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(Some(apiKeyData)))

          apiKeyValidationService.validateApiKey(publicTenantId_1, apiKey_1).asserting(_ shouldBe Right(apiKeyData))
        }

        "the key's expiresAt is 1 second in the future" in {
          val checksum = 42L
          checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
          checksumCodec.decode(any[String]).returns(checksum.asRight)
          apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(Some(apiKeyData_1)))

          apiKeyValidationService.validateApiKey(publicTenantId_1, apiKey_1).asserting(_ shouldBe Right(apiKeyData_1))
        }
      }
    }

    "provided with a key containing incorrect checksum" when {

      "ChecksumCodec returns Left containing an error" should {

        "NOT call ApiKeyRepository" in {
          checksumCalculator.calcChecksumFor(any[String]).returns(42L)
          checksumCodec.decode(any[String]).returns(ProvidedEncodedChecksumTooLongError("tooLongChecksum").asLeft)

          for {
            _ <- apiKeyValidationService.validateApiKey(publicTenantId_1, apiKey_1)
            _ = verifyZeroInteractions(apiKeyRepository)
          } yield ()
        }

        "return Left containing ApiKeyIncorrectError" in {
          checksumCalculator.calcChecksumFor(any[String]).returns(42L)
          checksumCodec.decode(any[String]).returns(ProvidedEncodedChecksumTooLongError("tooLongChecksum").asLeft)

          apiKeyValidationService
            .validateApiKey(publicTenantId_1, apiKey_1)
            .asserting(_ shouldBe Left(ApiKeyIncorrectError))
        }
      }

      "ChecksumCodec returns Right containing checksum which is different to what CRC32ChecksumCalculator returns" when {

        "CRC32ChecksumCalculator returns negative number" should {

          "NOT call ApiKeyRepository" in {
            checksumCalculator.calcChecksumFor(any[String]).returns(-42)
            checksumCodec.decode(any[String]).returns(42L.asRight)

            for {
              _ <- apiKeyValidationService.validateApiKey(publicTenantId_1, apiKey_1)
              _ = verifyZeroInteractions(apiKeyRepository)
            } yield ()
          }

          "return Left containing ApiKeyIncorrectError" in {
            checksumCalculator.calcChecksumFor(any[String]).returns(-42)
            checksumCodec.decode(any[String]).returns(42L.asRight)

            apiKeyValidationService
              .validateApiKey(publicTenantId_1, apiKey_1)
              .asserting(_ shouldBe Left(ApiKeyIncorrectError))
          }
        }

        "CRC32ChecksumCalculator returns NON-negative number" should {

          "NOT call ApiKeyRepository" in {
            checksumCalculator.calcChecksumFor(any[String]).returns(43)
            checksumCodec.decode(any[String]).returns(42L.asRight)

            for {
              _ <- apiKeyValidationService.validateApiKey(publicTenantId_1, apiKey_1)
              _ = verifyZeroInteractions(apiKeyRepository)
            } yield ()
          }

          "return Left containing ApiKeyIncorrectError" in {
            checksumCalculator.calcChecksumFor(any[String]).returns(43)
            checksumCodec.decode(any[String]).returns(42L.asRight)

            apiKeyValidationService
              .validateApiKey(publicTenantId_1, apiKey_1)
              .asserting(_ shouldBe Left(ApiKeyIncorrectError))
          }
        }
      }
    }

    "provided with a key that passes checksum validation, but ApiKeyRepository returns empty Option" should {
      "return Left containing ApiKeyIncorrectError" in {
        val checksum = 42L
        checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
        checksumCodec.decode(any[String]).returns(checksum.asRight)
        apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(None))

        apiKeyValidationService
          .validateApiKey(publicTenantId_1, apiKey_1)
          .asserting(_ shouldBe Left(ApiKeyIncorrectError))
      }
    }

    "provided with a key that passes checksum validation, but the key is expired" should {

      "return Left containing ApiKeyExpiredError" when {

        "expiresAt equals current time" in {
          val checksum   = 42L
          val apiKeyData = apiKeyData_1.copy(expiresAt = nowInstant)

          checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
          checksumCodec.decode(any[String]).returns(checksum.asRight)
          apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(Some(apiKeyData)))

          apiKeyValidationService
            .validateApiKey(publicTenantId_1, apiKey_1)
            .asserting(_ shouldBe Left(ApiKeyExpiredError(nowInstant)))
        }

        "expiresAt is 1 nanosecond in the past" in {
          val checksum   = 42L
          val apiKeyData = apiKeyData_1.copy(expiresAt = nowInstant.minusNanos(1))

          checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
          checksumCodec.decode(any[String]).returns(checksum.asRight)
          apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(Some(apiKeyData)))

          apiKeyValidationService
            .validateApiKey(publicTenantId_1, apiKey_1)
            .asserting(result => result shouldBe Left(ApiKeyExpiredError(nowInstant.minusNanos(1))))
        }

        "expiresAt is 1 second in the past" in {
          val checksum   = 42L
          val apiKeyData = apiKeyData_1.copy(expiresAt = nowInstant.minusSeconds(1))

          checksumCalculator.calcChecksumFor(any[String]).returns(checksum)
          checksumCodec.decode(any[String]).returns(checksum.asRight)
          apiKeyRepository.get(any[TenantId], any[ApiKey]).returns(IO.pure(Some(apiKeyData)))

          apiKeyValidationService
            .validateApiKey(publicTenantId_1, apiKey_1)
            .asserting(result => result shouldBe Left(ApiKeyExpiredError(nowInstant.minusSeconds(1))))
        }
      }
    }
  }

}
