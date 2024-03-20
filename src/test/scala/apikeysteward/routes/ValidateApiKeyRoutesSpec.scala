package apikeysteward.routes

import apikeysteward.base.TestData.{apiKeyData_1, apiKey_1}
import apikeysteward.routes.definitions.Endpoints.ErrorMessages
import apikeysteward.routes.model.{ValidateApiKeyRequest, ValidateApiKeyResponse}
import apikeysteward.services.ApiKeyService
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

class ValidateApiKeyRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  private val apiKeyService = mock[ApiKeyService[String]]
  private val validateApiKeyRoutes: HttpApp[IO] = new ValidateApiKeyRoutes(apiKeyService).allRoutes.orNotFound

  private val testException = new RuntimeException("Test Exception")

  "ValidateApiKeyRoutes on POST /api-key/validation" should {

    val uri = uri"/api-key/validation"
    val requestBody = ValidateApiKeyRequest(apiKey_1)
    val request = Request[IO](method = Method.POST, uri = uri).withEntity(requestBody.asJson)

    "call ApiKeyService" in {
      apiKeyService.validateApiKey(any[String]) returns IO.pure(Right(apiKeyData_1))

      for {
        _ <- validateApiKeyRoutes.run(request)
        _ = verify(apiKeyService).validateApiKey(eqTo(apiKey_1))
      } yield ()
    }

    "return Ok and ApiKeyData when ApiKeyService returns Right" in {
      apiKeyService.validateApiKey(any[String]) returns IO.pure(Right(apiKeyData_1))

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.Ok
        _ <- response
          .as[ValidateApiKeyResponse]
          .asserting(_ shouldBe ValidateApiKeyResponse(apiKeyData_1))
      } yield ()
    }

    "return Forbidden when ApiKeyService returns Left" in {
      apiKeyService.validateApiKey(any[String]) returns IO.pure(Left(ErrorMessages.ValidateApiKeyIncorrect))

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.Forbidden
        _ <- response
          .as[ErrorInfo]
          .asserting(_ shouldBe ErrorInfo.forbiddenErrorDetail(Some(ErrorMessages.ValidateApiKeyIncorrect)))
      } yield ()
    }

    "return Internal Server Error when ApiKeyService returns an exception" in {
      apiKeyService.validateApiKey(any[String]) returns IO.raiseError(testException)

      for {
        response <- validateApiKeyRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response
          .as[ErrorInfo]
          .asserting(_ shouldBe ErrorInfo.internalServerErrorDetail())
      } yield ()
    }
  }

}
