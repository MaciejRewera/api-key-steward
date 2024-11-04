package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantIdStr_1, publicTenantId_1}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.apikeytemplate._
import apikeysteward.services.ApiKeyTemplateService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.{Header, Headers, HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.ci.{CIString, CIStringSyntax}

import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class AdminApiKeyTemplateRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val apiKeyTemplateService = mock[ApiKeyTemplateService]

  private val adminRoutes: HttpApp[IO] =
    new AdminApiKeyTemplateRoutes(jwtAuthorizer, apiKeyTemplateService).allRoutes.orNotFound

  private val tenantIdHeaderName: CIString = ci"ApiKeySteward-TenantId"
  private val tenantIdHeader = Header.Raw(tenantIdHeaderName, publicTenantIdStr_1)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, apiKeyTemplateService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO], requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminRoutes, jwtAuthorizer, apiKeyTemplateService)(request, requiredPermissions)

  "AdminApiKeyTemplateRoutes on POST /admin/templates" when {

    val uri = Uri.unsafeFromString("/admin/templates")
    val requestBody = CreateApiKeyTemplateRequest(
      name = apiKeyTemplateName_1,
      description = apiKeyTemplateDescription_1,
      isDefault = false,
      apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but TenantId request header is NOT a UUID" should {

      val requestWithIncorrectHeader = request.putHeaders(Header.Raw(tenantIdHeaderName, "this-is-not-a-valid-uuid"))
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId""")
      )

      "return Bad Request" in authorizedFixture {
        for {
          response <- adminRoutes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty name" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with name containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with name longer than 280 characters" should {

        val nameThatIsTooLong = List.fill(281)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 280, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with description containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(description = Some("  \n   \n\n ")))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"Invalid value for: body (expected description to pass validation, but got: Some())")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with description longer than 500 characters" should {

        val descriptionThatIsTooLong = List.fill(501)("A").mkString
        val requestWithLongDescription =
          request.withEntity(requestBody.copy(description = Some(descriptionThatIsTooLong)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"Invalid value for: body (expected description to pass validation, but got: Some($descriptionThatIsTooLong))"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongDescription)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongDescription)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with negative apiKeyMaxExpiryPeriod value" should {

        val requestWithNegativeMaxExpiryPeriod =
          request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration(-1, TimeUnit.SECONDS)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected apiKeyMaxExpiryPeriod to pass validation, but got: -1 seconds)")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with MinusInf apiKeyMaxExpiryPeriod value" should {

        val requestWithNegativeMaxExpiryPeriod =
          request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration.MinusInf))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (expected apiKeyMaxExpiryPeriod to pass validation, but got: Duration.MinusInf)"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with Undefined apiKeyMaxExpiryPeriod value" should {

        val requestWithNegativeMaxExpiryPeriod =
          request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration.Undefined))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (expected apiKeyMaxExpiryPeriod to pass validation, but got: Duration.Undefined)"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateService" when {

        "provided with finite apiKeyMaxExpiryPeriod value" in authorizedFixture {
          apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
            apiKeyTemplate_1.asRight
          )

          for {
            _ <- adminRoutes.run(request)
            _ = verify(apiKeyTemplateService).createApiKeyTemplate(eqTo(publicTenantId_1), eqTo(requestBody))
          } yield ()
        }

        "provided with Inf apiKeyMaxExpiryPeriod value" in authorizedFixture {
          val apiKeyTemplate = apiKeyTemplate_1.copy(apiKeyMaxExpiryPeriod = Duration.Inf)
          apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
            apiKeyTemplate.asRight
          )

          val requestBodyWithInfiniteExpiryPeriod = requestBody.copy(apiKeyMaxExpiryPeriod = Duration.Inf)
          val requestWithInfiniteExpiryPeriod = request.withEntity(requestBodyWithInfiniteExpiryPeriod)

          for {
            _ <- adminRoutes.run(requestWithInfiniteExpiryPeriod)
            _ = verify(apiKeyTemplateService).createApiKeyTemplate(
              eqTo(publicTenantId_1),
              eqTo(requestBodyWithInfiniteExpiryPeriod)
            )
          } yield ()
        }
      }

      "return successful value returned by ApiKeyTemplateService" when {

        "provided with description" in authorizedFixture {
          apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
            apiKeyTemplate_1.asRight
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyTemplateResponse]
              .asserting(_ shouldBe CreateApiKeyTemplateResponse(apiKeyTemplate_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val apiKeyTemplateWithoutDescription = apiKeyTemplate_1.copy(description = None)
          apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
            apiKeyTemplateWithoutDescription.asRight
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyTemplateResponse]
              .asserting(_ shouldBe CreateApiKeyTemplateResponse(apiKeyTemplateWithoutDescription))
          } yield ()
        }

        "provided with Inf apiKeyMaxExpiryPeriod value" in authorizedFixture {
          val apiKeyTemplate = apiKeyTemplate_1.copy(apiKeyMaxExpiryPeriod = Duration.Inf)
          apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
            apiKeyTemplate.asRight
          )

          val requestWithInfiniteExpiryPeriod =
            request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration.Inf))

          for {
            response <- adminRoutes.run(requestWithInfiniteExpiryPeriod)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyTemplateResponse]
              .asserting(_ shouldBe CreateApiKeyTemplateResponse(apiKeyTemplate))
          } yield ()
        }
      }

      "return Internal Server Error when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplateAlreadyExistsError" in authorizedFixture {
        apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
          Left(ApiKeyTemplateAlreadyExistsError(publicTemplateIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplateInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
          Left(ApiKeyTemplateInsertionErrorImpl(testSqlException))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO.pure(
          Left(ReferencedTenantDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplate.ReferencedTenantNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.createApiKeyTemplate(any[TenantId], any[CreateApiKeyTemplateRequest]) returns IO
          .raiseError(
            testException
          )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on PUT /admin/templates/{apiKeyTemplateId}" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1")
    val requestBody = UpdateApiKeyTemplateRequest(name = apiKeyTemplateName_1, description = apiKeyTemplateDescription_1, isDefault = true, apiKeyMaxExpiryPeriod = Duration(17, TimeUnit.DAYS))

    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with apiKeyTemplateId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid")
        val requestWithIncorrectApiKeyTemplateId =
          Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter templateId"))
            )
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty name" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with name containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with name longer than 280 characters" should {

        val nameThatIsTooLong = List.fill(281)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 280, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with description containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(description = Some("  \n   \n\n ")))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"Invalid value for: body (expected description to pass validation, but got: Some())")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with description longer than 500 characters" should {

        val descriptionThatIsTooLong = List.fill(501)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(description = Some(descriptionThatIsTooLong)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"Invalid value for: body (expected description to pass validation, but got: Some($descriptionThatIsTooLong))"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with negative apiKeyMaxExpiryPeriod value" should {

        val requestWithNegativeMaxExpiryPeriod =
          request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration(-1, TimeUnit.SECONDS)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected apiKeyMaxExpiryPeriod to pass validation, but got: -1 seconds)")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with MinusInf apiKeyMaxExpiryPeriod value" should {

        val requestWithNegativeMaxExpiryPeriod =
          request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration.MinusInf))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (expected apiKeyMaxExpiryPeriod to pass validation, but got: Duration.MinusInf)"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }

      "request body is provided with Undefined apiKeyMaxExpiryPeriod value" should {

        val requestWithNegativeMaxExpiryPeriod =
          request.withEntity(requestBody.copy(apiKeyMaxExpiryPeriod = Duration.Undefined))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (expected apiKeyMaxExpiryPeriod to pass validation, but got: Duration.Undefined)"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeMaxExpiryPeriod)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateService" when {

        "provided with finite apiKeyMaxExpiryPeriod value" in authorizedFixture {
          apiKeyTemplateService.updateApiKeyTemplate(any[ApiKeyTemplateId], any[UpdateApiKeyTemplateRequest]) returns
            IO.pure(apiKeyTemplate_1.asRight)

          for {
            _ <- adminRoutes.run(request)
            _ = verify(apiKeyTemplateService).updateApiKeyTemplate(eqTo(publicTemplateId_1), eqTo(requestBody))
          } yield ()
        }

        "provided with Inf apiKeyMaxExpiryPeriod value" in authorizedFixture {
          val apiKeyTemplate = apiKeyTemplate_1.copy(apiKeyMaxExpiryPeriod = Duration.Inf)
          apiKeyTemplateService.updateApiKeyTemplate(any[ApiKeyTemplateId], any[UpdateApiKeyTemplateRequest]) returns
            IO.pure(apiKeyTemplate.asRight)

          val requestBodyWithInfiniteExpiryPeriod = requestBody.copy(apiKeyMaxExpiryPeriod = Duration.Inf)
          val requestWithInfiniteExpiryPeriod = request.withEntity(requestBodyWithInfiniteExpiryPeriod)

          for {
            _ <- adminRoutes.run(requestWithInfiniteExpiryPeriod)
            _ = verify(apiKeyTemplateService).updateApiKeyTemplate(
              eqTo(publicTemplateId_1),
              eqTo(requestBodyWithInfiniteExpiryPeriod)
            )
          } yield ()
        }
      }

      "return successful value returned by ApiKeyTemplateService" when {

        "provided with finite apiKeyMaxExpiryPeriod value" in authorizedFixture {
          apiKeyTemplateService.updateApiKeyTemplate(any[ApiKeyTemplateId], any[UpdateApiKeyTemplateRequest]) returns
            IO.pure(apiKeyTemplate_1.asRight)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[UpdateApiKeyTemplateResponse]
              .asserting(_ shouldBe UpdateApiKeyTemplateResponse(apiKeyTemplate_1))
          } yield ()
        }

        "provided with Inf apiKeyMaxExpiryPeriod value" in authorizedFixture {
          val apiKeyTemplate = apiKeyTemplate_1.copy(apiKeyMaxExpiryPeriod = Duration.Inf)
          apiKeyTemplateService.updateApiKeyTemplate(any[ApiKeyTemplateId], any[UpdateApiKeyTemplateRequest]) returns
            IO.pure(apiKeyTemplate.asRight)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[UpdateApiKeyTemplateResponse]
              .asserting(_ shouldBe UpdateApiKeyTemplateResponse(apiKeyTemplate))
          } yield ()
        }
      }

      "return Not Found when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplateNotFoundError" in authorizedFixture {
        apiKeyTemplateService.updateApiKeyTemplate(any[ApiKeyTemplateId], any[UpdateApiKeyTemplateRequest]) returns
          IO.pure(Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.updateApiKeyTemplate(any[ApiKeyTemplateId], any[UpdateApiKeyTemplateRequest]) returns
          IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on DELETE /admin/templates/{apiKeyTemplateId}" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with apiKeyTemplateId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid")
        val requestWithIncorrectApiKeyTemplateId =
          Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter templateId"))
            )
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.deleteApiKeyTemplate(any[UUID]) returns IO.pure(apiKeyTemplate_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(apiKeyTemplateService).deleteApiKeyTemplate(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return successful value returned by ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.deleteApiKeyTemplate(any[UUID]) returns IO.pure(apiKeyTemplate_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeleteApiKeyTemplateResponse]
            .asserting(_ shouldBe DeleteApiKeyTemplateResponse(apiKeyTemplate_1))
        } yield ()
      }

      "return Not Found when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplateNotFoundError" in authorizedFixture {
        apiKeyTemplateService.deleteApiKeyTemplate(any[UUID]) returns IO.pure(
          Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.deleteApiKeyTemplate(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on GET /admin/templates/{apiKeyTemplateId}" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "provided with apiKeyTemplateId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid")
        val requestWithIncorrectApiKeyTemplateId =
          Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter templateId"))
            )
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.getBy(any[UUID]) returns IO.pure(apiKeyTemplate_1.some)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(apiKeyTemplateService).getBy(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return successful value returned by ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.getBy(any[UUID]) returns IO.pure(apiKeyTemplate_1.some)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSingleApiKeyTemplateResponse]
            .asserting(_ shouldBe GetSingleApiKeyTemplateResponse(apiKeyTemplate_1))
        } yield ()
      }

      "return Not Found when ApiKeyTemplateService returns empty Option" in authorizedFixture {
        apiKeyTemplateService.getBy(any[UUID]) returns IO.pure(none)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKeyTemplate.ApiKeyTemplateNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.getBy(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on GET /admin/templates" when {

    val uri = Uri.unsafeFromString(s"/admin/templates")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but TenantId request header is NOT a UUID" should {

      val requestWithIncorrectHeader = request.putHeaders(Header.Raw(tenantIdHeaderName, "this-is-not-a-valid-uuid"))
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId""")
      )

      "return Bad Request" in authorizedFixture {
        for {
          response <- adminRoutes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(apiKeyTemplateService).getAllForTenant(eqTo(UUID.fromString(tenantIdHeader.value)))
        } yield ()
      }

      "return successful value returned by ApiKeyTemplateService" when {

        "ApiKeyTemplateService returns an empty List" in authorizedFixture {
          apiKeyTemplateService.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeyTemplatesResponse]
              .asserting(_ shouldBe GetMultipleApiKeyTemplatesResponse(templates = List.empty))
          } yield ()
        }

        "ApiKeyTemplateService returns a List with several elements" in authorizedFixture {
          apiKeyTemplateService.getAllForTenant(any[TenantId]) returns IO.pure(
            List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeyTemplatesResponse]
              .asserting(
                _ shouldBe GetMultipleApiKeyTemplatesResponse(templates =
                  List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
                )
              )
          } yield ()
        }
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
