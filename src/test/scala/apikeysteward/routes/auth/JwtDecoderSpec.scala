package apikeysteward.routes.auth

import apikeysteward.config.AuthConfig
import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.JwtDecoder._
import apikeysteward.routes.auth.PublicKeyGenerator._
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebToken, JwtCustom}
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.time.{Clock, ZoneOffset}
import scala.concurrent.duration.DurationInt

class JwtDecoderSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val jwkProvider = mock[JwkProvider]
  private val publicKeyGenerator = mock[PublicKeyGenerator]
  private val authConfig = mock[AuthConfig]

  implicit private def fixedClock: Clock = Clock.fixed(nowInstant, ZoneOffset.UTC)

  private val jwtDecoder = new JwtDecoder(jwkProvider, publicKeyGenerator, authConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwkProvider, publicKeyGenerator, authConfig)

    authConfig.allowedIssuers returns List(issuer_1, issuer_2)
    authConfig.audience returns AuthTestData.audience_1
    authConfig.maxTokenAge returns None
  }

  private val testException = new RuntimeException("Test Exception")

  "JwtDecoder on decode" when {

    "everything works correctly" should {

      "call JwkProvider providing Key Id from the token" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(jwkProvider).getJsonWebKey(eqTo(kid_1))
        } yield ()
      }

      "call PublicKeyGenerator providing JWK from JwkProvider" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(publicKeyGenerator).generateFrom(eqTo(jsonWebKey))
        } yield ()
      }

      "return Right containing JsonWebToken" when {

        "provided with a token younger than configured max token age" in {
          authConfig.maxTokenAge returns Some(5.minutes)
          jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
          publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

          jwtDecoder.decode(jwtString).asserting { result =>
            result shouldBe Right(jwtWithMockedSignature.copy(signature = result.value.signature))
          }
        }

        "provided with a token of any age when max token age is NOT configured" in {
          jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
          publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

          val jwtClaimVeryOld = jwtClaim.copy(issuedAt = Some(now.minus(366.days).toSeconds))
          val jwtYoungEnoughString = JwtCustom.encode(jwtHeader, jwtClaimVeryOld, privateKey)
          val expectedJwt = JsonWebToken(
            content = jwtYoungEnoughString,
            jwtHeader = jwtHeader,
            jwtClaim = jwtClaimVeryOld,
            signature = "test-signature"
          )

          jwtDecoder.decode(jwtYoungEnoughString).asserting { result =>
            result shouldBe Right(expectedJwt.copy(signature = result.value.signature))
          }
        }
      }
    }

    "provided with expired token" should {

      "NOT call either JwkProvider, nor PublicKeyGenerator" in {
        for {
          _ <- jwtDecoder.decode(expiredJwtString).attempt
          _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
        } yield ()
      }

      "return Left containing DecodingError" in {
        jwtDecoder.decode(expiredJwtString).asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[DecodingError]
          result.left.value.message should include("Exception occurred while decoding JWT: ")
          result.left.value.message should include("The token is expired since ")
        }
      }
    }

    "provided with a token without exp claim" should {
      "return Left containing DecodingError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtTooOldString = JwtCustom.encode(jwtHeader, jwtClaim.copy(expiration = None), privateKey)

        jwtDecoder.decode(jwtTooOldString).asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[DecodingError]
          result.left.value.message should include("Exception occurred while decoding JWT: ")
          result.left.value.message should include("The token is expired since ")
        }
      }
    }

    "provided with a token without iat claim" should {
      "return Left containing MissingIssuedAtClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtTooOldString = JwtCustom.encode(jwtHeader, jwtClaim.copy(issuedAt = None), privateKey)

        jwtDecoder.decode(jwtTooOldString).asserting(_ shouldBe Left(MissingIssuedAtClaimError))
      }
    }

    "provided with a token older than configured max token age" should {
      "return Left containing TokenTooOldError" in {
        authConfig.maxTokenAge returns Some(1.minute)
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtTooOldString =
          JwtCustom.encode(
            jwtHeader,
            jwtClaim.copy(issuedAt = Some(now.minus(61.seconds).toSeconds)),
            privateKey
          )

        jwtDecoder.decode(jwtTooOldString).asserting(_ shouldBe Left(TokenTooOldError(1.minute)))
      }
    }

    "provided with a token of age equal to configured max token age" should {
      "return Left containing TokenTooOldError" in {
        authConfig.maxTokenAge returns Some(1.minute)
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        jwtDecoder.decode(jwtString).asserting(_ shouldBe Left(TokenTooOldError(1.minute)))
      }
    }

    "provided with expired token, but with acceptable max token age" should {
      "return JwtExpiredException" in {
        authConfig.maxTokenAge returns Some(10.minutes)
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        jwtDecoder.decode(expiredJwtString).asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[DecodingError]
          result.left.value.message should include("Exception occurred while decoding JWT: ")
          result.left.value.message should include("The token is expired since ")
        }
      }
    }

    "provided with a token without kid (Key ID)" should {

      "NOT call either JwkProvider, nor PublicKeyGenerator" in {
        for {
          _ <- jwtDecoder.decode(jwtWithoutKidString).attempt
          _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
        } yield ()
      }

      "return Left containing MissingKeyIdFieldError" in {
        jwtDecoder.decode(jwtWithoutKidString).asserting { result =>
          result shouldBe Left(MissingKeyIdFieldError(jwtWithoutKidString))
        }
      }
    }

    "provided with a token without any issuer" should {
      "return Left containing MissingIssuerClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtWithoutIssuerString = JwtCustom.encode(jwtHeader, jwtClaim.copy(issuer = None), privateKey)

        jwtDecoder.decode(jwtWithoutIssuerString).asserting(result => result shouldBe Left(MissingIssuerClaimError))
      }
    }

    "provided with a token containing empty issuer claim" should {
      "return Left containing MissingIssuerClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtWithoutIssuerString = JwtCustom.encode(jwtHeader, jwtClaim.copy(issuer = Some("")), privateKey)

        jwtDecoder.decode(jwtWithoutIssuerString).asserting(result => result shouldBe Left(MissingIssuerClaimError))
      }
    }

    "provided with a token with not supported issuer" should {
      "return Left containing IncorrectIssuerClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtWithoutIssuerString = JwtCustom.encode(jwtHeader, jwtClaim.copy(issuer = Some(issuer_3)), privateKey)

        jwtDecoder.decode(jwtWithoutIssuerString).asserting { result =>
          result shouldBe Left(IncorrectIssuerClaimError(issuer_3))
        }
      }
    }

    "provided with a token without any audience" should {
      "return Left containing MissingAudienceClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtWithoutAudienceString = JwtCustom.encode(jwtHeader, jwtClaim.copy(audience = None), privateKey)

        jwtDecoder.decode(jwtWithoutAudienceString).asserting { result =>
          result shouldBe Left(MissingAudienceClaimError)
        }
      }
    }

    "provided with a token containing empty audience claim" should {
      "return Left containing MissingAudienceClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val jwtWithoutAudienceString =
          JwtCustom.encode(jwtHeader, jwtClaim.copy(audience = Some(Set.empty)), privateKey)

        jwtDecoder.decode(jwtWithoutAudienceString).asserting { result =>
          result shouldBe Left(MissingAudienceClaimError)
        }
      }
    }

    "provided with a token without required audience" should {
      "return Left containing IncorrectAudienceClaimError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)
        val incorrectAudience = Set(AuthTestData.audience_2, "some-other-audience-1", "some-other-audience-2")
        val jwtWithoutAudienceString =
          JwtCustom.encode(jwtHeader, jwtClaim.copy(audience = Some(incorrectAudience)), privateKey)

        jwtDecoder.decode(jwtWithoutAudienceString).asserting { result =>
          result shouldBe Left(IncorrectAudienceClaimError(incorrectAudience))
        }
      }
    }

    "JwkProvider returns empty Option" should {

      "NOT call PublicKeyGenerator" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(None)

        for {
          _ <- jwtDecoder.decode(jwtString).attempt
          _ = verifyZeroInteractions(publicKeyGenerator)
        } yield ()
      }

      "return Left containing MatchingJwkNotFoundError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(None)

        jwtDecoder.decode(jwtString).asserting(result => result shouldBe Left(MatchingJwkNotFoundError(kid_1)))
      }
    }

    "JwkProvider returns failed IO" should {

      "NOT call PublicKeyGenerator" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.raiseError(testException)

        for {
          _ <- jwtDecoder.decode(jwtString).attempt
          _ = verifyZeroInteractions(publicKeyGenerator)
        } yield ()
      }

      "return failed IO containing the same error" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.raiseError(testException)

        jwtDecoder.decode(jwtString).attempt.asserting(result => result shouldBe Left(testException))
      }
    }

    "PublicKeyGenerator returns Left containing errors" should {

      "call JwkProvider" in {
        val failureReasons = NonEmptyChain(
          AlgorithmNotSupportedError("RS256", "HS256"),
          KeyTypeNotSupportedError("RSA", "HSA"),
          KeyUseNotSupportedError("sig", "no-use")
        )
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Left(failureReasons)

        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(jwkProvider).getJsonWebKey(eqTo(kid_1))
        } yield ()
      }

      "return Left containing PublicKeyGenerationError" in {
        val failureReasons = NonEmptyChain(
          AlgorithmNotSupportedError("RS256", "HS256"),
          KeyTypeNotSupportedError("RSA", "HSA"),
          KeyUseNotSupportedError("sig", "no-use")
        )
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Left(failureReasons)

        jwtDecoder.decode(jwtString).asserting { result =>
          result shouldBe Left(PublicKeyGenerationError(failureReasons.iterator.toSeq))
        }
      }
    }
  }

}
