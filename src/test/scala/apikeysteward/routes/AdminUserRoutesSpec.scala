package apikeysteward.routes

import apikeysteward.base.testdata.TenantsTestData.{publicTenantIdStr_1, publicTenantId_1}
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError._
import apikeysteward.model.RepositoryErrors.UserDbError.UserNotFoundError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer}
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.user._
import apikeysteward.services.UserService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.http4sLiteralsSyntax
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

class AdminUserRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val userService = mock[UserService]

  private val adminUserRoutes: HttpApp[IO] = new AdminUserRoutes(jwtAuthorizer, userService).allRoutes.orNotFound

  private val tenantIdHeaderName: CIString = ci"ApiKeySteward-TenantId"
  private val tenantIdHeader = Header.Raw(tenantIdHeaderName, publicTenantIdStr_1)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, userService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO], requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminUserRoutes, jwtAuthorizer, userService)(request, requiredPermissions)

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    "JwtAuthorizer returns Right containing JsonWebToken, but request header is NOT a UUID" should {

      val requestWithIncorrectHeader = request.putHeaders(Header.Raw(tenantIdHeaderName, "this-is-not-a-valid-uuid"))
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId""")
      )

      "return Bad Request" in authorizedFixture {
        for {
          response <- adminUserRoutes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call ApplicationService" in authorizedFixture {
        for {
          _ <- adminUserRoutes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(userService)
        } yield ()
      }
    }

  "AdminUserRoutes on POST /admin/users" when {

    val uri = Uri.unsafeFromString("/admin/users")
    val requestBody = CreateUserRequest(userId = publicUserId_1)

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty userId" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(userId = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected userId to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call UserService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(userService)
          } yield ()
        }
      }

      "request body is provided with userId containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(userId = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected userId to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call UserService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(userService)
          } yield ()
        }
      }

      "request body is provided with userId longer than 250 characters" should {

        val nameThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(userId = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected userId to have length less than or equal to 250, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call UserService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(userService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call UserService" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          user_1.asRight
        )

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).createUser(eqTo(publicTenantId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by UserService" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(user_1.asRight)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ <- response
            .as[CreateUserResponse]
            .asserting(_ shouldBe CreateUserResponse(user_1))
        } yield ()
      }

      "return Bad Request when UserService returns successful IO with Left containing UserAlreadyExistsForThisTenantError" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          Left(UserAlreadyExistsForThisTenantError(publicUserId_1, 13L))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.UserAlreadyExistsForThisTenant))
            )
        } yield ()
      }

      "return Bad Request when UserService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          Left(ReferencedTenantDoesNotExistError(publicTenantId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns successful IO with Left containing UserInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          Left(UserInsertionErrorImpl(testSqlException))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on DELETE /admin/users/{userId}" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(user_1.asRight)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).deleteUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return successful value returned by UserService" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(user_1.asRight)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeleteUserResponse]
            .asserting(_ shouldBe DeleteUserResponse(user_1))
        } yield ()
      }

      "return Not Found when UserService returns successful IO with Left containing UserNotFoundError" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(
          Left(UserNotFoundError(publicTenantId_1, publicUserId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminUser.UserNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on GET /admin/users/{userId}" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.pure(user_1.some)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return successful value returned by UserService" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.pure(user_1.some)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSingleUserResponse]
            .asserting(_ shouldBe GetSingleUserResponse(user_1))
        } yield ()
      }

      "return Not Found when UserService returns empty Option" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.pure(none)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminUser.UserNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on GET /admin/users" when {

    val uri = uri"/admin/users"
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader, tenantIdHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.getAllForTenant(any[TenantId]) returns IO.pure(Right(List.empty))

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).getAllForTenant(eqTo(publicTenantId_1))
        } yield ()
      }

      "return the value returned by UserService" when {

        "UserService returns an empty List" in authorizedFixture {
          userService.getAllForTenant(any[TenantId]) returns IO.pure(Right(List.empty))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[GetMultipleUsersResponse].asserting(_ shouldBe GetMultipleUsersResponse(List.empty))
          } yield ()
        }

        "UserService returns a List with several elements" in authorizedFixture {
          userService.getAllForTenant(any[TenantId]) returns IO.pure(Right(List(user_1, user_2, user_3)))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleUsersResponse]
              .asserting(_ shouldBe GetMultipleUsersResponse(List(user_1, user_2, user_3)))
          } yield ()
        }
      }

      "return Bad Request when UserService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        userService.getAllForTenant(any[TenantId]) returns IO.pure(
          Left(ReferencedTenantDoesNotExistError(publicTenantId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
