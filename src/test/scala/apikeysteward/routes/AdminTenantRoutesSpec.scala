package apikeysteward.routes

import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError.TenantAlreadyExistsError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantIsNotDeactivatedError, TenantNotFoundError}
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

class AdminTenantRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val tenantService = mock[TenantService]

  private val adminRoutes: HttpApp[IO] =
    new AdminTenantRoutes(jwtAuthorizer, tenantService).allRoutes.orNotFound

  private val tokenString: AccessToken = "TOKEN"
  private val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, tenantService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] = IO {
    jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
      AuthTestData.jwtWithMockedSignature.asRight
    )
  }.flatMap(_ => test)

  private def runCommonJwtTests(request: Request[IO])(requiredPermissions: Set[Permission]): Unit = {

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- adminRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtAuthorizer or TenantService" in {
        for {
          _ <- adminRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtAuthorizer, tenantService)
        } yield ()
      }
    }

    "the JWT is provided" should {
      "call JwtAuthorizer providing access token" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          ErrorInfo.unauthorizedErrorInfo().asLeft
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(jwtAuthorizer).authorisedWithPermissions(eqTo(requiredPermissions))(eqTo(tokenString))
        } yield ()
      }
    }

    "JwtAuthorizer returns Left containing error" should {

      val jwtValidatorError = ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call TenantService" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(tenantService)
        } yield ()
      }
    }

    "JwtAuthorizer returns failed IO" should {

      "return Internal Server Error" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call TenantService" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(tenantService)
        } yield ()
      }
    }
  }

  "AdminTenantRoutes on POST /admin/tenants" when {

    val uri = Uri.unsafeFromString("/admin/tenants")
    val requestBody = CreateTenantRequest(name = name)

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
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call TenantService" in authorizedFixture {
        tenantService.createTenant(any[CreateTenantRequest]) returns IO.pure(tenant_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(tenantService).createTenant(eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by TenantService" in authorizedFixture {
        tenantService.createTenant(any[CreateTenantRequest]) returns IO.pure(tenant_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ <- response
            .as[CreateTenantResponse]
            .asserting(_ shouldBe CreateTenantResponse(tenant_1))
        } yield ()
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
    val requestBody = UpdateTenantRequest(name = name)

    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with tenantId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid")
        val requestWithIncorrectTenantId = Request[IO](method = Method.PUT, uri = uri)

        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
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
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid/reactivation")
        val requestWithIncorrectTenantId = Request[IO](method = Method.PUT, uri = uri)

        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
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
            .as[UpdateTenantResponse]
            .asserting(_ shouldBe UpdateTenantResponse(tenant_1))
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
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid/deactivation")
        val requestWithIncorrectTenantId = Request[IO](method = Method.PUT, uri = uri)

        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
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
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid")
        val requestWithIncorrectTenantId = Request[IO](method = Method.DELETE, uri = uri)

        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
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
            .as[DeactivateTenantResponse]
            .asserting(_ shouldBe DeactivateTenantResponse(tenant_1))
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
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/tenants/this-is-not-a-valid-uuid")
        val requestWithIncorrectTenantId = Request[IO](method = Method.GET, uri = uri)

        for {
          response <- adminRoutes.run(requestWithIncorrectTenantId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter tenantId")))
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
