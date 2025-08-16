package apikeysteward.connectors

import apikeysteward.base.testdata.Auth0LoginTestData.accessToken_1
import apikeysteward.base.testdata.Auth0ResourceServersTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{resourceServerName_1, resourceServerName_2}
import apikeysteward.config.Auth0ApiConfig
import apikeysteward.model.errors.Auth0Error.Auth0LoginError.Auth0LoginUpstreamErrorResponse
import apikeysteward.model.errors.Auth0Error.Auth0UpstreamErrorResponse
import apikeysteward.repositories.Auth0AccessTokenProvider
import apikeysteward.repositories.Auth0AccessTokenProvider.Auth0AccessToken
import apikeysteward.routes.auth.WireMockIntegrationSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxEitherId
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Codec, Decoder, Encoder, parser}
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.mockito.ArgumentMatchers.any
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.ci.CIStringSyntax
import sttp.model.StatusCode

class Auth0ConnectorSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with EitherValues
    with WireMockIntegrationSpec {

  private val audience     = "https://test/domain/api/v0/"
  private val clientId     = "test-client-id"
  private val clientSecret = "test-client-secret"

  private val auth0ApiConfig = Auth0ApiConfig(
    domain = wireMockUri.renderString,
    audience = audience,
    clientId = clientId,
    clientSecret = clientSecret
  )

  private val auth0Connector: Resource[IO, Auth0Connector] =
    BlazeClientBuilder[IO].resource.map(new Auth0Connector(_))

  private val responseJson =
    s"""{
       |  "field1": "value 123",
       |  "field2": 12345
       |}""".stripMargin

  private case class TestDataStructure(
      field1: String,
      field2: Int
  )

  private object TestDataStructure {
    implicit val encoder: Encoder[TestDataStructure] = deriveEncoder[TestDataStructure].mapJson(_.deepDropNullValues)
    implicit val decoder: Decoder[TestDataStructure] = deriveDecoder[TestDataStructure]
  }

  "Auth0Connector on performApiCall" should {

    def stubUrl(url: String, responseStatus: Int)(responseBody: String): StubMapping =
      stubFor(
        get(url)
          .willReturn(
            aResponse()
              .withStatus(responseStatus)
              .withBody(responseBody)
          )
      )

    val url = "/api/v2/test-data-structure?include_totals=true"

    def request: Request[IO] = {
      val uri = Uri
        .unsafeFromString(auth0ApiConfig.domain + "/api/v2/test-data-structure")
        .withQueryParam("include_totals", true)

      val authorizationHeader = Header.Raw(ci"Authorization", s"Bearer $accessToken_1")

      Request[IO](
        method = Method.GET,
        uri = uri,
        headers = Headers(List(authorizationHeader))
      )
    }

    "call the right url with correct parameters" in {
      stubUrl(url, StatusCode.Ok.code)(responseJson)

      for {
        _ <- auth0Connector.use(_.performApiCall[TestDataStructure](request).value)

        _ = WireMock.verify(1, getRequestedFor(urlEqualTo(url)))
      } yield ()
    }

    "return Auth0LoginResponse when API returns Ok (200) response" in {
      stubUrl(url, StatusCode.Ok.code)(responseJson)
      val expectedResponse = TestDataStructure("value 123", 12345)

      auth0Connector.use(
        _.performApiCall[TestDataStructure](request).value.asserting(_ shouldBe Right(expectedResponse))
      )
    }

    "return Left containing UpstreamErrorResponse" when
      Seq(400, 401, 403, 429, 500).foreach { errorStatusCode =>
        s"API returns $errorStatusCode response" in {
          val responseJsonEmpty = """{}"""
          stubUrl(url, errorStatusCode)(responseJsonEmpty)

          val expectedUrl = wireMockUri.renderString + url
          val expectedError = Auth0UpstreamErrorResponse(
            errorStatusCode,
            s"Call to Auth0 API with URL: $expectedUrl failed. Reason: $responseJsonEmpty"
          )

          auth0Connector
            .use(_.performApiCall[TestDataStructure](request).value)
            .asserting(_ shouldBe Left(expectedError))
        }
      }

    "return failed IO" when {
      "API returns Ok (200) response, but with incorrect JSON" in {
        val responseJsonIncorrect =
          s"""{
             |  "field1": "value 1"
             |}""".stripMargin
        stubUrl(url, StatusCode.Ok.code)(responseJsonIncorrect)

        auth0Connector.use(_.performApiCall[TestDataStructure](request).value).attempt.asserting { res =>
          res.isLeft shouldBe true
          res.left.value.getMessage should include(
            s"Invalid message body: Could not decode JSON: ${parser.parse(responseJsonIncorrect).value.spaces2}"
          )
        }
      }
    }
  }

}
