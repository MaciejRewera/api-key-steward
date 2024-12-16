package apikeysteward.services

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.config.ApiKeyConfig
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.TtlTooLargeError
import cats.data.NonEmptyChain
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import scala.concurrent.duration.Duration

class CreateApiKeyRequestValidatorSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val apiKeyConfig = mock[ApiKeyConfig]

  private val requestValidator = new CreateApiKeyRequestValidator(apiKeyConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyConfig)
    apiKeyConfig.ttlMax returns ttl
  }

  private val createRequest: CreateApiKeyRequest = CreateApiKeyRequest(
    name = name_1,
    description = description_1,
    templateId = publicTemplateId_1,
    ttl = ttl,
    permissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
  )

  "CreateApiKeyRequestValidator on validateCreateRequest" when {

    "return Right containing CreateApiKeyRequest" when {

      "the request contains ttl value smaller then ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns ttl.plus(Duration(1, ttl.unit))

        requestValidator.validateCreateRequest(createRequest) shouldBe Right(createRequest)
      }

      "the request contains ttl value equal to ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns ttl

        requestValidator.validateCreateRequest(createRequest) shouldBe Right(createRequest)
      }
    }

    "return Left containing CreateApiKeyRequestValidatorError" when {

      "the request contains ttl value bigger than ApiKeyConfig.ttlMax" in {
        val ttlMax = ttl.minus(Duration(1, ttl.unit))
        apiKeyConfig.ttlMax returns ttlMax

        requestValidator.validateCreateRequest(createRequest) shouldBe Left(
          NonEmptyChain.one(
            TtlTooLargeError(ttlRequest = ttl, ttlMax = ttlMax)
          )
        )
      }
    }
  }
}
