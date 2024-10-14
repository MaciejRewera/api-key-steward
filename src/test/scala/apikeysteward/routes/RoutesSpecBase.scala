package apikeysteward.routes

import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.http4s.AuthScheme.Bearer
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Headers, HttpApp, Request, Status}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

trait RoutesSpecBase extends AsyncWordSpec with AsyncIOSpec with Matchers {

  val testException = new RuntimeException("Test Exception")

  val tokenString: AccessToken = "TOKEN"
  val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  def authorizedFixture[T](jwtAuthorizer: JwtAuthorizer)(test: => IO[T]): IO[T] = IO {
    jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
      AuthTestData.jwtWithMockedSignature.asRight
    )
  }.flatMap(_ => test)

  def runCommonJwtTests[Service <: AnyRef](routes: HttpApp[IO], jwtAuthorizer: JwtAuthorizer, mockedService: Service)(
      request: Request[IO],
      requiredPermissions: Set[Permission]
  ): Unit = {

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- routes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      s"NOT call either JwtAuthorizer or ${mockedService.getClass.getSimpleName}" in {
        for {
          _ <- routes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtAuthorizer, mockedService)
        } yield ()
      }
    }

    "the JWT is provided" should {
      "call JwtAuthorizer providing access token" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          ErrorInfo.unauthorizedErrorInfo().asLeft
        )

        for {
          _ <- routes.run(request)
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
          response <- routes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      s"NOT call ${mockedService.getClass.getSimpleName}" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- routes.run(request)
          _ = verifyZeroInteractions(mockedService)
        } yield ()
      }
    }

    "JwtAuthorizer returns failed IO" should {

      "return Internal Server Error" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- routes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      s"NOT call ${mockedService.getClass.getSimpleName}" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- routes.run(request)
          _ = verifyZeroInteractions(mockedService)
        } yield ()
      }
    }
  }

}
