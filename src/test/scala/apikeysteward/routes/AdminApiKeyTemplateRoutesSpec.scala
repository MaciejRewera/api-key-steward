package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantIdStr_1, publicTenantId_1}
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.{
  ApiKeyTemplatesPermissionsInsertionError,
  ApiKeyTemplatesPermissionsNotFoundError
}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.apikeytemplate._
import apikeysteward.routes.model.admin.apikeytemplatespermissions.CreateApiKeyTemplatesPermissionsRequest
import apikeysteward.routes.model.admin.apikeytemplatesusers.CreateApiKeyTemplatesUsersRequest
import apikeysteward.routes.model.admin.permission.GetMultiplePermissionsResponse
import apikeysteward.routes.model.admin.user.GetMultipleUsersResponse
import apikeysteward.services.{ApiKeyTemplateService, PermissionService, UserService}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import fs2.Stream
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
  private val permissionService = mock[PermissionService]
  private val userService = mock[UserService]

  private val adminRoutes: HttpApp[IO] =
    new AdminApiKeyTemplateRoutes(
      jwtAuthorizer,
      apiKeyTemplateService,
      permissionService,
      userService
    ).allRoutes.orNotFound

  private val tenantIdHeaderName: CIString = ci"ApiKeySteward-TenantId"
  private val tenantIdHeader = Header.Raw(tenantIdHeaderName, publicTenantIdStr_1)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, apiKeyTemplateService, permissionService, userService)
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
      apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1,
      apiKeyPrefix = apiKeyPrefix_1
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

      "request body is provided with empty apiKeyPrefix" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(apiKeyPrefix = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected apiKeyPrefix to have length greater than or equal to 1, but got: "")"""
          )
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

      "request body is provided with apiKeyPrefix containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(apiKeyPrefix = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected apiKeyPrefix to have length greater than or equal to 1, but got: "")"""
          )
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

      "request body is provided with apiKeyPrefix longer than 120 characters" should {

        val apiKeyPrefixThatIsTooLong = List.fill(121)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(apiKeyPrefix = apiKeyPrefixThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected apiKeyPrefix to have length less than or equal to 120, but got: "$apiKeyPrefixThatIsTooLong")"""
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

  "AdminApiKeyTemplateRoutes on PUT /admin/templates/{templateId}" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1")
    val requestBody = UpdateApiKeyTemplateRequest(
      name = apiKeyTemplateName_1,
      description = apiKeyTemplateDescription_1,
      isDefault = true,
      apiKeyMaxExpiryPeriod = Duration(17, TimeUnit.DAYS)
    )

    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with apiKeyTemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
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

  "AdminApiKeyTemplateRoutes on DELETE /admin/templates/{templateId}" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with apiKeyTemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
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

  "AdminApiKeyTemplateRoutes on GET /admin/templates/{templateId}" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "provided with apiKeyTemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
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

  "AdminApiKeyTemplateRoutes on POST /admin/templates/{templateId}/permissions" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1/permissions")
    val requestBody = CreateApiKeyTemplatesPermissionsRequest(
      permissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with TemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid/permissions")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty List" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(permissionIds = List.empty))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected size of permissionIds to be greater than or equal to 1, but got 0)")
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

      "request body contains PermissionId which is not an UUID" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "permissionIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "this-is-not-a-valid-uuid",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (Got value '\"this-is-not-a-valid-uuid\"' with wrong type, expecting string at 'permissionIds[1]')"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(apiKeyTemplateService).associatePermissionsWithApiKeyTemplate(
            eqTo(publicTemplateId_1),
            eqTo(requestBody.permissionIds)
          )
        } yield ()
      }

      "return successful value returned by ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(().asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ = response.body shouldBe Stream.empty
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplatesPermissionsAlreadyExistsError" in authorizedFixture {
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          Left(ApiKeyTemplatesPermissionsAlreadyExistsError(101L, 102L))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ApiKeyTemplatesPermissionsAlreadyExists)
              )
            )
        } yield ()
      }

      "return Not Found when ApiKeyTemplateService returns successful IO with Left containing ReferencedApiKeyTemplateDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          Left(ApiKeyTemplatesPermissionsInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedApiKeyTemplateNotFound)
              )
            )
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ReferencedPermissionDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          Left(ReferencedPermissionDoesNotExistError(publicPermissionId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedPermissionNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplatesPermissionsInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(Left(ApiKeyTemplatesPermissionsInsertionErrorImpl(testSqlException)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns
          IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on DELETE /admin/templates/{templateId}/permissions" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1/permissions")
    val requestBody = CreateApiKeyTemplatesPermissionsRequest(
      permissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
    )

    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with TemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid/permissions")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty List" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(permissionIds = List.empty))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected size of permissionIds to be greater than or equal to 1, but got 0)")
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

      "request body contains PermissionId which is not an UUID" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "permissionIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "this-is-not-a-valid-uuid",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (Got value '\"this-is-not-a-valid-uuid\"' with wrong type, expecting string at 'permissionIds[1]')"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.removePermissionsFromApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(apiKeyTemplateService).removePermissionsFromApiKeyTemplate(
            eqTo(publicTemplateId_1),
            eqTo(requestBody.permissionIds)
          )
        } yield ()
      }

      "return successful value returned by ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.removePermissionsFromApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(().asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ = response.body shouldBe Stream.empty
        } yield ()
      }

      "return Not Found when ApiKeyTemplateService returns successful IO with Left containing ReferencedApiKeyTemplateDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.removePermissionsFromApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          Left(ApiKeyTemplatesPermissionsInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedApiKeyTemplateNotFound)
              )
            )
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ReferencedPermissionDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.removePermissionsFromApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(Left(ReferencedPermissionDoesNotExistError(publicPermissionId_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ReferencedPermissionNotFound)
              )
            )
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplatesPermissionsNotFoundError" in authorizedFixture {
        apiKeyTemplateService.removePermissionsFromApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          Left(
            ApiKeyTemplatesPermissionsNotFoundError(
              List(
                ApiKeyTemplatesPermissionsEntity.Write(101L, 102L),
                ApiKeyTemplatesPermissionsEntity.Write(201L, 202L),
                ApiKeyTemplatesPermissionsEntity.Write(301L, 302L)
              )
            )
          )
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesPermissions.ApiKeyTemplatesPermissionsNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.removePermissionsFromApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on GET /admin/templates/{templateId}/permissions" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1/permissions")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with TemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid/permissions")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call PermissionService" in authorizedFixture {
        permissionService.getAllFor(any[ApiKeyTemplateId]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(permissionService).getAllFor(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return successful value returned by PermissionService" when {

        "PermissionService returns an empty List" in authorizedFixture {
          permissionService.getAllFor(any[ApiKeyTemplateId]) returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultiplePermissionsResponse]
              .asserting(_ shouldBe GetMultiplePermissionsResponse(permissions = List.empty))
          } yield ()
        }

        "PermissionService returns a List with several elements" in authorizedFixture {
          permissionService.getAllFor(any[ApiKeyTemplateId]) returns IO.pure(
            List(permission_1, permission_2, permission_3)
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultiplePermissionsResponse]
              .asserting(
                _ shouldBe GetMultiplePermissionsResponse(permissions = List(permission_1, permission_2, permission_3))
              )
          } yield ()
        }
      }

      "return Internal Server Error when PermissionService returns failed IO" in authorizedFixture {
        permissionService.getAllFor(any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on POST /admin/templates/{templateId}/users" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1/users")
    val requestBody = CreateApiKeyTemplatesUsersRequest(userIds = List(publicUserId_1, publicUserId_2, publicUserId_3))

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with TemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid/users")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty List" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(userIds = List.empty))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected size of userIds to be greater than or equal to 1, but got 0)")
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

      "request body contains UserId which is an empty String" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "userIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (expected userIds to pass validation, but got: List(fd00156e-b56b-4d35-9d67-05bc3681ac82, , 457cc79a-1357-4fa4-8d50-acd8b2e67d2a))"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(apiKeyTemplateService).associateUsersWithApiKeyTemplate(
            eqTo(publicTemplateId_1),
            eqTo(requestBody.userIds)
          )
        } yield ()
      }

      "return Created status and empty body" in authorizedFixture {
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(().asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ = response.body shouldBe Stream.empty
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplatesUsersAlreadyExistsError" in authorizedFixture {
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(Left(ApiKeyTemplatesUsersAlreadyExistsError(101L, 102L)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ApiKeyTemplatesUsersAlreadyExists)
              )
            )
        } yield ()
      }

      "return Not Found when ApiKeyTemplateService returns successful IO with Left containing ReferencedApiKeyTemplateDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(
          Left(ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ReferencedApiKeyTemplateNotFound)
              )
            )
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ReferencedUserDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(Left(ReferencedUserDoesNotExistError(publicUserId_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ReferencedUserNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns successful IO with Left containing ApiKeyTemplatesUsersInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(Left(ApiKeyTemplatesUsersInsertionErrorImpl(testSqlException)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.associateUsersWithApiKeyTemplate(
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyTemplateRoutes on GET /admin/templates/{templateId}/users" when {

    val uri = Uri.unsafeFromString(s"/admin/templates/$publicTemplateId_1/users")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but provided with TemplateId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/templates/this-is-not-a-valid-uuid/users")
      val requestWithIncorrectApiKeyTemplateId =
        Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
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

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApiKeyTemplateId)
          _ = verifyZeroInteractions(apiKeyTemplateService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(Right(List.empty))

        for {
          _ <- adminRoutes.run(request)
          _ = verify(userService).getAllForTemplate(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return successful value returned by UserService" when {

        "UserService returns an empty List" in authorizedFixture {
          userService.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(Right(List.empty))

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleUsersResponse]
              .asserting(_ shouldBe GetMultipleUsersResponse(users = List.empty))
          } yield ()
        }

        "UserService returns a List with several elements" in authorizedFixture {
          userService.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(Right(List(user_1, user_2, user_3)))

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleUsersResponse]
              .asserting(
                _ shouldBe GetMultipleUsersResponse(users = List(user_1, user_2, user_3))
              )
          } yield ()
        }
      }

      "return Bad Request when UserService returns successful IO with Left containing ReferencedApiKeyTemplateDoesNotExistError" in authorizedFixture {
        userService.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(
          Left(ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }
}
