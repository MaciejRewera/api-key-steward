package apikeysteward.connectors

import apikeysteward.config.Auth0ApiConfig
import apikeysteward.connectors.Auth0LoginConnector.{Auth0LoginRequest, Auth0LoginResponse}
import apikeysteward.model.errors.Auth0Error.Auth0LoginError.Auth0LoginUpstreamErrorResponse
import apikeysteward.routes.auth.WireMockIntegrationSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import sttp.model.StatusCode

class Auth0LoginConnectorSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with EitherValues
    with WireMockIntegrationSpec {

  private val tenantDomain = "https://test/domain/api/v0"
  private val audience     = "https://test/domain/api/v0/"
  private val clientId     = "test-client-id"
  private val clientSecret = "test-client-secret"

  private val auth0ApiConfig = Auth0ApiConfig(
    domain = wireMockUri.renderString,
    audience = audience,
    clientId = clientId,
    clientSecret = clientSecret
  )

  private val auth0LoginCredentialsProvider = new Auth0LoginCredentialsProvider(auth0ApiConfig)

  private val auth0LoginConnectorRes: Resource[IO, Auth0LoginConnector] =
    BlazeClientBuilder[IO].resource.map(new Auth0LoginConnector(auth0ApiConfig, auth0LoginCredentialsProvider, _))

  private def stubUrl(url: String, responseStatus: Int)(responseBody: String): StubMapping =
    stubFor(
      post(url)
        .willReturn(
          aResponse()
            .withStatus(responseStatus)
            .withBody(responseBody)
        )
    )

  private val accessToken = "test.access.token"
  private val scope       = "test:scope1 test:scope2 test:scope3 read:scope3"
  private val expiresIn   = 86400
  private val tokenType   = "Bearer"

  private val responseJson =
    s"""{
       |  "access_token": "$accessToken",
       |  "scope": "$scope",
       |  "expires_in": $expiresIn,
       |  "token_type": "$tokenType"
       |}""".stripMargin

  "Auth0LoginConnector on fetchAccessToken" should {

    val url = "/oauth/token"

    "call the right url with correct request body" in {
      stubUrl(url, StatusCode.Ok.code)(responseJson)
      val expectedRequestBody = Auth0LoginRequest(
        client_id = clientId,
        client_secret = clientSecret,
        audience = audience,
        grant_type = "client_credentials"
      )

      for {
        _ <- auth0LoginConnectorRes.use(_.fetchAccessToken(tenantDomain).value)
        _ = verify(
          1,
          postRequestedFor(urlEqualTo(url)).withRequestBody(equalToJson(expectedRequestBody.asJson.noSpaces))
        )
      } yield ()
    }

    "return Auth0LoginResponse when API returns Ok (200) response" in {
      stubUrl(url, StatusCode.Ok.code)(responseJson)
      val expectedResponse = Auth0LoginResponse(
        access_token = accessToken,
        scope = scope,
        expires_in = expiresIn,
        token_type = tokenType
      )

      auth0LoginConnectorRes.use(_.fetchAccessToken(tenantDomain).value).asserting(_ shouldBe Right(expectedResponse))
    }

    "return failed IO containing UpstreamErrorResponse" when
      Seq(400, 401, 403, 429, 500).foreach { errorStatusCode =>
        s"API returns $errorStatusCode response" in {
          val responseJsonEmpty = """{}"""
          stubUrl(url, errorStatusCode)(responseJsonEmpty)

          val expectedError = Auth0LoginUpstreamErrorResponse(
            errorStatusCode,
            s"Call to log into Auth0 failed for tenant domain: $tenantDomain. Reason: $responseJsonEmpty"
          )

          auth0LoginConnectorRes.use(_.fetchAccessToken(tenantDomain).value).asserting(_ shouldBe Left(expectedError))
        }
      }

    "return failed IO when API returns Ok (200) response, but with incorrect JSON" in {
      val responseJsonIncorrect =
        s"""{
           |  "access_token": "$accessToken",
           |  "expires_in": $expiresIn,
           |  "token_type": "$tokenType"
           |}""".stripMargin
      stubUrl(url, StatusCode.Ok.code)(responseJsonIncorrect)

      auth0LoginConnectorRes.use(_.fetchAccessToken(tenantDomain).value).attempt.asserting { res =>
        res.isLeft shouldBe true
        res.left.value.getMessage should include(
          s"Invalid message body: Could not decode JSON: ${parser.parse(responseJsonIncorrect).value.spaces2}"
        )
      }

    }

  }

}
