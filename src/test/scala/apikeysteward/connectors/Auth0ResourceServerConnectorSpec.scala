package apikeysteward.connectors

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.Auth0LoginTestData.accessToken_1
import apikeysteward.base.testdata.Auth0ResourceServersTestData._
import apikeysteward.config.Auth0ApiConfig
import apikeysteward.connectors.model.auth0.Auth0ResourceServerResponse
import apikeysteward.model.errors.Auth0Error
import apikeysteward.model.errors.Auth0Error.Auth0LoginError.Auth0LoginUpstreamErrorResponse
import apikeysteward.model.errors.Auth0Error.Auth0UpstreamErrorResponse
import apikeysteward.repositories.{Auth0AccessTokenProvider, DoobieUnitSpec}
import cats.data.EitherT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.http4s.{Header, Method, Request}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.ci.CIStringSyntax

class Auth0ResourceServerConnectorSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach {

  private val auth0AccessTokenProvider = mock[Auth0AccessTokenProvider]
  private val auth0ApiConfig           = mock[Auth0ApiConfig]
  private val auth0Connector           = mock[Auth0Connector]

  private val auth0ResourceServerConnector: Auth0ResourceServerConnector =
    new Auth0ResourceServerConnector(auth0AccessTokenProvider, auth0ApiConfig, auth0Connector)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(auth0AccessTokenProvider)
    reset(auth0ApiConfig)
    reset(auth0Connector)

    auth0AccessTokenProvider.getAccessToken(any()).returns(IO.pure(accessToken_1.asRight))
    auth0ApiConfig.domain.returns(tenantDomain)
  }

  private val tenantDomain = "https://test/domain/api/v0"

  private val testException = new RuntimeException("Test Exception")

  "Auth0Connector on performApiCall" when {

    "everything works correctly" should {

      "call Auth0AccessTokenProvider, Auth0ApiConfig and Auth0Connector" in {
        auth0Connector.performApiCall(any())(any()).returns(EitherT.pure(auth0ResourceServerResponse_1))

        val expectedUri     = s"$tenantDomain/api/v2/resource-servers?include_totals=true"
        val expectedHeaders = List(Header.Raw(ci"Authorization", s"Bearer $accessToken_1"))

        for {
          _ <- auth0ResourceServerConnector.fetchAllResourceServers(tenantDomain).value

          _ = verify(auth0AccessTokenProvider).getAccessToken(eqTo(tenantDomain))
          _ = verify(auth0ApiConfig).domain

          captor        = ArgumentCaptor.forClass(classOf[Request[IO]])
          _             = verify(auth0Connector).performApiCall(captor.capture)(any())
          actualRequest = captor.getValue
          _             = actualRequest.method shouldBe Method.GET
          _             = actualRequest.uri.renderString shouldBe expectedUri
          _             = actualRequest.headers.headers shouldBe expectedHeaders
        } yield ()
      }

      "return the value obtained from Auth0Connector" in {
        auth0Connector.performApiCall(any())(any()).returns(EitherT.pure(auth0ResourceServerResponse_1))
        val expectedResult = Right(List(auth0ResourceServer_1, auth0ResourceServer_2))

        auth0ResourceServerConnector.fetchAllResourceServers(tenantDomain).value.asserting(_ shouldBe expectedResult)
      }
    }

    "Auth0AccessTokenProvider returns Left containing Auth0LoginError" should {

      "NOT call Auth0ApiConfig or Auth0Connector" in {
        val upstreamErrorResponse =
          Auth0LoginUpstreamErrorResponse(500, "Test Auth0LoginUpstreamErrorResponse error message")
        auth0AccessTokenProvider.getAccessToken(any()).returns(IO.pure(upstreamErrorResponse.asLeft))

        for {
          _ <- auth0ResourceServerConnector.fetchAllResourceServers(tenantDomain).value
          _ = verifyZeroInteractions(auth0ApiConfig, auth0Connector)
        } yield ()
      }

      "return Left containing Auth0LoginError" in {
        val upstreamErrorResponse =
          Auth0LoginUpstreamErrorResponse(500, "Test Auth0LoginUpstreamErrorResponse error message")
        auth0AccessTokenProvider.getAccessToken(any()).returns(IO.pure(upstreamErrorResponse.asLeft))

        auth0ResourceServerConnector
          .fetchAllResourceServers(tenantDomain)
          .value
          .asserting(_ shouldBe Left(upstreamErrorResponse))
      }
    }

    "Auth0AccessTokenProvider returns failed IO" should {

      "NOT call Auth0ApiConfig or Auth0Connector" in {
        auth0AccessTokenProvider.getAccessToken(any()).returns(IO.raiseError(testException))

        for {
          _ <- auth0ResourceServerConnector.fetchAllResourceServers(tenantDomain).value.attempt
          _ = verifyZeroInteractions(auth0ApiConfig, auth0Connector)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        auth0AccessTokenProvider.getAccessToken(any()).returns(IO.raiseError(testException))

        auth0ResourceServerConnector
          .fetchAllResourceServers(tenantDomain)
          .value
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "Auth0ApiConfig throws exception" should {

      "NOT call Auth0Connector" in {
        auth0ApiConfig.domain.throws(testException)

        for {
          _ <- auth0ResourceServerConnector.fetchAllResourceServers(tenantDomain).value.attempt
          _ = verifyZeroInteractions(auth0Connector)
        } yield ()
      }

      "return failed IO" in {
        auth0ApiConfig.domain.throws(testException)

        auth0ResourceServerConnector
          .fetchAllResourceServers(tenantDomain)
          .value
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "Auth0Connector returns Left containing Auth0Error" should {
      "return Left containing this error" in {
        val upstreamErrorResponse = Auth0UpstreamErrorResponse(500, "Test Auth0UpstreamErrorResponse error message")
        auth0Connector.performApiCall(any())(any()).returns(EitherT.leftT(upstreamErrorResponse))

        auth0ResourceServerConnector
          .fetchAllResourceServers(tenantDomain)
          .value
          .asserting(_ shouldBe Left(upstreamErrorResponse))
      }
    }

    "Auth0Connector returns failed IO" should {
      "return failed IO containing the same exception" in {
        auth0Connector
          .performApiCall(any())(any())
          .returns(EitherT(IO.raiseError[Either[Auth0Error, Auth0ResourceServerResponse]](testException)))

        auth0ResourceServerConnector
          .fetchAllResourceServers(tenantDomain)
          .value
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
