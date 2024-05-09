package apikeysteward.routes.auth

import apikeysteward.config.JwksConfig
import apikeysteward.routes.auth.AuthTestData._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.client.WireMock._
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

  private val url = "/.well-known/jwks.json"
  private val jwksConfig =
    JwksConfig(url = wireMockUri.addPath(url.replaceFirst("/", "")), cacheRefreshPeriod = 10.minutes)

  private val urlJwkProviderRes: Resource[IO, UrlJwkProvider] =
    BlazeClientBuilder[IO].resource.map(new UrlJwkProvider(jwksConfig, _))

  "UrlJwkProvider on getJsonWebKey" should {

    "call provided auth jwks url" in {
      stubFor(
        get(url)
          .willReturn(
            aResponse()
              .withStatus(StatusCode.Ok.code)
              .withBody("""{"keys": []}""")
          )
      )

      for {
        _ <- urlJwkProviderRes.use(_.getJsonWebKey(kid_1))
        _ = verify(getRequestedFor(urlEqualTo(url)))
      } yield ()
    }
  }

  "UrlJwkProvider on getJsonWebKey" when {

    "fetched JWKS contains NO JWKs" should {
      "return empty Option" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody("""{"keys": []}""")
            )
        )

        urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting(_ shouldBe empty)
      }
    }

    "fetched JWKS contains a single JWK" should {
      val responseJson =
        s"""{
          "keys": [
            {
              "alg": "RS256",
              "kty": "RSA",
              "use": "sig",
              "n": "$encodedModulus",
              "e": "$encodedExponent",
              "kid": "${jsonWebKey.kid}",
              "x5t": "${jsonWebKey.x5t.get}",
              "x5c": ["${jsonWebKey.x5c.get.head}"]
            }
          ]
        }""".stripMargin

      "return this JWK if key IDs match" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody(parser.parse(responseJson).value.spaces2)
            )
        )

        urlJwkProviderRes.use(_.getJsonWebKey(kid_1)).asserting { res =>
          res shouldBe defined
          res.get shouldBe jsonWebKey
        }
      }

      "return empty Option if key IDs do NOT match" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody(parser.parse(responseJson).value.spaces2)
            )
        )

        urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting(res => res shouldBe empty)
      }
    }

    "fetched JWKS contains multiple JWKs" should {
      val responseJson =
        s"""{
          "keys": [
            {
              "alg": "RS256",
              "kty": "RSA",
              "use": "sig",
              "n": "$encodedModulus",
              "e": "$encodedExponent",
              "kid": "$kid_1",
              "x5t": "${jsonWebKey.x5t.get}",
              "x5c": ["${jsonWebKey.x5c.get.head}"]
            },
            {
              "alg": "RS256",
              "kty": "RSA",
              "use": "sig",
              "n": "$encodedModulus",
              "e": "$encodedExponent",
              "kid": "$kid_2",
              "x5t": "${jsonWebKey.x5t.get}",
              "x5c": ["${jsonWebKey.x5c.get.head}"]
            },
            {
              "alg": "RS256",
              "kty": "RSA",
              "use": "sig",
              "n": "$encodedModulus",
              "e": "$encodedExponent",
              "kid": "$kid_3",
              "x5t": "${jsonWebKey.x5t.get}",
              "x5c": ["${jsonWebKey.x5c.get.head}"]
            }
          ]
        }""".stripMargin

      "return JWK with matching key ID" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody(parser.parse(responseJson).value.spaces2)
            )
        )

        urlJwkProviderRes.use(_.getJsonWebKey(kid_2)).asserting { res =>
          res shouldBe defined
          res.get shouldBe jsonWebKey.copy(kid = kid_2)
        }
      }

      "return empty Option if provided key ID does NOT match any JWK key ID" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody(parser.parse(responseJson).value.spaces2)
            )
        )

        urlJwkProviderRes.use(_.getJsonWebKey(kid_4)).asserting(res => res shouldBe empty)
      }
    }

    "called several times" should {

      "call provided url only once" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody("""{"keys": []}""")
            )
        )

        for {
          _ <- urlJwkProviderRes.use { urlJwkProvider =>
            urlJwkProvider.getJsonWebKey(kid_1) >>
              urlJwkProvider.getJsonWebKey(kid_2) >>
              urlJwkProvider.getJsonWebKey(kid_3) >>
              urlJwkProvider.getJsonWebKey(kid_4)
          }

          _ = verify(1, getRequestedFor(urlEqualTo(url)))
        } yield ()
      }

      "call provided url again after cache expiry period" in {
        stubFor(
          get(url)
            .willReturn(
              aResponse()
                .withStatus(StatusCode.Ok.code)
                .withBody("""{"keys": []}""")
            )
        )

        val cacheRefreshPeriod = 100.milliseconds
        val sleepDuration = cacheRefreshPeriod.plus(10.millisecond)
        val jwksConfig = JwksConfig(
          url = wireMockUri.addPath(url.replaceFirst("/", "")),
          cacheRefreshPeriod = cacheRefreshPeriod
        )

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

          _ = verify(3, getRequestedFor(urlEqualTo(url)))
        } yield ()
      }
    }
  }
}
