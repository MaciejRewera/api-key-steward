package apikeysteward.routes

import apikeysteward.base.testdata.ResourceServersTestData.publicResourceServerId_1
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerIsNotDeactivatedError
import apikeysteward.model.errors.TenantDbError.TenantInsertionError.TenantAlreadyExistsError
import apikeysteward.model.errors.TenantDbError.{
  CannotDeleteDependencyError,
  TenantIsNotDeactivatedError,
  TenantNotFoundError
}
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer}
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.tenant._
import apikeysteward.services.TenantService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import io.circe.syntax.EncoderOps
import org.http4s.AuthScheme.Bearer
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Headers, HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class AdminTenantRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val tenantService = mock[TenantService]

  private val adminRoutes: HttpApp[IO] =
    new AdminTenantRoutes(jwtAuthorizer, tenantService).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, tenantService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO])(requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminRoutes, jwtAuthorizer, List(tenantService))(request, requiredPermissions)

  "AdminTenantRoutes on POST /admin/tenants" when {

    val uri = Uri.unsafeFromString("/admin/tenants")
    val requestBody = CreateTenantRequest(name = tenantName_1, description = tenantDescription_1)

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(tenantService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call TenantService" in authorizedFixture {
        tenantService.createTenant(any[CreateTenantRequest]) returns IO.pure(tenant_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).createTenant(eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by TenantService" when {

        "provided with description" in authorizedFixture {
          tenantService.createTenant(any[CreateTenantRequest]) returns IO.pure(tenant_1.asRight)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateTenantResponse]
              .asserting(_ shouldBe CreateTenantResponse(tenant_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val tenantWithoutDescription = tenant_1.copy(description = None)
          tenantService.createTenant(any[CreateTenantRequest]) returns IO.pure(tenantWithoutDescription.asRight)

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateTenantResponse]
              .asserting(_ shouldBe CreateTenantResponse(tenantWithoutDescription))
          } yield ()
        }
      }

      "return Internal Server Error when TenantService returns successful IO with Left containing TenantInsertionError" in authorizedFixture {
        tenantService.createTenant(any[CreateTenantRequest]) returns IO.pure(
          Left(TenantAlreadyExistsError(publicTenantIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.createTenant(any[CreateTenantRequest]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on PUT /admin/tenants/{tenantId}" when {

    val uri = Uri.unsafeFromString(s"/admin/tenants/$publicTenantId_1")
    val requestBody = UpdateTenantRequest(name = tenantName_1, description = tenantDescription_1)

    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with tenantId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid")
      val requestWithIncorrectTenantId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(tenantService)
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

        "NOT call TenantService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(tenantService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call TenantService" in authorizedFixture {
        tenantService.updateTenant(any[UUID], any[UpdateTenantRequest]) returns IO.pure(tenant_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).updateTenant(eqTo(publicTenantId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by TenantService" in authorizedFixture {
        tenantService.updateTenant(any[UUID], any[UpdateTenantRequest]) returns IO.pure(tenant_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[UpdateTenantResponse]
            .asserting(_ shouldBe UpdateTenantResponse(tenant_1))
        } yield ()
      }

      "return Not Found when TenantService returns successful IO with Left containing TenantNotFoundError" in authorizedFixture {
        tenantService.updateTenant(any[UUID], any[UpdateTenantRequest]) returns IO.pure(
          Left(TenantNotFoundError(publicTenantIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)))
        } yield ()
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.updateTenant(any[UUID], any[UpdateTenantRequest]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on PUT /admin/tenants/{tenantId}/reactivation" when {

    val uri = Uri.unsafeFromString(s"/admin/tenants/$publicTenantId_1/reactivation")
    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with tenantId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid/reactivation")
      val requestWithIncorrectTenantId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = verifyZeroInteractions(tenantService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call TenantService" in authorizedFixture {
        tenantService.reactivateTenant(any[UUID]) returns IO.pure(tenant_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).reactivateTenant(eqTo(publicTenantId_1))
        } yield ()
      }

      "return successful value returned by TenantService" in authorizedFixture {
        tenantService.reactivateTenant(any[UUID]) returns IO.pure(tenant_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[ReactivateTenantResponse]
            .asserting(_ shouldBe ReactivateTenantResponse(tenant_1))
        } yield ()
      }

      "return Not Found when TenantService returns successful IO with Left containing TenantNotFoundError" in authorizedFixture {
        tenantService.reactivateTenant(any[UUID]) returns IO.pure(Left(TenantNotFoundError(publicTenantIdStr_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)))
        } yield ()
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.reactivateTenant(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on PUT /admin/tenants/{tenantId}/deactivation" when {

    val uri = Uri.unsafeFromString(s"/admin/tenants/$publicTenantId_1/deactivation")
    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with tenantId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid/deactivation")
      val requestWithIncorrectTenantId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = verifyZeroInteractions(tenantService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call TenantService" in authorizedFixture {
        tenantService.deactivateTenant(any[UUID]) returns IO.pure(tenant_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).deactivateTenant(eqTo(publicTenantId_1))
        } yield ()
      }

      "return successful value returned by TenantService" in authorizedFixture {
        tenantService.deactivateTenant(any[UUID]) returns IO.pure(tenant_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeactivateTenantResponse]
            .asserting(_ shouldBe DeactivateTenantResponse(tenant_1))
        } yield ()
      }

      "return Not Found when TenantService returns successful IO with Left containing TenantNotFoundError" in authorizedFixture {
        tenantService.deactivateTenant(any[UUID]) returns IO.pure(Left(TenantNotFoundError(publicTenantIdStr_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)))
        } yield ()
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.deactivateTenant(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on DELETE /admin/tenants/{tenantId}" when {

    val uri = Uri.unsafeFromString(s"/admin/tenants/$publicTenantId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with tenantId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid")
      val requestWithIncorrectTenantId =
        Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = verifyZeroInteractions(tenantService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call TenantService" in authorizedFixture {
        tenantService.deleteTenant(any[UUID]) returns IO.pure(tenant_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).deleteTenant(eqTo(publicTenantId_1))
        } yield ()
      }

      "return successful value returned by TenantService" in authorizedFixture {
        tenantService.deleteTenant(any[UUID]) returns IO.pure(tenant_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeleteTenantResponse]
            .asserting(_ shouldBe DeleteTenantResponse(tenant_1))
        } yield ()
      }

      "return Bad Request when TenantService returns successful IO with Left containing TenantIsNotDeactivatedError" in authorizedFixture {
        tenantService.deleteTenant(any[UUID]) returns IO.pure(Left(TenantIsNotDeactivatedError(publicTenantId_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminTenant.TenantIsNotDeactivated(publicTenantId_1))
              )
            )
        } yield ()
      }

      "return Bad Request when TenantService returns successful IO with Left containing CannotDeleteDependencyError" in authorizedFixture {
        val dependencyError = ResourceServerIsNotDeactivatedError(publicResourceServerId_1)
        tenantService.deleteTenant(any[UUID]) returns IO.pure(
          Left(CannotDeleteDependencyError(publicTenantId_1, dependencyError))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminTenant.TenantDependencyCannotBeDeleted(publicTenantId_1))
              )
            )
        } yield ()
      }

      "return Not Found when TenantService returns successful IO with Left containing TenantNotFoundError" in authorizedFixture {
        tenantService.deleteTenant(any[UUID]) returns IO.pure(Left(TenantNotFoundError(publicTenantIdStr_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)))
        } yield ()
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.deleteTenant(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on GET /admin/tenants/{tenantId}" when {

    val uri = Uri.unsafeFromString(s"/admin/tenants/$publicTenantId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadAdmin))

    "provided with tenantId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid")
      val requestWithIncorrectTenantId =
        Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = verifyZeroInteractions(tenantService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call TenantService" in authorizedFixture {
        tenantService.getBy(any[UUID]) returns IO.pure(tenant_1.some)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).getBy(eqTo(publicTenantId_1))
        } yield ()
      }

      "return successful value returned by TenantService" in authorizedFixture {
        tenantService.getBy(any[UUID]) returns IO.pure(tenant_1.some)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSingleTenantResponse]
            .asserting(_ shouldBe GetSingleTenantResponse(tenant_1))
        } yield ()
      }

      "return Not Found when TenantService returns empty Option" in authorizedFixture {
        tenantService.getBy(any[UUID]) returns IO.pure(none)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminTenant.TenantNotFound)))
        } yield ()
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.getBy(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on GET /admin/tenants" when {

    val uri = Uri.unsafeFromString(s"/admin/tenants")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call TenantService" in authorizedFixture {
        tenantService.getAll returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).getAll
        } yield ()
      }

      "return successful value returned by TenantService" when {

        "TenantService returns an empty List" in authorizedFixture {
          tenantService.getAll returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleTenantsResponse]
              .asserting(_ shouldBe GetMultipleTenantsResponse(tenants = List.empty))
          } yield ()
        }

        "TenantService returns a List with several elements" in authorizedFixture {
          tenantService.getAll returns IO.pure(List(tenant_1, tenant_2, tenant_3))

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleTenantsResponse]
              .asserting(_ shouldBe GetMultipleTenantsResponse(tenants = List(tenant_1, tenant_2, tenant_3)))
          } yield ()
        }
      }

      "return Internal Server Error when TenantService returns failed IO" in authorizedFixture {
        tenantService.getAll returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
