package apikeysteward.routes

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData.{apiKeyData_1, apiKeyRandomSection_1, apiKey_1}
import apikeysteward.model.ApiKey
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.{ValidateApiKeyRequest, ValidateApiKeyResponse}
import apikeysteward.services.ApiKeyValidationService
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{HttpApp, Method, Request, Status}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, verify}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyValidationRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with FixedClock {

  private val apiKeyService = mock[ApiKeyValidationService]
  private val validateApiKeyRoutes: HttpApp[IO] = new ApiKeyValidationRoutes(apiKeyService).allRoutes.orNotFound

  private val testException = new RuntimeException("Test Exception")

  "ValidateApiKeyRoutes on POST /api-key/validation" should {

    val uri = uri"/api-key/validation"
    val requestBody = ValidateApiKeyRequest(apiKey_1.value)
    val request = Request[IO](method = Method.POST, uri = uri).withEntity(requestBody.asJson)

    "call ApiKeyService" in {
      apiKeyService.validateApiKey(any[ApiKey]) returns IO.pure(Right(apiKeyData_1))

      for {
        _ <- validateApiKeyRoutes.run(request)
        _ = verify(apiKeyService).validateApiKey(eqTo(apiKey_1))
      } yield ()
    }

    "return Ok and ApiKeyData when ApiKeyService returns Right" in {
      apiKeyService.validateApiKey(any[ApiKey]) returns IO.pure(Right(apiKeyData_1))

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.Ok
        _ <- response
          .as[ValidateApiKeyResponse]
          .asserting(_ shouldBe ValidateApiKeyResponse(apiKeyData_1))
      } yield ()
    }

    "return Forbidden when ApiKeyService returns Left containing ApiKeyIncorrectError" in {
      apiKeyService.validateApiKey(any[ApiKey]) returns IO.pure(Left(ApiKeyIncorrectError))

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.Forbidden
        _ <- response
          .as[ErrorInfo]
          .asserting(
            _ shouldBe ErrorInfo.forbiddenErrorInfo(Some(ApiErrorMessages.ValidateApiKey.ValidateApiKeyIncorrect))
          )
      } yield ()
    }

    "return Forbidden when ApiKeyService returns Left containing ApiKeyExpiredError" in {
      apiKeyService.validateApiKey(any[ApiKey]) returns IO.pure(
        Left(ApiKeyExpiredError(now.minusSeconds(1).plusMillis(123).plusNanos(456)))
      )

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.Forbidden
        _ <- response
          .as[ErrorInfo]
          .asserting { resp =>
            resp.error shouldBe "Access Denied"
            resp.errorDetail shouldBe Some("Provided API Key is expired since: 2024-02-15T12:34:55.123000456Z.")
          }
      } yield ()
    }

    "return Internal Server Error when ApiKeyService returns an exception" in {
      apiKeyService.validateApiKey(any[ApiKey]) returns IO.raiseError(testException)

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response
          .as[ErrorInfo]
          .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
      } yield ()
    }
  }

}
