package apikeysteward.routes.auth

import apikeysteward.routes.auth.AuthTestData.jsonWebKey
import apikeysteward.routes.auth.PublicKeyGenerator._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pdi.jwt.JwtBase64

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec

class PublicKeyGeneratorSpec extends AnyWordSpec with Matchers {

  private val publicKeyGenerator = new PublicKeyGenerator

  private val SupportedAlgorithm = "RS256"
  private val SupportedKeyType = "RSA"
  private val SupportedKeyUse = "sig"

  "PublicKeyGenerator on generateFrom" should {

    "return Left containing errors" when {

      "provided with Json Web Key with empty algorithm (alg field)" in {
        val jwk = jsonWebKey.copy(alg = None)

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe AlgorithmNotProvidedError
        }
      }

      "provided with Json Web Key with incorrect algorithm (alg field)" in {
        val jwk = jsonWebKey.copy(alg = Some("HS256"))

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe AlgorithmNotSupportedError(SupportedAlgorithm, "HS256")
        }
      }

      "provided with Json Web Key with incorrect key type (kty field)" in {
        val jwk = jsonWebKey.copy(kty = "HSA")

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe KeyTypeNotSupportedError(SupportedKeyType, "HSA")
        }
      }

      "provided with Json Web Key with incorrect use (use field)" in {
        val jwk = jsonWebKey.copy(use = "no-use")

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe KeyUseNotSupportedError(SupportedKeyUse, "no-use")
        }
      }

      "provided with all 3 incorrect fields" in {
        val jwk = jsonWebKey.copy(alg = Some("HS256"), kty = "HSA", use = "no-use")

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 3
          errors.toChain.toList should contain theSameElementsAs Seq(
            AlgorithmNotSupportedError(SupportedAlgorithm, "HS256"),
            KeyTypeNotSupportedError(SupportedKeyType, "HSA"),
            KeyUseNotSupportedError(SupportedKeyUse, "no-use")
          )
        }
      }
    }

    "return Right containing PublicKey" when {

      "provided with correct Json Web Key" in {
        val result = publicKeyGenerator.generateFrom(jsonWebKey)

        val keySpec = new RSAPublicKeySpec(
          new BigInteger(1, JwtBase64.decode(jsonWebKey.n)),
          new BigInteger(1, JwtBase64.decode(jsonWebKey.e))
        )
        val expectedPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        result.isRight shouldBe true
        result.map(_ shouldBe expectedPublicKey)
      }
    }
  }
}
