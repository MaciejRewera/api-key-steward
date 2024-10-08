package apikeysteward.routes

import apikeysteward.base.TestData.ApiKeys._
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer}
import apikeysteward.routes.model.admin.GetMultipleUserIdsResponse
import apikeysteward.services.ApiKeyManagementService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.http4s.AuthScheme.Bearer
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.headers.Authorization
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Credentials, Headers, HttpApp, Method, Request, Status}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class AdminUserRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val managementService = mock[ApiKeyManagementService]

  private val adminUserRoutes: HttpApp[IO] = new AdminUserRoutes(jwtAuthorizer, managementService).allRoutes.orNotFound

  private val tokenString: AccessToken = "TOKEN"
  private val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, managementService)
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
          response <- adminUserRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtAuthorizer or ManagementService" in {
        for {
          _ <- adminUserRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtAuthorizer, managementService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtAuthorizer providing access token" in {
      jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- adminUserRoutes.run(request)
        _ = verify(jwtAuthorizer).authorisedWithPermissions(eqTo(requiredPermissions))(eqTo(tokenString))
      } yield ()
    }

    "JwtAuthorizer returns Left containing error" should {

      val jwtValidatorError = ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- adminUserRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtAuthorizer returns failed IO" should {

      "return Internal Server Error" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- adminUserRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }
  }

  "AdminApiKeyRoutes on GET /admin/users" when {

    val uri = uri"/admin/users"
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ManagementService" in authorizedFixture {
        managementService.getAllUserIds returns IO.pure(List.empty)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(managementService).getAllUserIds
        } yield ()
      }

      "return the value returned by ManagementService" when {

        "ManagementService returns an empty List" in authorizedFixture {
          managementService.getAllUserIds returns IO.pure(List.empty)

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[GetMultipleUserIdsResponse].asserting(_ shouldBe GetMultipleUserIdsResponse(List.empty))
          } yield ()
        }

        "ManagementService returns a List with several elements" in authorizedFixture {
          managementService.getAllUserIds returns IO.pure(List(userId_1, userId_2, userId_3))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleUserIdsResponse]
              .asserting(_ shouldBe GetMultipleUserIdsResponse(List(userId_1, userId_2, userId_3)))
          } yield ()
        }
      }

      "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
        managementService.getAllUserIds returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
