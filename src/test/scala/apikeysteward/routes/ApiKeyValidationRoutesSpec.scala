package apikeysteward.routes

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData.{apiKeyData_1, apiKey_1}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenant_1}
import apikeysteward.model.ApiKey
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.apikey.{ValidateApiKeyRequest, ValidateApiKeyResponse}
import apikeysteward.services.ApiKeyValidationService
import apikeysteward.services.ApiKeyValidationService.ApiKeyValidationError.{ApiKeyExpiredError, ApiKeyIncorrectError}
import cats.data.EitherT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{HttpApp, Method, Request, Status}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
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

  private val activeTenantVerifier = mock[ActiveTenantVerifier]
  private val apiKeyService        = mock[ApiKeyValidationService]

  private val validateApiKeyRoutes: HttpApp[IO] =
    new ApiKeyValidationRoutes(activeTenantVerifier, apiKeyService).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(activeTenantVerifier, apiKeyService)
  }

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    runCommonTenantIdHeaderTests(validateApiKeyRoutes, List(apiKeyService))(request)

  "ValidateApiKeyRoutes on POST /api-keys/validation" when {

    val uri         = uri"/api-keys/validation"
    val requestBody = ValidateApiKeyRequest(apiKey_1.value)
    val request     = Request[IO](method = Method.POST, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonTenantIdHeaderTests(request)

    "everything works correctly" should {

      "call ActiveTenantVerifier and ApiKeyService" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        apiKeyService.validateApiKey(any[TenantId], any[ApiKey]).returns(IO.pure(Right(apiKeyData_1)))

        for {
          _ <- validateApiKeyRoutes.run(request)
          _ = verify(apiKeyService).validateApiKey(eqTo(publicTenantId_1), eqTo(apiKey_1))
        } yield ()
      }

      "return Ok and ApiKeyData when ApiKeyService returns Right" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        apiKeyService.validateApiKey(any[TenantId], any[ApiKey]).returns(IO.pure(Right(apiKeyData_1)))

        for {
          response <- validateApiKeyRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[ValidateApiKeyResponse]
            .asserting(_ shouldBe ValidateApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Forbidden when ApiKeyService returns Left containing ApiKeyIncorrectError" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        apiKeyService.validateApiKey(any[TenantId], any[ApiKey]).returns(IO.pure(Left(ApiKeyIncorrectError)))

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
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        apiKeyService
          .validateApiKey(any[TenantId], any[ApiKey])
          .returns(
            IO.pure(
              Left(ApiKeyExpiredError(nowInstant.minusSeconds(1).plusMillis(123).plusNanos(456)))
            )
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
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        apiKeyService.validateApiKey(any[TenantId], any[ApiKey]).returns(IO.raiseError(testException))

        for {
          response <- validateApiKeyRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }

    "ActiveTenantVerifier returns Left containing ErrorInfo" should {

      val tenantIsDeactivatedError = ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))

      "NOT call ApiKeyService" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.leftT(tenantIsDeactivatedError))

        for {
          _ <- validateApiKeyRoutes.run(request)
          _ = verifyZeroInteractions(apiKeyService)
        } yield ()
      }

      "return Bad Request" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.leftT(tenantIsDeactivatedError))

        for {
          response <- validateApiKeyRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe tenantIsDeactivatedError)
        } yield ()
      }
    }
  }

}
