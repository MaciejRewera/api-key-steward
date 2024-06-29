package apikeysteward.routes.auth

import apikeysteward.config.AuthConfig
import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError._
import cats.data.NonEmptyChainImpl
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import pdi.jwt.JwtHeader

import java.time.{Clock, ZoneOffset}
import scala.concurrent.duration.DurationInt

class JwtValidatorSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val authConfig = mock[AuthConfig]

  implicit private def fixedClock: Clock = Clock.fixed(nowInstant, ZoneOffset.UTC)

  private val jwtValidator = new JwtValidator(authConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(authConfig)

    authConfig.allowedIssuers returns List(issuer_1, issuer_2)
    authConfig.audience returns AuthTestData.audience_1
    authConfig.maxTokenAge returns None

    authConfig.requireExp returns true
    authConfig.requireNbf returns true
    authConfig.requireIat returns true
    authConfig.requireIss returns true
    authConfig.requireAud returns true
  }

  "JwtValidator on validateKeyId" should {

    "NOT call AuthConfig" in {
      jwtValidator.validateKeyId(jwtHeader)

      verifyZeroInteractions(authConfig)
    }

    "return Right containing JwtClaim" when {
      "provided with a token with correct kid (Key ID) claim" in {
        jwtValidator.validateKeyId(jwtHeader) shouldBe Right(jwtHeader)
      }
    }

    "return Left containing MissingKeyIdFieldError" when {

      "provided with a token without kid (Key ID) claim" in {
        jwtValidator.validateKeyId(jwtHeaderWithoutKid) shouldBe Left(MissingKeyIdFieldError)
      }

      "provided with a token with empty kid (Key ID) claim" in {
        val jwtHeaderWithEmptyKid = JwtHeader(algorithm = Some(algorithm), typ = Some("JWT"), keyId = Some(""))

        jwtValidator.validateKeyId(jwtHeaderWithEmptyKid) shouldBe Left(MissingKeyIdFieldError)
      }
    }
  }

  "JwtValidator on validateExpirationTimeClaim" when {

    "provided with a token with correct exp claim" should {
      "return Right containing JwtClaim" in {
        jwtValidator.validateExpirationTimeClaim(jwtClaim) shouldBe Right(jwtClaim)
      }
    }

    "provided with a token without exp claim" when {

      val noExpirationTimeClaim = jwtClaim.copy(expiration = None)

      "AuthConfig 'requireExp' field is set to TRUE" should {
        "return Left containing MissingExpirationTimeClaimError" in {
          authConfig.requireExp returns true

          jwtValidator.validateExpirationTimeClaim(noExpirationTimeClaim) shouldBe Left(MissingExpirationTimeClaimError)
        }
      }

      "AuthConfig 'requireExp' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireExp returns false

          jwtValidator.validateExpirationTimeClaim(noExpirationTimeClaim) shouldBe Right(noExpirationTimeClaim)
        }
      }
    }

    "provided with expired token" when {

      "AuthConfig 'requireExp' field is set to TRUE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireExp returns true

          jwtValidator.validateExpirationTimeClaim(expiredJwtClaim) shouldBe Right(expiredJwtClaim)
        }
      }

      "AuthConfig 'requireExp' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireExp returns false

          jwtValidator.validateExpirationTimeClaim(expiredJwtClaim) shouldBe Right(expiredJwtClaim)
        }
      }
    }
  }

  "JwtValidator on validateNotBeforeClaim" when {

    "provided with a token with correct nbf claim" should {
      "return Right containing JwtClaim" in {
        jwtValidator.validateNotBeforeClaim(jwtClaim) shouldBe Right(jwtClaim)
      }
    }

    "provided with a token without nbf claim" when {

      val noNotBeforeClaim = jwtClaim.copy(notBefore = None)

      "AuthConfig 'requireNbf' field is set to TRUE" should {
        "return Left containing MissingNotBeforeClaimError" in {
          authConfig.requireNbf returns true

          jwtValidator.validateNotBeforeClaim(noNotBeforeClaim) shouldBe Left(MissingNotBeforeClaimError)
        }
      }

      "AuthConfig 'requireNbf' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireNbf returns false

          jwtValidator.validateNotBeforeClaim(noNotBeforeClaim) shouldBe Right(noNotBeforeClaim)
        }
      }
    }

    "provided with a token with nbf value in the future" when {

      val noNotBeforeClaim = jwtClaim.copy(notBefore = Some(now.plus(1.minute).toSeconds))

      "AuthConfig 'requireNbf' field is set to TRUE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireNbf returns true

          jwtValidator.validateNotBeforeClaim(noNotBeforeClaim) shouldBe Right(noNotBeforeClaim)
        }
      }

      "AuthConfig 'requireNbf' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireNbf returns false

          jwtValidator.validateNotBeforeClaim(noNotBeforeClaim) shouldBe Right(noNotBeforeClaim)
        }
      }
    }
  }

  "JwtValidator on validateIssuedAtClaim" when {

    "provided with a token with correct iat claim" should {

      "return Right containing JwtClaim" when {

        "provided with a token younger than configured max token age" in {
          authConfig.maxTokenAge returns Some(5.minutes)

          jwtValidator.validateIssuedAtClaim(jwtClaim) shouldBe Right(jwtClaim)
        }

        "provided with a token of any age when max token age is NOT configured" in {
          val jwtClaimVeryOld = jwtClaim.copy(issuedAt = Some(now.minus(366.days).toSeconds))

          jwtValidator.validateIssuedAtClaim(jwtClaimVeryOld) shouldBe Right(jwtClaimVeryOld)
        }
      }
    }

    "provided with a token without iat claim" when {

      val noIssuedAtClaim = jwtClaim.copy(issuedAt = None)

      "AuthConfig 'requireIat' field is set to TRUE" should {
        "return Left containing MissingIssuedAtClaimError" in {
          authConfig.requireIat returns true

          jwtValidator.validateIssuedAtClaim(noIssuedAtClaim) shouldBe Left(MissingIssuedAtClaimError)
        }
      }

      "AuthConfig 'requireIat' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireIat returns false

          jwtValidator.validateIssuedAtClaim(noIssuedAtClaim) shouldBe Right(noIssuedAtClaim)
        }
      }
    }

    "provided with a token older than configured max token age" when {

      val jwtTooOld = jwtClaim.copy(issuedAt = Some(now.minus(61.seconds).toSeconds))

      "AuthConfig 'requireIat' field is set to TRUE" should {
        "return Left containing TokenTooOldError" in {
          authConfig.requireIat returns true
          authConfig.maxTokenAge returns Some(1.minute)

          jwtValidator.validateIssuedAtClaim(jwtTooOld) shouldBe Left(TokenTooOldError(1.minute))
        }
      }

      "AuthConfig 'requireIat' field is set to FALSE" should {
        "return Left containing TokenTooOldError" in {
          authConfig.requireIat returns false
          authConfig.maxTokenAge returns Some(1.minute)

          jwtValidator.validateIssuedAtClaim(jwtTooOld) shouldBe Left(TokenTooOldError(1.minute))
        }
      }
    }

    "provided with a token of age equal to configured max token age" when {

      "AuthConfig 'requireIat' field is set to TRUE" should {
        "return Left containing TokenTooOldError" in {
          authConfig.requireIat returns true
          authConfig.maxTokenAge returns Some(1.minute)

          jwtValidator.validateIssuedAtClaim(jwtClaim) shouldBe Left(TokenTooOldError(1.minute))
        }
      }

      "AuthConfig 'requireIat' field is set to FALSE" should {
        "return Left containing TokenTooOldError" in {
          authConfig.requireIat returns false
          authConfig.maxTokenAge returns Some(1.minute)

          jwtValidator.validateIssuedAtClaim(jwtClaim) shouldBe Left(TokenTooOldError(1.minute))
        }
      }
    }

    "provided with expired token, but with acceptable max token age" when {

      "AuthConfig 'requireIat' field is set to TRUE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireIat returns true
          authConfig.maxTokenAge returns Some(10.minutes)

          jwtValidator.validateIssuedAtClaim(expiredJwtClaim) shouldBe Right(expiredJwtClaim)
        }
      }

      "AuthConfig 'requireIat' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireIat returns false
          authConfig.maxTokenAge returns Some(10.minutes)

          jwtValidator.validateIssuedAtClaim(expiredJwtClaim) shouldBe Right(expiredJwtClaim)
        }
      }
    }
  }

  "JwtValidator on validateIssuerClaim" when {

    "provided with a token with correct iss claim" should {
      "return Right containing JwtClaim" in {
        jwtValidator.validateIssuerClaim(jwtClaim) shouldBe Right(jwtClaim)
      }
    }

    "provided with a token without any issuer" when {

      val noIssuerClaim = jwtClaim.copy(issuer = None)

      "AuthConfig 'requireIss' field is set to TRUE" should {
        "return Left containing MissingIssuerClaimError" in {
          authConfig.requireIss returns true

          jwtValidator.validateIssuerClaim(noIssuerClaim) shouldBe Left(MissingIssuerClaimError)
        }
      }

      "AuthConfig 'requireIss' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireIss returns false

          jwtValidator.validateIssuerClaim(noIssuerClaim) shouldBe Right(noIssuerClaim)
        }
      }
    }

    "provided with a token containing empty issuer claim" when {

      val emptyIssuerClaim = jwtClaim.copy(issuer = Some(""))

      "AuthConfig 'requireIss' field is set to TRUE" should {
        "return Left containing MissingIssuerClaimError" in {
          authConfig.requireIss returns true

          jwtValidator.validateIssuerClaim(emptyIssuerClaim) shouldBe Left(MissingIssuerClaimError)
        }
      }

      "AuthConfig 'requireIss' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireIss returns false

          jwtValidator.validateIssuerClaim(emptyIssuerClaim) shouldBe Right(emptyIssuerClaim)
        }
      }
    }

    "provided with a token with not supported issuer" when {

      val notSupportedIssuerClaim = jwtClaim.copy(issuer = Some(issuer_3))

      "AuthConfig 'requireIss' field is set to TRUE" should {
        "return Left containing IncorrectIssuerClaimError" in {
          authConfig.requireIss returns true

          jwtValidator.validateIssuerClaim(notSupportedIssuerClaim) shouldBe Left(IncorrectIssuerClaimError(issuer_3))
        }
      }

      "AuthConfig 'requireIss' field is set to FALSE" should {
        "return Left containing IncorrectIssuerClaimError" in {
          authConfig.requireIss returns false

          jwtValidator.validateIssuerClaim(notSupportedIssuerClaim) shouldBe Left(IncorrectIssuerClaimError(issuer_3))
        }
      }
    }
  }

  "JwtValidator on validateAudienceClaim" when {

    "provided with a token with correct aud claim" should {
      "return Right containing JwtClaim" in {
        jwtValidator.validateAudienceClaim(jwtClaim) shouldBe Right(jwtClaim)
      }
    }

    "provided with a token without any audience" when {

      val noAudienceClaim = jwtClaim.copy(audience = None)

      "AuthConfig 'requireAud' field is set to TRUE" should {
        "return Left containing MissingAudienceClaimError" in {
          authConfig.requireAud returns true

          jwtValidator.validateAudienceClaim(noAudienceClaim) shouldBe Left(MissingAudienceClaimError)
        }
      }

      "AuthConfig 'requireAud' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireAud returns false

          jwtValidator.validateAudienceClaim(noAudienceClaim) shouldBe Right(noAudienceClaim)
        }
      }
    }

    "provided with a token containing empty audience claim" when {

      val noAudienceClaim = jwtClaim.copy(audience = Some(Set.empty))

      "AuthConfig 'requireAud' field is set to TRUE" should {
        "return Left containing MissingAudienceClaimError" in {
          authConfig.requireAud returns true

          jwtValidator.validateAudienceClaim(noAudienceClaim) shouldBe Left(MissingAudienceClaimError)
        }
      }

      "AuthConfig 'requireAud' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireAud returns false

          jwtValidator.validateAudienceClaim(noAudienceClaim) shouldBe Right(noAudienceClaim)
        }
      }
    }

    "provided with a token containing empty String as audience" when {

      val noAudienceClaim = jwtClaim.copy(audience = Some(Set("")))

      "AuthConfig 'requireAud' field is set to TRUE" should {
        "return Left containing MissingAudienceClaimError" in {
          authConfig.requireAud returns true

          jwtValidator.validateAudienceClaim(noAudienceClaim) shouldBe Left(MissingAudienceClaimError)
        }
      }

      "AuthConfig 'requireAud' field is set to FALSE" should {
        "return Right containing JwtClaim" in {
          authConfig.requireAud returns false

          jwtValidator.validateAudienceClaim(noAudienceClaim) shouldBe Right(noAudienceClaim)
        }
      }
    }

    "provided with a token without required audience" when {

      val incorrectAudience = Set(AuthTestData.audience_2, "some-other-audience-1", "some-other-audience-2")
      val incorrectAudienceClaim = jwtClaim.copy(audience = Some(incorrectAudience))

      "AuthConfig 'requireAud' field is set to TRUE" should {
        "return Left containing IncorrectAudienceClaimError" in {
          authConfig.requireAud returns true

          val result = jwtValidator.validateAudienceClaim(incorrectAudienceClaim)

          result shouldBe Left(IncorrectAudienceClaimError(incorrectAudience))
        }
      }

      "AuthConfig 'requireAud' field is set to FALSE" should {
        "return Left containing IncorrectAudienceClaimError" in {
          authConfig.requireAud returns false

          val result = jwtValidator.validateAudienceClaim(incorrectAudienceClaim)

          result shouldBe Left(IncorrectAudienceClaimError(incorrectAudience))
        }
      }
    }
  }

  "JwtValidator on validateAll" should {

    "return Right containing JsonWebToken" when {
      "provided with correct token" in {
        jwtValidator.validateAll(jwtWithMockedSignature) shouldBe Right(jwtWithMockedSignature)
      }
    }

    "return Left containing errors" when {

      "there is a single error during token validation" in {
        val jwtSingleError = jwtWithMockedSignature.copy(header = jwtHeaderWithoutKid)

        jwtValidator.validateAll(jwtSingleError) shouldBe Left(NonEmptyChainImpl.one(MissingKeyIdFieldError))
      }

      "there are multiple errors during token validation" in {
        val jwtMultipleErrors = jwtWithMockedSignature.copy(
          header = jwtHeaderWithoutKid,
          claim = jwtClaim.copy(
            issuer = None,
            audience = None,
            expiration = None
          )
        )
        val expectedErrors = Seq(
          MissingKeyIdFieldError,
          MissingIssuerClaimError,
          MissingAudienceClaimError,
          MissingExpirationTimeClaimError
        )

        val result = jwtValidator.validateAll(jwtMultipleErrors)

        result.isLeft shouldBe true
        result.left.value.iterator.toList should contain theSameElementsAs expectedErrors
      }
    }
  }

}
