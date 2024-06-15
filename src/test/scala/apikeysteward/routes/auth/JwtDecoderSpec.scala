package apikeysteward.routes.auth

import apikeysteward.config.AuthConfig
import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.JwtDecoder._
import apikeysteward.routes.auth.PublicKeyGenerator._
import apikeysteward.routes.auth.model.{JsonWebKey, JwtCustom}
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

class JwtDecoderSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val jwkProvider = mock[JwkProvider]
  private val publicKeyGenerator = mock[PublicKeyGenerator]
  private val authConfig = mock[AuthConfig]

  private val jwtDecoder = new JwtDecoder(jwkProvider, publicKeyGenerator, authConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwkProvider, publicKeyGenerator, authConfig)
  }

  private val testException = new RuntimeException("Test Exception")

  "JwtDecoder on decode" when {

    "everything works correctly" should {

      "call JwkProvider providing Key Id from the token" in {
        authConfig.audience returns AuthTestData.audience_1
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(jwkProvider).getJsonWebKey(eqTo(kid_1))
        } yield ()
      }

      "call PublicKeyGenerator providing JWK from JwkProvider" in {
        authConfig.audience returns AuthTestData.audience_1
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(publicKeyGenerator).generateFrom(eqTo(jsonWebKey))
        } yield ()
      }

      "return correct JsonWebToken" in {
        authConfig.audience returns AuthTestData.audience_1
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        jwtDecoder.decode(jwtString).asserting { result =>
          result shouldBe Right(jwtWithMockedSignature.copy(signature = result.value.signature))
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

    "provided with a token containing empty audience" should {
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
        authConfig.audience returns AuthTestData.audience_1
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
