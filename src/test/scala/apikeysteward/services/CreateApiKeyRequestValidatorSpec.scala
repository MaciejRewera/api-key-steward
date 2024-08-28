package apikeysteward.services

import apikeysteward.base.TestData._
import apikeysteward.config.ApiKeyConfig
import apikeysteward.routes.model.CreateApiKeyRequest
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

  private def buildRequest(scopes: List[String]): CreateApiKeyRequest = CreateApiKeyRequest(
    name = name,
    description = description,
    ttl = ttlMinutes,
    scopes = scopes
  )

  "CreateApiKeyRequestValidator on validateRequest" should {

    "return Right containing CreateApiKeyRequest" when {

      "ApiKeyConfig.allowedScopes returns empty Set and the request contains no scopes" in {
        apiKeyConfig.allowedScopes returns Set.empty[String]
        val request = buildRequest(List.empty)

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig.allowedScopes returns non-empty Set and the request contains no scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List.empty)

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig.allowedScopes returns non-empty Set and the request contains scopes forming a subset of the allowed scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List(scopeRead_1, scopeWrite_1))

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig.allowedScopes returns non-empty Set and the request contains all scopes from the allowed scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2))

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig.allowedScopes returns non-empty Set and the request contains repeated scopes forming a subset of the allowed scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List(scopeRead_1, scopeRead_1, scopeRead_1, scopeWrite_1, scopeWrite_1))

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "the request contains ttl value smaller then ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes + 1, TimeUnit.MINUTES)
        val request = buildRequest(List.empty)

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "the request contains ttl value equal to ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes, TimeUnit.MINUTES)
        val request = buildRequest(List.empty)

        requestValidator.validateRequest(request) shouldBe Right(request)
      }
    }

    "return Left containing CreateApiKeyRequestValidatorError" when {

      "ApiKeyConfig.allowedScopes returns empty Set" when {

        "the request contains a single scope" in {
          apiKeyConfig.allowedScopes returns Set.empty[String]
          val request = buildRequest(List(scopeRead_1))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_1)))
          )
        }

        "the request contains multiple scopes" in {
          apiKeyConfig.allowedScopes returns Set.empty[String]
          val request = buildRequest(List(scopeRead_1, scopeWrite_1, scopeWrite_2))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_1, scopeWrite_1, scopeWrite_2)))
          )
        }

        "the request contains repeated scopes" in {
          apiKeyConfig.allowedScopes returns Set.empty[String]
          val request = buildRequest(List(scopeRead_1, scopeWrite_1, scopeWrite_1))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_1, scopeWrite_1)))
          )
        }
      }

      "ApiKeyConfig.allowedScopes returns non-empty Set" when {

        "the request contains a single scope not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_3)))
          )
        }

        "the request contains multiple scopes not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_3, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_3, scopeWrite_3)))
          )
        }

        "the request contains repeated scopes not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_3, scopeRead_3, scopeRead_3, scopeWrite_3, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_3, scopeWrite_3)))
          )
        }

        "the request contains multiple scopes, but one of them is not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeWrite_3)))
          )
        }

        "the request contains multiple scopes, but some of them are not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request =
            buildRequest(List(scopeRead_1, scopeRead_2, scopeRead_3, scopeWrite_1, scopeWrite_2, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_3, scopeWrite_3)))
          )
        }
      }

      "the request contains ttl value bigger than ApiKeyConfig.ttlMax" in {
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES)
        val request = buildRequest(List.empty)

        requestValidator.validateRequest(request) shouldBe Left(
          NonEmptyChain.one(
            TtlTooLargeError(ttlRequest = ttlMinutes, ttlMax = FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES))
          )
        )
      }
    }

    "return Left containing multiple CreateApiKeyRequestValidatorErrors" when {

      "both scopes and ttl validation fail" in {
        apiKeyConfig.allowedScopes returns Set.empty[String]
        apiKeyConfig.ttlMax returns FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES)
        val request = buildRequest(List(scopeRead_1))

        val result = requestValidator.validateRequest(request)

        result.isLeft shouldBe true
        result.left.value.length shouldBe 2
        result.left.value.iterator.toSeq should contain theSameElementsAs Seq(
          NotAllowedScopesProvidedError(Set(scopeRead_1)),
          TtlTooLargeError(ttlRequest = ttlMinutes, ttlMax = FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES))
        )
      }
    }

  }
}
