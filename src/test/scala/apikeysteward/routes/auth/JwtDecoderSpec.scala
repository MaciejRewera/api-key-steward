package apikeysteward.routes.auth

import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.JwtDecoder._
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError._
import apikeysteward.routes.auth.PublicKeyGenerator._
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebToken, JwtClaimCustom, JwtCustom}
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import scala.concurrent.duration.DurationInt

class JwtDecoderSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val jwtValidator = mock[JwtValidator]
  private val jwkProvider = mock[JwkProvider]
  private val publicKeyGenerator = mock[PublicKeyGenerator]
  private val jwtDecoder = new JwtDecoder(jwtValidator, jwkProvider, publicKeyGenerator)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwtValidator, jwkProvider, publicKeyGenerator)

    jwkProvider.getJsonWebKey(any[String]) returns IO.pure(Some(jsonWebKey))
    publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

    jwtValidator.validateAll(any[JsonWebToken]) returns Right(jwtWithMockedSignature)
  }

  private def jwtWithClaimString(jwtClaim: JwtClaimCustom): String =
    JwtCustom.encode(jwtHeader, jwtClaim, privateKey)

  private val testException = new RuntimeException("Test Exception")

  "JwtDecoder on decode" when {

    "everything works correctly" should {

      "call JwtValidator providing decoded token" in {
        for {
          jwt <- jwtDecoder.decode(jwtString)

          _ = verify(jwtValidator).validateAll(
            eqTo(jwtWithMockedSignature.copy(signature = jwt.toOption.get.signature))
          )
        } yield ()
      }

      "call JwkProvider providing Key Id from the token" in {
        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(jwkProvider).getJsonWebKey(eqTo(kid_1))
        } yield ()
      }

      "call PublicKeyGenerator providing JWK from JwkProvider" in {
        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verify(publicKeyGenerator).generateFrom(eqTo(jsonWebKey))
        } yield ()
      }

      "return Right containing JsonWebToken" in {
        jwtDecoder.decode(jwtString).asserting { result =>
          result shouldBe Right(jwtWithMockedSignature.copy(signature = result.value.signature))
        }
      }
    }

    "provided with expired token" should {

      "NOT call either JwtValidator, JwkProvider, nor PublicKeyGenerator" in {
        for {
          _ <- jwtDecoder.decode(expiredJwtString).attempt

          _ = verifyZeroInteractions(jwtValidator, jwkProvider, publicKeyGenerator)
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

    "provided with a token with nbf value in the future" should {

      val jwtClaimNbfInTheFuture = jwtClaim.copy(notBefore = Some(now.plus(1.minute).toSeconds))
      val jwtNbfInTheFutureString = JwtCustom.encode(jwtHeader, jwtClaimNbfInTheFuture, privateKey)

      "NOT call either JwtValidator, JwkProvider, nor PublicKeyGenerator" in {
        for {
          _ <- jwtDecoder.decode(jwtNbfInTheFutureString).attempt
          _ = verifyZeroInteractions(jwtValidator, jwkProvider, publicKeyGenerator)
        } yield ()
      }

      "return Left containing DecodingError" in {
        jwtDecoder.decode(jwtNbfInTheFutureString).asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[DecodingError]
          result.left.value.message should include("Exception occurred while decoding JWT: ")
          result.left.value.message should include("The token will only be valid after ")
        }
      }
    }

    "provided with a token without kid (Key ID)" should {

      "call JwtValidator" in {
        val jwtCaptor: ArgumentCaptor[JsonWebToken] = ArgumentCaptor.forClass(classOf[JsonWebToken])

        for {
          _ <- jwtDecoder.decode(jwtWithoutKidString)

          _ = verify(jwtValidator).validateAll(jwtCaptor.capture)
          jwt = jwtCaptor.getValue
          _ = jwt.content shouldBe jwtWithoutKidString
          _ = jwt.header shouldBe jwtHeaderWithoutKid
          _ = jwt.claim shouldBe jwtClaim
        } yield ()
      }

      "NOT call either JwkProvider, nor PublicKeyGenerator" in {
        for {
          _ <- jwtDecoder.decode(jwtWithoutKidString)
          _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
        } yield ()
      }

      "return Left containing ValidationError" in {
        jwtDecoder.decode(jwtWithoutKidString).asserting(_ shouldBe Left(ValidationError(MissingKeyIdFieldError)))
      }
    }

    "JwtValidator returns Left containing JwtValidatorError" should {

      "NOT call either JwkProvider, nor PublicKeyGenerator" when {

        "jwtValidator.validateKeyId returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingKeyIdFieldError))

          for {
            _ <- jwtDecoder.decode(jwtWithoutKidString)
            _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
          } yield ()
        }

        "jwtValidator.validateExpirationTimeClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingExpirationTimeClaimError))

          for {
            _ <- jwtDecoder.decode(jwtWithClaimString(jwtClaim.copy(expiration = None)))
            _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
          } yield ()
        }

        "jwtValidator.validateNotBeforeClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingNotBeforeClaimError))

          for {
            _ <- jwtDecoder.decode(jwtWithClaimString(jwtClaim.copy(notBefore = None)))
            _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
          } yield ()
        }

        "jwtValidator.validateIssuedAtClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingIssuedAtClaimError))

          for {
            _ <- jwtDecoder.decode(jwtWithClaimString(jwtClaim.copy(issuedAt = None)))
            _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
          } yield ()
        }

        "jwtValidator.validateIssuerClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingIssuerClaimError))

          for {
            _ <- jwtDecoder.decode(jwtWithClaimString(jwtClaim.copy(issuer = None)))
            _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
          } yield ()
        }

        "jwtValidator.validateAudienceClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingAudienceClaimError))

          for {
            _ <- jwtDecoder.decode(jwtWithClaimString(jwtClaim.copy(audience = None)))
            _ = verifyZeroInteractions(jwkProvider, publicKeyGenerator)
          } yield ()
        }
      }

      "return Left containing ValidationError" when {

        "jwtValidator.validateKeyId returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingKeyIdFieldError))

          jwtDecoder.decode(jwtWithoutKidString).asserting(_ shouldBe Left(ValidationError(MissingKeyIdFieldError)))
        }

        "jwtValidator.validateExpirationTimeClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingExpirationTimeClaimError))

          jwtDecoder
            .decode(jwtWithClaimString(jwtClaim.copy(expiration = None)))
            .asserting(_ shouldBe Left(ValidationError(MissingExpirationTimeClaimError)))
        }

        "jwtValidator.validateNotBeforeClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingNotBeforeClaimError))

          jwtDecoder
            .decode(jwtWithClaimString(jwtClaim.copy(notBefore = None)))
            .asserting(_ shouldBe Left(ValidationError(MissingNotBeforeClaimError)))
        }

        "jwtValidator.validateIssuedAtClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingIssuedAtClaimError))

          jwtDecoder
            .decode(jwtWithClaimString(jwtClaim.copy(issuedAt = None)))
            .asserting(_ shouldBe Left(ValidationError(MissingIssuedAtClaimError)))
        }

        "jwtValidator.validateIssuerClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingIssuerClaimError))

          jwtDecoder
            .decode(jwtWithClaimString(jwtClaim.copy(issuer = None)))
            .asserting(_ shouldBe Left(ValidationError(MissingIssuerClaimError)))
        }

        "jwtValidator.validateAudienceClaim returns error" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(NonEmptyChain(MissingAudienceClaimError))

          jwtDecoder
            .decode(jwtWithClaimString(jwtClaim.copy(audience = None)))
            .asserting(_ shouldBe Left(ValidationError(MissingAudienceClaimError)))
        }
      }

      "return Left containing multiple ValidationErrors" when {
        "JwtValidator returns errors for several calls" in {
          jwtValidator.validateAll(any[JsonWebToken]) returns Left(
            NonEmptyChain(
              MissingKeyIdFieldError,
              MissingExpirationTimeClaimError,
              MissingNotBeforeClaimError,
              MissingIssuedAtClaimError,
              MissingIssuerClaimError,
              MissingAudienceClaimError
            )
          )

          val validationErrors = Seq(
            MissingKeyIdFieldError,
            MissingExpirationTimeClaimError,
            MissingNotBeforeClaimError,
            MissingIssuedAtClaimError,
            MissingIssuerClaimError,
            MissingAudienceClaimError
          )

          jwtDecoder.decode(jwtString).asserting(_ shouldBe Left(ValidationError(validationErrors)))
        }
      }
    }

    "JwkProvider returns empty Option" should {

      "NOT call PublicKeyGenerator" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(None)

        for {
          _ <- jwtDecoder.decode(jwtString)
          _ = verifyZeroInteractions(publicKeyGenerator)
        } yield ()
      }

      "return Left containing MatchingJwkNotFoundError" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(None)

        jwtDecoder.decode(jwtString).asserting(_ shouldBe Left(MatchingJwkNotFoundError(kid_1)))
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

      "have called JwkProvider" in {
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

        jwtDecoder.decode(jwtString).asserting(_ shouldBe Left(PublicKeyGenerationError(failureReasons.iterator.toSeq)))
      }
    }
  }

}
