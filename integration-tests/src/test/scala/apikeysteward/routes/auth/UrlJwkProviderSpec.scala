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
    urls = Set(wireMockUri.addPath(url_1.replaceFirst("/", ""))),
    fetchRetryAttemptInitialDelay = 10.millis,
    fetchRetryMaxAttempts = 3,
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

      "JWKS provider returns incorrect JSON" should {
        "throw exception" in {
          val responseJsonIncorrect =
            s"""{
               |  "keys": [
               |    {
               |      "alg": "RS256",
               |      "use": "sig",
               |      "n": "$encodedModulus",
               |      "e": "$encodedExponent",
               |      "kid": "${jsonWebKey.kid}",
               |      "x5t": "${jsonWebKey.x5t.get}",
               |      "x5c": ["${jsonWebKey.x5c.get.head}"]
               |    }
               |  ]
               |}
               |""".stripMargin
          stubUrl(url_1, StatusCode.Ok.code)(parser.parse(responseJsonIncorrect).value.spaces2)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).attempt.asserting { result =>
            result.isLeft shouldBe true
            result.left.value.getMessage should include(
              s"Invalid message body: Could not decode JSON: ${parser.parse(responseJsonIncorrect).value.spaces2}"
            )
          }
        }
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

        "call the provider again until reaching maxRetries" in {
          stubUrlInternalServerError()
          val expectedAttempts = jwksConfigSingleUrl.fetchRetryMaxAttempts

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

      "the JWKS provider returns response other than 200 OK on the first call to UrlJwkProvider, but returns 200 on subsequent calls before cache expires" should {

        def stubUrls(): StubMapping = {
          val scenarioName = "First try fails"
          val scenarioStateFailure_1 = "Failure next 1"
          val scenarioStateFailure_2 = "Failure next 2"
          val scenarioStateSuccess = "Success next"

          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateFailure_1)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(scenarioStateFailure_1)
              .willSetStateTo(scenarioStateFailure_2)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(scenarioStateFailure_2)
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

        "on the first call, call the provider again until reaching maxRetries" in {
          stubUrls()
          val expectedAttempts = 1 + jwksConfigSingleUrl.fetchRetryMaxAttempts

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }

        "on the first call, return empty Option" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))

            _ = result shouldBe None
          } yield ()
        }

        "on the second call, return JWK with matching key ID" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = result shouldBe defined
            _ = result.get shouldBe jsonWebKey
          } yield ()
        }

        "on the second call, return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_2) >>
                urlJwkProvider.getJsonWebKey(kid_2)
            }

            _ = result shouldBe None
          } yield ()
        }
      }

      "the JWKS provider returns 200 OK on the first call to UrlJwkProvider, but returns error response on subsequent calls after cache expires" should {

        def stubUrls(): StubMapping = {
          val scenarioName = "First try fails"
          val scenarioStateFailure = "Failure next 1"

          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateFailure)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(responseJsonSingleJwk_1)
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName)
              .whenScenarioStateIs(scenarioStateFailure)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
        }

        val cacheRefreshPeriod = 100.milliseconds
        val sleepDuration = cacheRefreshPeriod.plus(10.millisecond)
        val jwksConfig = jwksConfigSingleUrl.copy(cacheRefreshPeriod = cacheRefreshPeriod)

        val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
          BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfig, _))

        "on the second call, call the provider again until reaching maxRetries" in {
          stubUrls()
          val expectedAttempts = 1 + jwksConfigSingleUrl.fetchRetryMaxAttempts

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                IO.sleep(sleepDuration) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_1)))
          } yield ()
        }

        "on the second call, return empty Option" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                IO.sleep(sleepDuration) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = result shouldBe None
          } yield ()
        }
      }
    }

    "configuration contains several URLs" when {

      val url_2 = "/2/.well-known/jwks.json"
      val url_3 = "/3/.well-known/jwks.json"

      val jwksConfigMultipleUrls = jwksConfigSingleUrl.copy(urls =
        Set(
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

      "configuration contains duplicated urls" should {

        "call such urls only once" in {
          val jwksConfigDuplicatedUrls = jwksConfigSingleUrl.copy(urls =
            Set(
              wireMockUri.addPath(url_1.replaceFirst("/", "")),
              wireMockUri.addPath(url_2.replaceFirst("/", "")),
              wireMockUri.addPath(url_2.replaceFirst("/", "")),
              wireMockUri.addPath(url_3.replaceFirst("/", "")),
              wireMockUri.addPath(url_1.replaceFirst("/", ""))
            )
          )

          val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
            BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfigDuplicatedUrls, _))

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
      }

      "one of JWKS providers return incorrect JSON" should {
        "throw exception" in {
          val responseJsonIncorrect =
            s"""{
               |  "keys": [
               |    {
               |      "alg": "RS256",
               |      "use": "sig",
               |      "n": "$encodedModulus",
               |      "e": "$encodedExponent",
               |      "kid": "${jsonWebKey.kid}",
               |      "x5t": "${jsonWebKey.x5t.get}",
               |      "x5c": ["${jsonWebKey.x5c.get.head}"]
               |    }
               |  ]
               |}
               |""".stripMargin
          stubUrl(url_1, StatusCode.Ok.code)(responseJsonEmpty)
          stubUrl(url_2, StatusCode.Ok.code)(parser.parse(responseJsonIncorrect).value.spaces2)
          stubUrl(url_3, StatusCode.Ok.code)(responseJsonEmpty)

          urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).attempt.asserting { result =>
            result.isLeft shouldBe true
            result.left.value.getMessage should include(
              s"Invalid message body: Could not decode JSON: ${parser.parse(responseJsonIncorrect).value.spaces2}"
            )
          }
        }
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

        "call the provider again until reaching maxRetries" in {
          stubUrlInternalServerError()
          val expectedAttempts = jwksConfigSingleUrl.fetchRetryMaxAttempts

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

        "call the provider again until reaching maxRetries" in {
          stubUrls()
          val expectedAttempts = jwksConfigSingleUrl.fetchRetryMaxAttempts

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

      "one of the JWKS providers returns response other than 200 OK on the first call to UrlJwkProvider, but returns 200 on subsequent calls before cache expires" should {

        def stubUrls(): StubMapping = {
          val scenarioName_1 = "First try fails"
          val scenarioStateFailure_1 = "Failure next 1"
          val scenarioStateFailure_2 = "Failure next 2"
          val scenarioStateSuccess = "Success next"

          stubFor(
            get(url_1)
              .inScenario(scenarioName_1)
              .whenScenarioStateIs(Scenario.STARTED)
              .willSetStateTo(scenarioStateFailure_1)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName_1)
              .whenScenarioStateIs(scenarioStateFailure_1)
              .willSetStateTo(scenarioStateFailure_2)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.InternalServerError.code)
                  .withBody("Something went wrong.")
              )
          )
          stubFor(
            get(url_1)
              .inScenario(scenarioName_1)
              .whenScenarioStateIs(scenarioStateFailure_2)
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
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_2).value.spaces2)
              )
          )
          stubFor(
            get(url_3)
              .willReturn(
                aResponse()
                  .withStatus(StatusCode.Ok.code)
                  .withBody(parser.parse(responseJsonSingleJwk_3).value.spaces2)
              )
          )
        }

        "on the first call, call the failing provider again until reaching maxRetries" in {
          stubUrls()
          val expectedAttempts = 1 + jwksConfigSingleUrl.fetchRetryMaxAttempts

          for {
            _ <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = verify(expectedAttempts, getRequestedFor(urlEqualTo(url_1)))
            _ = verify(1, getRequestedFor(urlEqualTo(url_2)))
            _ = verify(1, getRequestedFor(urlEqualTo(url_3)))
          } yield ()
        }

        "on the first call, return empty Option if kid is NOT present among retrieved JWKs" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
            _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))

            _ = result shouldBe None
          } yield ()
        }

        "on the first call, return JWK with matching key ID if kid is present among retrieved JWKs" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = result shouldBe defined
            _ = result.get shouldBe jsonWebKey
          } yield ()
        }

        "on the second call, return JWK with matching key ID if kid is present among retrieved JWKs" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_1) >>
                urlJwkProvider.getJsonWebKey(kid_1)
            }

            _ = result shouldBe defined
            _ = result.get shouldBe jsonWebKey
          } yield ()
        }

        "on the second call, return empty Option if provided key ID does NOT match any JWK key ID" in {
          stubUrls()

          for {
            result <- urlJwkProviderRes.use { urlJwkProvider =>
              urlJwkProvider.getJsonWebKey(kid_4) >>
                urlJwkProvider.getJsonWebKey(kid_4)
            }

            _ = result shouldBe None
          } yield ()
        }
      }
    }
  }
}
