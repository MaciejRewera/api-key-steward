package apikeysteward.routes

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData.{apiKeyData_1, apiKey_1}
import apikeysteward.model.ApiKey
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.apikey.{ValidateApiKeyRequest, ValidateApiKeyResponse}
import apikeysteward.services.ApiKeyValidationService
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Headers, HttpApp, Method, Request, Status}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyValidationRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase
    with FixedClock {

  private val apiKeyService = mock[ApiKeyValidationService]
  private val validateApiKeyRoutes: HttpApp[IO] = new ApiKeyValidationRoutes(apiKeyService).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(apiKeyService)
  }

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    runCommonTenantIdHeaderTests(validateApiKeyRoutes, List(apiKeyService))(request)

  "ValidateApiKeyRoutes on POST /api-keys/validation" should {

    val uri = uri"/api-keys/validation"
    val requestBody = ValidateApiKeyRequest(apiKey_1.value)
    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(tenantIdHeader))
      .withEntity(requestBody.asJson)

    runCommonTenantIdHeaderTests(request)

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
        Left(ApiKeyExpiredError(nowInstant.minusSeconds(1).plusMillis(123).plusNanos(456)))
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
