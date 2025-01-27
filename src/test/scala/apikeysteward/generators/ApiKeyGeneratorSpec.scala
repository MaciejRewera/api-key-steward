package apikeysteward.generators

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.generators.ApiKeyGenerator.ApiKeyGeneratorError
import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import apikeysteward.model.ApiKey
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val apiKeyPrefixProvider = mock[ApiKeyPrefixProvider]
  private val randomStringGenerator = mock[RandomStringGenerator]
  private val checksumCalculator = mock[CRC32ChecksumCalculator]
  private val checksumCodec = mock[ChecksumCodec]

  private val apiKeyGenerator =
    new ApiKeyGenerator(apiKeyPrefixProvider, randomStringGenerator, checksumCalculator, checksumCodec)

  private val encodedValue_42 = "00000" + Base62.CharacterSet(42)

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyPrefixProvider, randomStringGenerator, checksumCalculator, checksumCodec)
  }

  "ApiKeyGenerator on generateApiKey" when {

    "everything works correctly" should {

      "call ApiKeyPrefixProvider, RandomStringGenerator, CRC32ChecksumCalculator and ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Right(apiKeyPrefix))
        randomStringGenerator.generate returns IO.pure(apiKeyRandomSection_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1)

          _ = verify(apiKeyPrefixProvider).fetchPrefix(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(randomStringGenerator).generate
          expectedApiKeyWithPrefix = apiKeyPrefix + apiKeyRandomSection_1
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(expectedApiKeyWithPrefix))
          _ = verify(checksumCodec).encode(eqTo(42L))
        } yield ()
      }

      "return Right containing the newly created Api Key" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Right(apiKeyPrefix))
        randomStringGenerator.generate returns IO.pure(apiKeyRandomSection_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).asserting {
          _ shouldBe Right(ApiKey(apiKeyPrefix + apiKeyRandomSection_1 + encodedValue_42))
        }
      }
    }

    "ApiKeyPrefixProvider returns Left containing error" should {

      "NOT call either RandomStringGenerator, CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(
          Left(ApiKeyTemplateNotFoundError(publicTemplateId_1.toString))
        )

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1)

          _ = verifyZeroInteractions(randomStringGenerator, checksumCalculator, checksumCodec)
        } yield ()
      }

      "return Left containing ApiKeyGeneratorError" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(
          Left(ApiKeyTemplateNotFoundError(publicTemplateId_1.toString))
        )

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyGeneratorError(ApiKeyTemplateNotFoundError(publicTemplateId_1.toString))))
      }
    }

    "ApiKeyPrefixProvider returns failed IO" should {

      "NOT call either RandomStringGenerator, CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(randomStringGenerator, checksumCalculator, checksumCodec)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "RandomStringGenerator returns failed IO" should {

      "call ApiKeyPrefixProvider anyway" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Right(apiKeyPrefix))
        randomStringGenerator.generate returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).attempt
          _ = verify(apiKeyPrefixProvider).fetchPrefix(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
        } yield ()
      }

      "NOT call either CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Right(apiKeyPrefix))
        randomStringGenerator.generate returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).attempt
          _ = verifyZeroInteractions(checksumCalculator, checksumCodec)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Right(apiKeyPrefix))
        randomStringGenerator.generate returns IO.raiseError(testException)

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ChecksumCodec returns Left containing ProvidedWithNegativeNumberError" should {
      "return Left containing ApiKeyGeneratorError" in {
        apiKeyPrefixProvider.fetchPrefix(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Right(apiKeyPrefix))
        randomStringGenerator.generate returns IO.pure(apiKeyRandomSection_1)
        checksumCalculator.calcChecksumFor(any[String]) returns -42L
        checksumCodec.encode(any[Long]) returns Left(ProvidedWithNegativeNumberError(-42L))

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyGeneratorError(ProvidedWithNegativeNumberError(-42L))))
      }
    }
  }

}
