package apikeysteward.routes

import apikeysteward.base.testdata.ApplicationsTestData._
import apikeysteward.base.testdata.PermissionsTestData.{
  createPermissionRequest_1,
  createPermissionRequest_2,
  createPermissionRequest_3
}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantIdStr_1, publicTenantId_1}
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError._
import apikeysteward.model.RepositoryErrors.ApplicationDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.application._
import apikeysteward.services.ApplicationService
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

class AdminApplicationRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val applicationService = mock[ApplicationService]

  private val adminRoutes: HttpApp[IO] =
    new AdminApplicationRoutes(jwtAuthorizer, applicationService).allRoutes.orNotFound

  private val tenantIdHeaderName: CIString = ci"ApiKeySteward-TenantId"
  private val tenantIdHeader = Header.Raw(tenantIdHeaderName, publicTenantIdStr_1)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, applicationService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO], requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminRoutes, jwtAuthorizer, List(applicationService))(request, requiredPermissions)

  "AdminApplicationRoutes on POST /admin/applications" when {

    val uri = Uri.unsafeFromString("/admin/applications")
    val requestBody = CreateApplicationRequest(
      name = applicationName_1,
      description = applicationDescription_1,
      permissions = List(createPermissionRequest_1, createPermissionRequest_2, createPermissionRequest_3)
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

      "NOT call ApplicationService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(applicationService)
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(applicationService)
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(applicationService)
          } yield ()
        }
      }

      "request body is provided with name longer than 250 characters" should {

        val nameThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 250, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(applicationService)
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(applicationService)
          } yield ()
        }
      }

      "request body is provided with description longer than 250 characters" should {

        val descriptionThatIsTooLong = List.fill(251)("A").mkString
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(applicationService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.pure(
          application_1.asRight
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).createApplication(eqTo(publicTenantId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by ApplicationService" when {

        "provided with description" in authorizedFixture {
          applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.pure(
            application_1.asRight
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApplicationResponse]
              .asserting(_ shouldBe CreateApplicationResponse(application_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val applicationWithoutDescription = application_1.copy(description = None)
          applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.pure(
            applicationWithoutDescription.asRight
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApplicationResponse]
              .asserting(_ shouldBe CreateApplicationResponse(applicationWithoutDescription))
          } yield ()
        }
      }

      "return Internal Server Error when ApplicationService returns successful IO with Left containing ApplicationAlreadyExistsError" in authorizedFixture {
        applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.pure(
          Left(ApplicationAlreadyExistsError(publicApplicationIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns successful IO with Left containing ApplicationInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.pure(
          Left(ApplicationInsertionErrorImpl(testSqlException))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Bad Request when ApplicationService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.pure(
          Left(ReferencedTenantDoesNotExistError(publicApplicationId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminApplication.ReferencedTenantNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.createApplication(any[TenantId], any[CreateApplicationRequest]) returns IO.raiseError(
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

  "AdminApplicationRoutes on PUT /admin/applications/{applicationId}" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1")
    val requestBody = UpdateApplicationRequest(name = applicationName_1, description = applicationDescription_1)

    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with applicationId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid")
      val requestWithIncorrectApplicationId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = verifyZeroInteractions(applicationService)
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(applicationService)
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(applicationService)
          } yield ()
        }
      }

      "request body is provided with name longer than 250 characters" should {

        val nameThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 250, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(applicationService)
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(applicationService)
          } yield ()
        }
      }

      "request body is provided with description longer than 250 characters" should {

        val descriptionThatIsTooLong = List.fill(251)("A").mkString
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

        "NOT call ApplicationService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(applicationService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.updateApplication(any[ApplicationId], any[UpdateApplicationRequest]) returns IO.pure(
          application_1.asRight
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).updateApplication(eqTo(publicApplicationId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by ApplicationService" in authorizedFixture {
        applicationService.updateApplication(any[ApplicationId], any[UpdateApplicationRequest]) returns IO.pure(
          application_1.asRight
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[UpdateApplicationResponse]
            .asserting(_ shouldBe UpdateApplicationResponse(application_1))
        } yield ()
      }

      "return Not Found when ApplicationService returns successful IO with Left containing ApplicationNotFoundError" in authorizedFixture {
        applicationService.updateApplication(any[ApplicationId], any[UpdateApplicationRequest]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.updateApplication(any[ApplicationId], any[UpdateApplicationRequest]) returns IO.raiseError(
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

  "AdminApplicationRoutes on PUT /admin/applications/{applicationId}/reactivation" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/reactivation")
    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with applicationId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid/reactivation")
      val requestWithIncorrectApplicationId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = verifyZeroInteractions(applicationService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.reactivateApplication(any[UUID]) returns IO.pure(application_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).reactivateApplication(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return successful value returned by ApplicationService" in authorizedFixture {
        applicationService.reactivateApplication(any[UUID]) returns IO.pure(application_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[ReactivateApplicationResponse]
            .asserting(_ shouldBe ReactivateApplicationResponse(application_1))
        } yield ()
      }

      "return Not Found when ApplicationService returns successful IO with Left containing ApplicationNotFoundError" in authorizedFixture {
        applicationService.reactivateApplication(any[UUID]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.reactivateApplication(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApplicationRoutes on PUT /admin/applications/{applicationId}/deactivation" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/deactivation")
    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with applicationId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid/deactivation")
      val requestWithIncorrectApplicationId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = verifyZeroInteractions(applicationService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.deactivateApplication(any[UUID]) returns IO.pure(application_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).deactivateApplication(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return successful value returned by ApplicationService" in authorizedFixture {
        applicationService.deactivateApplication(any[UUID]) returns IO.pure(application_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeactivateApplicationResponse]
            .asserting(_ shouldBe DeactivateApplicationResponse(application_1))
        } yield ()
      }

      "return Not Found when ApplicationService returns successful IO with Left containing ApplicationNotFoundError" in authorizedFixture {
        applicationService.deactivateApplication(any[UUID]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.deactivateApplication(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApplicationRoutes on DELETE /admin/applications/{applicationId}" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with applicationId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid")
      val requestWithIncorrectApplicationId =
        Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = verifyZeroInteractions(applicationService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.deleteApplication(any[UUID]) returns IO.pure(application_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).deleteApplication(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return successful value returned by ApplicationService" in authorizedFixture {
        applicationService.deleteApplication(any[UUID]) returns IO.pure(application_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeleteApplicationResponse]
            .asserting(_ shouldBe DeleteApplicationResponse(application_1))
        } yield ()
      }

      "return Bad Request when ApplicationService returns successful IO with Left containing ApplicationIsNotDeactivatedError" in authorizedFixture {
        applicationService.deleteApplication(any[UUID]) returns IO.pure(
          Left(ApplicationIsNotDeactivatedError(publicApplicationId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApplication.ApplicationIsNotDeactivated(publicApplicationId_1))
              )
            )
        } yield ()
      }

      "return Not Found when ApplicationService returns successful IO with Left containing ApplicationNotFoundError" in authorizedFixture {
        applicationService.deleteApplication(any[UUID]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.deleteApplication(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApplicationRoutes on GET /admin/applications/{applicationId}" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "provided with applicationId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid")
      val requestWithIncorrectApplicationId =
        Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = verifyZeroInteractions(applicationService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.getBy(any[UUID]) returns IO.pure(application_1.some)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).getBy(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return successful value returned by ApplicationService" in authorizedFixture {
        applicationService.getBy(any[UUID]) returns IO.pure(application_1.some)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSingleApplicationResponse]
            .asserting(_ shouldBe GetSingleApplicationResponse(application_1))
        } yield ()
      }

      "return Not Found when ApplicationService returns empty Option" in authorizedFixture {
        applicationService.getBy(any[UUID]) returns IO.pure(none)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApplication.ApplicationNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.getBy(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApplicationRoutes on GET /admin/applications" when {

    val uri = Uri.unsafeFromString(s"/admin/applications")
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

      "NOT call ApplicationService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(applicationService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApplicationService" in authorizedFixture {
        applicationService.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(applicationService).getAllForTenant(eqTo(UUID.fromString(tenantIdHeader.value)))
        } yield ()
      }

      "return successful value returned by ApplicationService" when {

        "ApplicationService returns an empty List" in authorizedFixture {
          applicationService.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApplicationsResponse]
              .asserting(_ shouldBe GetMultipleApplicationsResponse(applications = List.empty))
          } yield ()
        }

        "ApplicationService returns a List with several elements" in authorizedFixture {
          applicationService.getAllForTenant(any[TenantId]) returns IO.pure(
            List(application_1, application_2, application_3)
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApplicationsResponse]
              .asserting(
                _ shouldBe GetMultipleApplicationsResponse(applications =
                  List(application_1, application_2, application_3)
                )
              )
          } yield ()
        }
      }

      "return Internal Server Error when ApplicationService returns failed IO" in authorizedFixture {
        applicationService.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
