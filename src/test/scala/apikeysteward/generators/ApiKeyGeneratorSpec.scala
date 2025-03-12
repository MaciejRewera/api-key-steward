package apikeysteward.generators

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{
  apiKeyPrefix_1,
  apiKeyTemplate_1,
  publicTemplateId_1,
  randomSectionLength_1
}
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.generators.ApiKeyGenerator.ApiKeyGeneratorError
import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import apikeysteward.model.ApiKey
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.repositories.ApiKeyTemplateRepository
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]
  private val randomStringGenerator = mock[RandomStringGenerator]
  private val checksumCalculator = mock[CRC32ChecksumCalculator]
  private val checksumCodec = mock[ChecksumCodec]

  private val apiKeyGenerator =
    new ApiKeyGenerator(apiKeyTemplateRepository, randomStringGenerator, checksumCalculator, checksumCodec)

  private val encodedValue_42 = "00000" + Base62.CharacterSet(42)

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyTemplateRepository, randomStringGenerator, checksumCalculator, checksumCodec)
  }

  "ApiKeyGenerator on generateApiKey" when {

    "everything works correctly" should {

      "call ApiKeyPrefixProvider, RandomStringGenerator, CRC32ChecksumCalculator and ChecksumCodec" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))
        randomStringGenerator.generate(any[Int]) returns IO.pure(apiKeyRandomSection_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1)

          _ = verify(apiKeyTemplateRepository).getBy(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(randomStringGenerator).generate(eqTo(randomSectionLength_1))
          expectedApiKeyWithPrefix = apiKeyPrefix_1 + apiKeyRandomSection_1
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(expectedApiKeyWithPrefix))
          _ = verify(checksumCodec).encode(eqTo(42L))
        } yield ()
      }

      "return Right containing the newly created Api Key" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))
        randomStringGenerator.generate(any[Int]) returns IO.pure(apiKeyRandomSection_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).asserting {
          _ shouldBe Right(ApiKey(apiKeyPrefix_1 + apiKeyRandomSection_1 + encodedValue_42))
        }
      }
    }

    "ApiKeyTemplateRepository returns empty Option" should {

      "NOT call either RandomStringGenerator, CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(None)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1)

          _ = verifyZeroInteractions(randomStringGenerator, checksumCalculator, checksumCodec)
        } yield ()
      }

      "return Left containing ApiKeyGeneratorError" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(None)

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyGeneratorError(ApiKeyTemplateNotFoundError(publicTemplateId_1.toString))))
      }
    }

    "ApiKeyTemplateRepository returns failed IO" should {

      "NOT call either RandomStringGenerator, CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(randomStringGenerator, checksumCalculator, checksumCodec)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "RandomStringGenerator returns failed IO" should {

      "call ApiKeyPrefixProvider anyway" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))
        randomStringGenerator.generate(any[Int]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).attempt
          _ = verify(apiKeyTemplateRepository).getBy(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
        } yield ()
      }

      "NOT call either CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))
        randomStringGenerator.generate(any[Int]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey(publicTenantId_1, publicTemplateId_1).attempt
          _ = verifyZeroInteractions(checksumCalculator, checksumCodec)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))
        randomStringGenerator.generate(any[Int]) returns IO.raiseError(testException)

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ChecksumCodec returns Left containing ProvidedWithNegativeNumberError" should {
      "return Left containing ApiKeyGeneratorError" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))
        randomStringGenerator.generate(any[Int]) returns IO.pure(apiKeyRandomSection_1)
        checksumCalculator.calcChecksumFor(any[String]) returns -42L
        checksumCodec.encode(any[Long]) returns Left(ProvidedWithNegativeNumberError(-42L))

        apiKeyGenerator
          .generateApiKey(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyGeneratorError(ProvidedWithNegativeNumberError(-42L))))
      }
    }
  }

}
