package apikeysteward.routes

import apikeysteward.base.testdata.TenantsTestData.publicTenantIdStr_1
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.http4s.AuthScheme.Bearer
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Header, Headers, HttpApp, Request, Status}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.ci.{CIString, CIStringSyntax}

trait RoutesSpecBase extends AsyncWordSpec with AsyncIOSpec with Matchers {

  val testException = new RuntimeException("Test Exception")

  val tokenString: AccessToken           = "TOKEN"
  val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  val tenantIdHeaderName: CIString = ci"ApiKeySteward-TenantId"
  val tenantIdHeader: Header.Raw   = Header.Raw(tenantIdHeaderName, publicTenantIdStr_1)

  val allHeaders: Headers = Headers(authorizationHeader, tenantIdHeader)

  def authorizedFixture[T](jwtAuthorizer: JwtAuthorizer)(test: => IO[T]): IO[T] = IO {
    jwtAuthorizer
      .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
      .returns(
        IO.pure(
          AuthTestData.jwtWithMockedSignature.asRight
        )
      )
  }.flatMap(_ => test)

  def runCommonJwtTests[Service <: AnyRef](
      routes: HttpApp[IO],
      jwtAuthorizer: JwtAuthorizer,
      mockedServices: List[Service]
  )(
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

      "NOT call either JwtAuthorizer or the Services" in {
        for {
          _ <- routes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtAuthorizer)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }

    "the JWT is provided" should {
      "call JwtAuthorizer providing access token" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              ErrorInfo.unauthorizedErrorInfo().asLeft
            )
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
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              jwtValidatorError.asLeft
            )
          )

        for {
          response <- routes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call the Services" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              jwtValidatorError.asLeft
            )
          )

        for {
          _ <- routes.run(request)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }

    "JwtAuthorizer returns failed IO" should {

      "return Internal Server Error" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.raiseError(
              testException
            )
          )

        for {
          response <- routes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call the Services" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.raiseError(
              testException
            )
          )

        for {
          _ <- routes.run(request)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }
  }

  def runCommonTenantIdHeaderTests[Service <: AnyRef](
      routes: HttpApp[IO],
      jwtAuthorizer: JwtAuthorizer,
      mockedServices: List[Service]
  )(request: Request[IO]): Unit = {

    def authorizedInnerFixture[T](test: => IO[T]): IO[T] = authorizedFixture(jwtAuthorizer)(test)

    "JwtAuthorizer returns Right containing JsonWebToken, but TenantId request header is NOT a UUID" should {

      val requestWithIncorrectHeader = request.putHeaders(Header.Raw(tenantIdHeaderName, "this-is-not-a-valid-uuid"))
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId""")
      )

      "return Bad Request" in authorizedInnerFixture {
        for {
          response <- routes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call the Services" in authorizedInnerFixture {
        for {
          _ <- routes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but TenantId request header is NOT present" should {

      val requestWithIncorrectHeader = request.removeHeader(tenantIdHeaderName)
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId (missing)""")
      )

      "return Bad Request" in authorizedInnerFixture {
        for {
          response <- routes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call the Services" in authorizedInnerFixture {
        for {
          _ <- routes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }
  }

  def runCommonTenantIdHeaderTests[Service <: AnyRef](
      routes: HttpApp[IO],
      mockedServices: List[Service]
  )(request: Request[IO]): Unit = {

    "JwtAuthorizer returns Right containing JsonWebToken, but TenantId request header is NOT a UUID" should {

      val requestWithIncorrectHeader = request.putHeaders(Header.Raw(tenantIdHeaderName, "this-is-not-a-valid-uuid"))
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId""")
      )

      "return Bad Request" in {
        for {
          response <- routes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call the Services" in {
        for {
          _ <- routes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but TenantId request header is NOT present" should {

      val requestWithIncorrectHeader = request.removeHeader(tenantIdHeaderName)
      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
        Some(s"""Invalid value for: header ApiKeySteward-TenantId (missing)""")
      )

      "return Bad Request" in {
        for {
          response <- routes.run(requestWithIncorrectHeader)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "NOT call the Services" in {
        for {
          _ <- routes.run(requestWithIncorrectHeader)
          _ = verifyZeroInteractions(mockedServices: _*)
        } yield ()
      }
    }
  }

}
