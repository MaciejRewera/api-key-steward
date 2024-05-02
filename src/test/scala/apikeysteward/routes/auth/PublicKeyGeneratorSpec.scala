package apikeysteward.routes.auth

import apikeysteward.config.AuthConfig
import apikeysteward.routes.auth.PublicKeyGenerator._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pdi.jwt.JwtBase64

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec

class PublicKeyGeneratorSpec extends AnyWordSpec with Matchers {

  private val authConfig = AuthConfig(
    supportedAlgorithm = "RS256",
    supportedKeyType = "RSA",
    supportedKeyUse = "sig"
  )

  private val publicKeyGenerator = new PublicKeyGenerator(authConfig)

  private val correctJwk = JsonWebKey(
    alg = Some("RS256"),
    kty = "RSA",
    use = "sig",
    n =
      "s3rKaN2I71o7qWkTERoC04jCvZU6MgU7r6B1w-qxg4h_kBWH4kP6sCKlyakMYSNyZLPJRVuKKQRpor2TdR18WXEQhbZtPfLxDQ_NogVCfWcTgkIpknXYoyA_PNlfcZ7DCOpG-1Ep4HR2yrA0bQ2RgidIqrf4CUSJTZcgTJ_f5d0i9_wm5alsfx7dXa7e7DHSkDYWcE3XS3i9186k2U2wo9xbu-vpc-BlTLCpJSnz9AjopmkwOCdi3DHSs5n25P0467SVN5_Q3diklTUZqARvW60H8IgbA9YY1NEisZOt74jajcwBKxSxiA_dzneAh5idrFVWoZq4MEzBDbsZo8tyew",
    e = "AQAB",
    kid = "test-key-id-1",
    x5t = Some("jRYd85jYya3Ve"),
    x5c = Some(Seq("nedesMA0GCSqGSIb3DQEBCwUAMCwxKjAoBgNVBAMTIWRldi1oeDFpMjh4eHZhcWY"))
  )

  "PublicKeyGenerator on generateFrom" should {

    "return Left containing errors" when {

      "provided with Json Web Key with empty algorithm (alg field)" in {
        val jwk = correctJwk.copy(alg = None)

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe AlgorithmNotProvidedError
        }
      }

      "provided with Json Web Key with incorrect algorithm (alg field)" in {
        val jwk = correctJwk.copy(alg = Some("HS256"))

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe AlgorithmNotSupportedError(authConfig.supportedAlgorithm, "HS256")
        }
      }

      "provided with Json Web Key with incorrect key type (kty field)" in {
        val jwk = correctJwk.copy(kty = "HSA")

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe KeyTypeNotSupportedError(authConfig.supportedKeyType, "HSA")
        }
      }

      "provided with Json Web Key with incorrect use (use field)" in {
        val jwk = correctJwk.copy(use = "no-use")

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 1
          errors.head shouldBe KeyUseNotSupportedError(authConfig.supportedKeyUse, "no-use")
        }
      }

      "provided with all 3 incorrect fields" in {
        val jwk = correctJwk.copy(alg = Some("HS256"), kty = "HSA", use = "no-use")

        val result = publicKeyGenerator.generateFrom(jwk)

        result.isLeft shouldBe true
        result.swap.map { errors =>
          errors.length shouldBe 3
          errors.toChain.toList should contain theSameElementsAs Seq(
            AlgorithmNotSupportedError(authConfig.supportedAlgorithm, "HS256"),
            KeyTypeNotSupportedError(authConfig.supportedKeyType, "HSA"),
            KeyUseNotSupportedError(authConfig.supportedKeyUse, "no-use")
          )
        }
      }
    }

    "return Right containing PublicKey" when {

      "provided with correct Json Web Key" in {
        val result = publicKeyGenerator.generateFrom(correctJwk)

        val keySpec = new RSAPublicKeySpec(
          new BigInteger(1, JwtBase64.decode(correctJwk.n)),
          new BigInteger(1, JwtBase64.decode(correctJwk.e))
        )
        val expectedPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        result.isRight shouldBe true
        result.map(_ shouldBe expectedPublicKey)
      }
    }
  }
}
