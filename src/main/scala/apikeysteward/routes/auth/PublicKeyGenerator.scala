package apikeysteward.routes.auth

import apikeysteward.model.errors.CustomError
import apikeysteward.routes.auth.PublicKeyGenerator._
import apikeysteward.routes.auth.model.JsonWebKey
import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.implicits._
import pdi.jwt.JwtBase64

import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}

class PublicKeyGenerator {

  private val SupportedAlgorithm = "RS256"
  private val SupportedKeyType   = "RSA"
  private val SupportedKeyUse    = "sig"

  def generateFrom(jsonWebKey: JsonWebKey): Either[NonEmptyChain[PublicKeyGeneratorError], PublicKey] =
    validateJwk(jsonWebKey).map { jwk =>
      val modulo   = new BigInteger(1, JwtBase64.decode(jwk.n))
      val exponent = new BigInteger(1, JwtBase64.decode(jwk.e))
      val keySpec  = new RSAPublicKeySpec(modulo, exponent)

      KeyFactory.getInstance(SupportedKeyType).generatePublic(keySpec)
    }.toEither

  private def validateJwk(jwk: JsonWebKey): ValidatedNec[PublicKeyGeneratorError, JsonWebKey] =
    (
      validateAlgorithm(jwk),
      validateKeyType(jwk),
      validateKeyUse(jwk)
    ).mapN((_, _, _) => jwk)

  private def validateAlgorithm(jwk: JsonWebKey): ValidatedNec[PublicKeyGeneratorError, JsonWebKey] =
    (for {
      _ <- validateAlgorithmIsDefined(jwk).toEither
      _ <- validateAlgorithmIsSupported(jwk).toEither
    } yield jwk).toValidated

  private def validateAlgorithmIsDefined(jwk: JsonWebKey): ValidatedNec[PublicKeyGeneratorError, JsonWebKey] =
    Validated
      .condNec(
        jwk.alg.isDefined,
        jwk,
        AlgorithmNotProvidedError
      )

  private def validateAlgorithmIsSupported(jwk: JsonWebKey): ValidatedNec[PublicKeyGeneratorError, JsonWebKey] =
    Validated
      .condNec(
        jwk.alg.contains(SupportedAlgorithm),
        jwk,
        AlgorithmNotSupportedError(SupportedAlgorithm, jwk.alg.getOrElse(""))
      )

  private def validateKeyType(jwk: JsonWebKey): ValidatedNec[PublicKeyGeneratorError, JsonWebKey] =
    Validated
      .condNec(
        jwk.kty == SupportedKeyType,
        jwk,
        KeyTypeNotSupportedError(SupportedKeyType, jwk.kty)
      )

  private def validateKeyUse(jwk: JsonWebKey): ValidatedNec[PublicKeyGeneratorError, JsonWebKey] =
    Validated
      .condNec(
        jwk.use == SupportedKeyUse,
        jwk,
        KeyUseNotSupportedError(SupportedKeyUse, jwk.use)
      )

}

object PublicKeyGenerator {

  sealed abstract class PublicKeyGeneratorError(override val message: String) extends CustomError

  case object AlgorithmNotProvidedError extends PublicKeyGeneratorError(message = "Algorithm not provided.")

  case class AlgorithmNotSupportedError(supportedAlgorithm: String, actualAlgorithm: String)
      extends PublicKeyGeneratorError(
        message = s"Algorithm $actualAlgorithm is not supported. Please use $supportedAlgorithm."
      )

  case class KeyTypeNotSupportedError(supportedKeyType: String, actualKeyType: String)
      extends PublicKeyGeneratorError(
        message = s"Key Type $actualKeyType is not supported. Please use $supportedKeyType."
      )

  case class KeyUseNotSupportedError(supportedKeyUse: String, actualKeyUse: String)
      extends PublicKeyGeneratorError(
        message = s"Public Key Use $actualKeyUse is not supported. Please use $supportedKeyUse."
      )

}
