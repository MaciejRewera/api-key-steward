package apikeysteward.routes.auth

import apikeysteward.model.errors.CustomError
import apikeysteward.routes.auth.JwtDecoder._
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError._
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebToken, JwtClaimCustom, JwtCustom}
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import pdi.jwt._

import java.security.PublicKey
import scala.util.Try

class JwtDecoder(jwtValidator: JwtValidator, jwkProvider: JwkProvider, publicKeyGenerator: PublicKeyGenerator)(
    jwtCustom: JwtCustom
) extends Logging {

  private val DecodeAlgorithms: Seq[JwtAlgorithm.RS256.type] = Seq(JwtAlgorithm.RS256)

  def decode(accessToken: String): IO[Either[JwtDecoderError, JsonWebToken]] =
    (for {
      jwt <- decodeNoSignature(accessToken)
      _   <- validateToken(jwt)

      keyId     <- extractKeyId(jwt.header)
      jwk       <- fetchJwk(keyId)
      publicKey <- generatePublicKey(jwk)

      jsonWebToken <- decodeToken(accessToken, publicKey)
    } yield jsonWebToken).value

  private def validateToken(jwt: JsonWebToken): EitherT[IO, ValidationError, JsonWebToken] =
    EitherT.fromEither[IO](jwtValidator.validateAll(jwt).left.map(err => ValidationError(err.iterator.toSeq)))

  private def extractKeyId(jwtHeader: JwtHeader): EitherT[IO, JwtDecoderError, String] =
    EitherT.fromEither[IO](jwtHeader.keyId.toRight(ValidationError(MissingKeyIdFieldError)))

  private def fetchJwk(keyId: String): EitherT[IO, JwtDecoderError, JsonWebKey] =
    EitherT(
      for {
        jwkOpt <- jwkProvider.getJsonWebKey(keyId)
        res    <- IO(Either.fromOption(jwkOpt, MatchingJwkNotFoundError(keyId)))
      } yield res
    )

  private def generatePublicKey(jwk: JsonWebKey): EitherT[IO, JwtDecoderError, PublicKey] =
    EitherT
      .fromEither[IO](
        publicKeyGenerator
          .generateFrom(jwk)
          .left
          .map[JwtDecoderError](errors => PublicKeyGenerationError(errors.iterator.toSeq))
      )
      .leftSemiflatTap(decoderError => logger.warn(s"${decoderError.message}. Provided JWK: $jwk"))

  private def decodeNoSignature(accessToken: String): EitherT[IO, JwtDecoderError, JsonWebToken] = {
    val FakeKey                             = "fakeKey"
    val VerificationFlagsDecode: JwtOptions = new JwtOptions(signature = false, expiration = true)

    decodeToken(accessToken)(
      jwtCustom
        .decodeAll(token = accessToken, key = FakeKey, algorithms = DecodeAlgorithms, options = VerificationFlagsDecode)
    )
  }

  private def decodeToken(accessToken: String, publicKey: PublicKey): EitherT[IO, JwtDecoderError, JsonWebToken] =
    decodeToken(accessToken)(
      jwtCustom
        .decodeAll(
          token = accessToken,
          key = publicKey,
          algorithms = DecodeAlgorithms,
          options = JwtOptions.DEFAULT
        )
    )

  private def decodeToken(
      accessToken: String
  )(decodeResult: => Try[(JwtHeader, JwtClaimCustom, String)]): EitherT[IO, JwtDecoderError, JsonWebToken] =
    EitherT.fromEither[IO](
      decodeResult.toEither match {
        case Left(exc) => DecodingError(exc).asLeft
        case Right((jwtHeader, jwtClaim, signature)) =>
          JsonWebToken(accessToken, jwtHeader, jwtClaim, signature).asRight
      }
    )

}

object JwtDecoder {

  sealed abstract class JwtDecoderError(override val message: String) extends CustomError

  case class DecodingError(exception: Throwable)
      extends JwtDecoderError(message = s"Exception occurred while decoding JWT: ${exception.getMessage}")

  case class ValidationError(errors: Seq[JwtValidatorError])
      extends JwtDecoderError(
        message = s"An error occurred while validating the JWT: ${errors.map(_.message).mkString("['", "', '", "']")}."
      )

  object ValidationError {
    def apply(jwtValidationError: JwtValidatorError): ValidationError = ValidationError(Seq(jwtValidationError))
  }

  case class MatchingJwkNotFoundError(keyId: String)
      extends JwtDecoderError(
        message = s"Cannot find JWK with kid: $keyId."
      )

  case class PublicKeyGenerationError(errors: Seq[PublicKeyGenerator.PublicKeyGeneratorError])
      extends JwtDecoderError(
        message = s"Cannot generate Public Key because: ${errors.map(_.message).mkString("['", "', '", "']")}."
      )

}
