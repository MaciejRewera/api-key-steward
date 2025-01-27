package apikeysteward.generators

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{apiKeyPrefix_1, apiKeyTemplate_1, publicTemplateId_1}
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.repositories.ApiKeyTemplateRepository
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyPrefixProviderSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]

  private val apiKeyPrefixProvider = new ApiKeyPrefixProvider(apiKeyTemplateRepository)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyTemplateRepository)
  }

  private val testException = new RuntimeException("Test Exception")

  "ApiKeyPrefixProvider on fetchPrefix" when {

    "everything works correctly" should {

      "call ApiKeyTemplateRepository" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))

        for {
          _ <- apiKeyPrefixProvider.fetchPrefix(publicTenantId_1, publicTemplateId_1)

          _ = verify(apiKeyTemplateRepository).getBy(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Right containing prefix from fetched Template" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))

        apiKeyPrefixProvider
          .fetchPrefix(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Right(apiKeyPrefix_1))
      }
    }

    "ApiKeyTemplateRepository returns empty Option" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.pure(None)

        apiKeyPrefixProvider
          .fetchPrefix(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateId_1.toString)))
      }
    }

    "ApiKeyTemplateRepository returns exception" should {

      "return failed IO containing this exception" in {
        apiKeyTemplateRepository.getBy(any[TenantId], any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        apiKeyPrefixProvider
          .fetchPrefix(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
