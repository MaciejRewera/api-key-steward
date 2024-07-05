package apikeysteward.services

import apikeysteward.base.TestData._
import apikeysteward.config.ApiKeyConfig
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.NotAllowedScopesProvidedError
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CreateApiKeyRequestValidatorSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private val apiKeyConfig = mock[ApiKeyConfig]

  private val requestValidator = new CreateApiKeyRequestValidator(apiKeyConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyConfig)
  }

  private def buildRequest(scopes: List[String]): CreateApiKeyRequest = CreateApiKeyRequest(
    name = name,
    description = description,
    ttl = ttlSeconds,
    scopes = scopes
  )

  "CreateApiKeyRequestValidator on validateRequest" should {

    "return Right containing CreateApiKeyRequest" when {

      "ApiKeyConfig returns empty Set and the request contains no scopes" in {
        apiKeyConfig.allowedScopes returns Set.empty[String]
        val request = buildRequest(List.empty)

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig returns non-empty Set and the request contains scopes forming a subset of the allowed scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List(scopeRead_1, scopeWrite_1))

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig returns non-empty Set and the request contains all scopes from the allowed scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2))

        requestValidator.validateRequest(request) shouldBe Right(request)
      }

      "ApiKeyConfig returns non-empty Set and the request contains repeated scopes forming a subset of the allowed scopes" in {
        apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
        val request = buildRequest(List(scopeRead_1, scopeRead_1, scopeRead_1, scopeWrite_1, scopeWrite_1))

        requestValidator.validateRequest(request) shouldBe Right(request)
      }
    }

    "return Left containing CreateApiKeyRequestValidatorError" when {

      "ApiKeyConfig returns empty Set" when {

        "the request contains a single scope" in {
          apiKeyConfig.allowedScopes returns Set.empty[String]
          val request = buildRequest(List(scopeRead_1))

          requestValidator.validateRequest(request) shouldBe Left(NotAllowedScopesProvidedError(Set(scopeRead_1)))
        }

        "the request contains multiple scopes" in {
          apiKeyConfig.allowedScopes returns Set.empty[String]
          val request = buildRequest(List(scopeRead_1, scopeWrite_1, scopeWrite_2))

          requestValidator.validateRequest(request) shouldBe Left(
            NotAllowedScopesProvidedError(Set(scopeRead_1, scopeWrite_1, scopeWrite_2))
          )
        }

        "the request contains repeated scopes" in {
          apiKeyConfig.allowedScopes returns Set.empty[String]
          val request = buildRequest(List(scopeRead_1, scopeWrite_1, scopeWrite_1))

          requestValidator.validateRequest(request) shouldBe Left(
            NotAllowedScopesProvidedError(Set(scopeRead_1, scopeWrite_1))
          )
        }
      }

      "ApiKeyConfig returns non-empty Set" when {

        "the request contains a single scope not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_3))

          requestValidator.validateRequest(request) shouldBe Left(NotAllowedScopesProvidedError(Set(scopeRead_3)))
        }

        "the request contains multiple scopes not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_3, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NotAllowedScopesProvidedError(Set(scopeRead_3, scopeWrite_3))
          )
        }

        "the request contains repeated scopes not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_3, scopeRead_3, scopeRead_3, scopeWrite_3, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NotAllowedScopesProvidedError(Set(scopeRead_3, scopeWrite_3))
          )
        }

        "the request contains multiple scopes, but one of them is not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request = buildRequest(List(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(NotAllowedScopesProvidedError(Set(scopeWrite_3)))
        }

        "the request contains multiple scopes, but some of them are not present in the allowed scopes" in {
          apiKeyConfig.allowedScopes returns Set(scopeRead_1, scopeRead_2, scopeWrite_1, scopeWrite_2)
          val request =
            buildRequest(List(scopeRead_1, scopeRead_2, scopeRead_3, scopeWrite_1, scopeWrite_2, scopeWrite_3))

          requestValidator.validateRequest(request) shouldBe Left(
            NotAllowedScopesProvidedError(Set(scopeRead_3, scopeWrite_3))
          )
        }
      }
    }

  }
}
