package apikeysteward.services

import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.config.ApiKeyConfig
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError._
import cats.data.NonEmptyChain
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class CreateApiKeyRequestValidatorSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val apiKeyConfig = mock[ApiKeyConfig]

  private val requestValidator = new CreateApiKeyRequestValidator(apiKeyConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyConfig)
    apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes, TimeUnit.MINUTES)
  }

  private val createRequest: CreateApiKeyRequest = CreateApiKeyRequest(
    name = name_1,
    description = description_1,
    ttl = ttlMinutes
  )

  "CreateApiKeyRequestValidator on validateCreateRequest" when {

    "return Right containing CreateApiKeyRequest" when {

      "the request contains ttl value smaller then ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes + 1, TimeUnit.MINUTES)

        requestValidator.validateCreateRequest(createRequest) shouldBe Right(createRequest)
      }

      "the request contains ttl value equal to ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes, TimeUnit.MINUTES)

        requestValidator.validateCreateRequest(createRequest) shouldBe Right(createRequest)
      }
    }

    "return Left containing CreateApiKeyRequestValidatorError" when {

      "the request contains ttl value bigger than ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES)

        requestValidator.validateCreateRequest(createRequest) shouldBe Left(
          NonEmptyChain.one(
            TtlTooLargeError(ttlRequest = ttlMinutes, ttlMax = FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES))
          )
        )
      }
    }
  }
}
