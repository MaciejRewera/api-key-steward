package apikeysteward.routes.auth

import apikeysteward.config.JwksConfig
import apikeysteward.routes.auth.AuthTestData._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.{Scenario, StubMapping}
import io.circe.parser
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import sttp.model.StatusCode

import scala.concurrent.duration.DurationInt

class UrlJwkProviderSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with EitherValues
    with WireMockIntegrationSpec {

  private val url_1 = "/1/.well-known/jwks.json"

  private val jwksConfigSingleUrl = JwksConfig(
    urls = List(wireMockUri.addPath(url_1.replaceFirst("/", ""))),
    fetchRetryAttemptInitialDelay = 10.millis,
    fetchRetryAttemptMaxAmount = 3,
    cacheRefreshPeriod = 10.minutes,
    supportedAlgorithm = "RS256",
    supportedKeyType = "RSA",
    supportedKeyUse = "sig"
  )

  private def stubUrl(url: String, responseStatus: Int)(responseBody: String): StubMapping =
    stubFor(
      get(url)
        .willReturn(
          aResponse()
            .withStatus(responseStatus)
            .withBody(responseBody)
        )
    )

  private val jwkJson_1 =
    s"""{
       |  "alg": "RS256",
       |  "kty": "RSA",
       |  "use": "sig",
       |  "n": "$encodedModulus",
       |  "e": "$encodedExponent",
       |  "kid": "${jsonWebKey.kid}",
       |  "x5t": "${jsonWebKey.x5t.get}",
       |  "x5c": ["${jsonWebKey.x5c.get.head}"]
       |}
       |""".stripMargin

  private val jwkJson_2 =
    s"""{
       |  "alg": "RS256",
       |  "kty": "RSA",
       |  "use": "sig",
       |  "n": "$encodedModulus",
       |  "e": "$encodedExponent",
       |  "kid": "$kid_2",
       |  "x5t": "${jsonWebKey.x5t.get}",
       |  "x5c": ["${jsonWebKey.x5c.get.head}"]
       |}
       |""".stripMargin

  private val jwkJson_3 =
    s"""{
       |  "alg": "RS256",
       |  "kty": "RSA",
       |  "use": "sig",
       |  "n": "$encodedModulus",
       |  "e": "$encodedExponent",
       |  "kid": "$kid_3",
       |  "x5t": "${jsonWebKey.x5t.get}",
       |  "x5c": ["${jsonWebKey.x5c.get.head}"]
       |}
       |""".stripMargin

  private val responseJsonEmpty = """{"keys": []}"""

  private val responseJsonSingleJwk_1 =
    s"""{
          "keys": [
            $jwkJson_1
          ]
        }""".stripMargin

  private val responseJsonSingleJwk_2 =
    s"""{
          "keys": [
            $jwkJson_2
          ]
        }""".stripMargin

  private val responseJsonSingleJwk_3 =
    s"""{
          "keys": [
            $jwkJson_3
          ]
        }""".stripMargin

  private val responseJsonMultipleJwks =
    s"""{
          "keys": [
            $jwkJson_1,
            $jwkJson_2,
            $jwkJson_3
          ]
        }""".stripMargin

  private val responseJsonMultipleJwks_1_3 =
    s"""{
          "keys": [
            $jwkJson_1,
            $jwkJson_3
          ]
        }""".stripMargin

  "UrlJwkProvider on getJsonWebKey" when {

    "configuration contains only a single URL" when {

      val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
        BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfigSingleUrl, _))

      "should always call provided url" in {
        stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)

        for {
          _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
          _ = verify(1, getRequestedFor(urlEqualTo(url_1)))
        } yield ()
      }

      "fetched JWKS contains NO JWKs" should {
        "return empty Option" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting(_ shouldBe None)
        }
      }

      "fetched JWKS contains a single JWK" should {
        "return this JWK if key IDs match" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_1).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey
          }
        }

        "return empty Option if key IDs do NOT match" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_1).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting(_ shouldBe None)
        }
      }

      "fetched JWKS contains multiple JWKs" should {

        "return JWK with matching key ID" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonMultipleJwks).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey.copy(kid = kid_2)
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonMultipleJwks).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_4)).asserting(_ shouldBe None)
        }
      }

      "called several times" should {

        "call provided url only once" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_2) >>
                urlJwkProvider.getJsonWebKey(kid_3) >>
                urlJwkProvider.getJsonWebKey(kid_4)
            }

            _ = verify(1, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }

        "call provided url again after cache expiry period" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)

          val cacheRefreshPeriod = 100.milliseconds
          val sleepDuration = cacheRefreshPeriod.plus(10.millisecond)
          val jwksConfig = jwksConfigSingleUrl.copy(cacheRefreshPeriod = cacheRefreshPeriod)

          val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
            BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfig, _))

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_2) >>
                IO.sleep(sleepDuration) >>
                urlJwkProvider.getJsonWebKey(kid_3) >>
                IO.sleep(sleepDuration) >>
                urlJwkProvider.getJsonWebKey(kid_4)
            }

            _ = verify(3, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }
      }

      "the JWKS provider returns response other than 200 OK" should {

        def stubUrlInternalServerError(): StubMapping =
          stubUrl(url_1, StatusCode.InternalServerError.code)("Something went wrong.")

        "call the provider again, until reaching maxRetries" in {
          stubUrlInternalServerError()
          val expectedAttempts = jwksConfigSingleUrl.fetchRetryAttemptMaxAmount

          for {
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }

        "return empty Option" in {
          stubUrlInternalServerError()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting(_ shouldBe None)
        }
      }

      "the JWKS provider returns response other than 200 OK, but only on the first try" should {

        def stubUrlInternalServerErrorOnFirstTry(): StubMapping = {
          val scenarioName = "First try fails"
          val scenarioStateSuccess = "Success next"

          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_1).value.spaces2)
              )
          )
        }

        "call the provider again" in {
          stubUrlInternalServerErrorOnFirstTry()
          val expectedAttempts = 2

          for {
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }

        "return JWK with matching key ID" in {
          stubUrlInternalServerErrorOnFirstTry()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrlInternalServerErrorOnFirstTry()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting(_ shouldBe None)
        }
      }
    }

    "configuration contains several URLs" when {

      val url_2 = "/2/.well-known/jwks.json"
      val url_3 = "/3/.well-known/jwks.json"

      val jwksConfigMultipleUrls = jwksConfigSingleUrl.copy(urls =
        List(
          wireMockUri.addPath(url_1.replaceFirst("/", "")),
          wireMockUri.addPath(url_2.replaceFirst("/", "")),
          wireMockUri.addPath(url_3.replaceFirst("/", ""))
        )
      )

      val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
        BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfigMultipleUrls, _))

      "should always call provided urls" in {
        stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
        stubUrl(url_2, StatusCode.Ok.code)(responseJsonEmpty)
        stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

        for {
          _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
          _ = verify(1, getRequestedFor(urlEqualTo(url_1)))
          _ = verify(1, getRequestedFor(urlEqualTo(url_2)))
          _ = verify(1, getRequestedFor(urlEqualTo(url_3)))
        } yield ()
      }

      "all JWKS providers return empty sets" should {
        "return empty Option" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_2, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting(_ shouldBe None)
        }
      }

      "only one JWKS provider returns a single JWK" should {

        "return this JWK if key IDs match" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_1).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey
          }
        }

        "return empty Option if key IDs do NOT match" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_1).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting(_ shouldBe None)
        }
      }

      "each JWKS provider returns a single JWK" should {

        "return JWK with matching key ID" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_1).value.spaces2)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_3).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_2).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey.copy(kid = kid_2)
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_1).value.spaces2)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_3).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_2).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_4)).asserting(_ shouldBe None)
        }
      }

      "JWKS providers return different amounts of JWKs" should {

        "return JWK with matching key ID" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_2).value.spaces2)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonMultipleJwks_1_3).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey.copy(kid = kid_2)
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonSingleJwk_2).value.spaces2)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonMultipleJwks_1_3).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_4)).asserting(_ shouldBe None)
        }
      }

      "called several times" should {

        "call provided urls only once" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_2, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_2) >>
                urlJwkProvider.getJsonWebKey(kid_3) >>
                urlJwkProvider.getJsonWebKey(kid_4)
            }

            _ = verify(1, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }

        "call provided urls again after cache expiry period" in {
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_2, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          val cacheRefreshPeriod = 100.milliseconds
          val sleepDuration = cacheRefreshPeriod.plus(10.millisecond)
          val jwksConfig = jwksConfigMultipleUrls.copy(cacheRefreshPeriod = cacheRefreshPeriod)

          val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
            BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfig, _))

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_2) >>
                IO.sleep(sleepDuration) >>
                urlJwkProvider.getJsonWebKey(kid_3) >>
                IO.sleep(sleepDuration) >>
                urlJwkProvider.getJsonWebKey(kid_4)
            }

            _ = verify(3, getRequestedFor(urlEqualTo(url_1)))
            _ = verify(3, getRequestedFor(urlEqualTo(url_2)))
            _ = verify(3, getRequestedFor(urlEqualTo(url_3)))
          } yield ()
        }
      }

      "all JWKS providers return response other than 200 OK" should {

        def stubUrlInternalServerError(): StubMapping = {
          stubUrl(url_1, StatusCode.InternalServerError.code)("Something went wrong.")
          stubUrl(url_2, StatusCode.InternalServerError.code)("Something went wrong.")
          stubUrl(url_3, StatusCode.InternalServerError.code)("Something went wrong.")
        }

        "call the provider again, until reaching maxRetries" in {
          stubUrlInternalServerError()
          val expectedAttempts = jwksConfigSingleUrl.fetchRetryAttemptMaxAmount

          for {
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))

            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_1)))
            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_2)))
            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_3)))
          } yield ()
        }

        "return empty Option" in {
          stubUrlInternalServerError()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting(_ shouldBe None)
        }
      }

      "one of JWKS providers return response other than 200 OK" should {

        def stubUrls(): StubMapping = {
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonMultipleJwks_1_3).value.spaces2)
          stubUrl(url_2, StatusCode.InternalServerError.code)("Something went wrong.")
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)
        }

        "call the provider again, until reaching maxRetries" in {
          stubUrls()
          val expectedAttempts = jwksConfigSingleUrl.fetchRetryAttemptMaxAmount

          for {
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
            _ = verify(1, getRequestedFor(urlEqualTo(url_1)))
            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_2)))
            _ = verify(1, getRequestedFor(urlEqualTo(url_3)))
          } yield ()
        }

        "return JWK with matching key ID" in {
          stubUrls()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrls()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting(_ shouldBe None)
        }
      }

      "all JWKS providers return response other than 200 OK, but only on the first few tries" should {

        def stubUrlInternalServerErrorOnFirstTry(): StubMapping = {
          val scenarioName_1 = "First try fails 1"
          val scenarioName_2 = "First try fails 2"
          val scenarioName_3 = "First try fails 3"
          val scenarioStateFailure = "Failure next"
          val scenarioStateSuccess = "Success next"

          stubFor(
            get(url_1)
              .inScenario(scenarioName_1)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName_1)
              .whenScenarioStateIs(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_1).value.spaces2)
              )
          )

          stubFor(
            get(url_2)
              .inScenario(scenarioName_2)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateFailure)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_2)
              .inScenario(scenarioName_2)
              .whenScenarioStateIs(scenarioStateFailure)
              .willSetStateTo(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_2)
              .inScenario(scenarioName_2)
              .whenScenarioStateIs(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_2).value.spaces2)
              )
          )

          stubFor(
            get(url_3)
              .inScenario(scenarioName_3)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_3)
              .inScenario(scenarioName_3)
              .whenScenarioStateIs(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_3).value.spaces2)
              )
          )
        }

        "call the providers again" in {
          stubUrlInternalServerErrorOnFirstTry()

          for {
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))

            _ = verify(2, getRequestedFor(urlEqualTo(url_1)))
            _ = verify(3, getRequestedFor(urlEqualTo(url_2)))
            _ = verify(2, getRequestedFor(urlEqualTo(url_3)))
          } yield ()
        }

        "return JWK with matching key ID" in {
          stubUrlInternalServerErrorOnFirstTry()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrlInternalServerErrorOnFirstTry()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_4)).asserting(_ shouldBe None)
        }
      }

      "some of the JWKS providers return response other than 200 OK, but only on the first few tries" should {

        def stubUrlInternalServerErrorOnFirstTry(): StubMapping = {
          val scenarioName_2 = "First try fails 2"
          val scenarioName_3 = "First try fails 3"
          val scenarioStateFailure = "Failure next"
          val scenarioStateSuccess = "Success next"

          stubFor(
            get(url_1)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_1).value.spaces2)
              )
          )

          stubFor(
            get(url_2)
              .inScenario(scenarioName_2)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateFailure)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_2)
              .inScenario(scenarioName_2)
              .whenScenarioStateIs(scenarioStateFailure)
              .willSetStateTo(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_2)
              .inScenario(scenarioName_2)
              .whenScenarioStateIs(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_2).value.spaces2)
              )
          )

          stubFor(
            get(url_3)
              .inScenario(scenarioName_3)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_3)
              .inScenario(scenarioName_3)
              .whenScenarioStateIs(scenarioStateSuccess)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_3).value.spaces2)
              )
          )
        }

        "call the failing providers again" in {
          stubUrlInternalServerErrorOnFirstTry()

          for {
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))

            _ = verify(1, getRequestedFor(urlEqualTo(url_1)))
            _ = verify(3, getRequestedFor(urlEqualTo(url_2)))
            _ = verify(2, getRequestedFor(urlEqualTo(url_3)))
          } yield ()
        }

        "return JWK with matching key ID" in {
          stubUrlInternalServerErrorOnFirstTry()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
            res shouldBe defined
            res.get shouldBe jsonWebKey
          }
        }

        "return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrlInternalServerErrorOnFirstTry()

          urlJwkProviderRes.use(_.getJsonWebKey(kid_4)).asserting(_ shouldBe None)
        }
      }

    }
  }
}
