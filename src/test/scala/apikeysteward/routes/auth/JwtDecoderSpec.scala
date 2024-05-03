package apikeysteward.routes.auth

import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.PublicKeyGenerator.{
  AlgorithmNotSupportedError,
  KeyTypeNotSupportedError,
  KeyUseNotSupportedError
}
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import pdi.jwt.exceptions.JwtExpirationException

class JwtDecoderSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val jwkProvider = mock[JwkProvider]
  private val publicKeyGenerator = mock[PublicKeyGenerator]

  private val jwtDecoder = new JwtDecoder(jwkProvider, publicKeyGenerator)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwkProvider, publicKeyGenerator)
  }

  "JwtDecoder on decode" when {

    "everything works correctly" should {

      "call JwkProvider providing Key Id from the token" in IO {}

      "call PublicKeyGenerator providing JWK from JwkProvider" in IO {}

      "return correct JsonWebToken" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(jsonWebKey)
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Right(publicKey)

        jwtDecoder.decode(jwtString).asserting { result =>
          result shouldBe JsonWebToken(
            content = jwtString,
            jwtHeader = jwtHeader,
            jwtClaim = jwtClaim,
            signature = result.signature
          )
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

      "return failed IO" in {
        jwtDecoder.decode(expiredJwtString).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[JwtExpirationException]
          result.left.value.getMessage should include("The token is expired since ")
        }
      }
    }

    "JwkProvider returns failed IO" should {

      "NOT call PublicKeyGenerator" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.raiseError(new RuntimeException("Test Exception"))

        for {
          _ <- jwtDecoder.decode(jwtString).attempt

          _ = verifyZeroInteractions(publicKeyGenerator)
        } yield ()
      }

      "return failed IO containing the same error" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.raiseError(new RuntimeException("Test Exception"))

        jwtDecoder.decode(jwtString).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[RuntimeException]
          result.left.value.getMessage shouldBe "Test Exception"
        }
      }
    }

    "PublicKeyGenerator returns Left containing errors" should {

      "return failed IO containing IllegalArgumentException" in {
        jwkProvider.getJsonWebKey(any[String]) returns IO.pure(jsonWebKey)
        publicKeyGenerator.generateFrom(any[JsonWebKey]) returns Left(
          NonEmptyChain(
            AlgorithmNotSupportedError("RS256", "HS256"),
            KeyTypeNotSupportedError("RSA", "HSA"),
            KeyUseNotSupportedError("sig", "no-use")
          )
        )

        jwtDecoder.decode(jwtString).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe an[IllegalArgumentException]

          result.left.value.getMessage should include("Cannot generate Public Key because: [")
          result.left.value.getMessage should include(AlgorithmNotSupportedError("RS256", "HS256").message)
          result.left.value.getMessage should include(KeyTypeNotSupportedError("RSA", "HSA").message)
          result.left.value.getMessage should include(KeyUseNotSupportedError("sig", "no-use").message)
          result.left.value.getMessage should include(s"]. Provided JWK: $jsonWebKey")
        }
      }
    }
  }

}
