package apikeysteward.routes.auth

import apikeysteward.config.AuthConfig
import apikeysteward.routes.auth.JwtDecoder._
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebToken, JwtClaimCustom, JwtCustom}
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import pdi.jwt._

import java.security.PublicKey

class JwtDecoder(jwkProvider: JwkProvider, publicKeyGenerator: PublicKeyGenerator, authConfig: AuthConfig)
    extends Logging {

  private val FakeKey = "fakeKey"
  private val VerificationFlagsDecode: JwtOptions = new JwtOptions(signature = false, expiration = true)

  private val DecodeAlgorithms: Seq[JwtAlgorithm.RS256.type] = Seq(JwtAlgorithm.RS256)

  def decode(accessToken: String): IO[Either[JwtDecoderError, JsonWebToken]] =
    (for {
      keyId <- extractKeyId(accessToken)
      jwk <- fetchJwk(keyId)
      publicKey <- generatePublicKey(jwk)

      jsonWebToken <- decodeToken(accessToken, publicKey)
      _ <- EitherT.fromEither[IO](validateAudClaim(jsonWebToken.jwtClaim))

    } yield jsonWebToken).value

  private def extractKeyId(accessToken: String): EitherT[IO, JwtDecoderError, String] =
    EitherT(
      IO(
        JwtCirce
          .decodeAll(
            token = accessToken,
            key = FakeKey,
            algorithms = DecodeAlgorithms,
            options = VerificationFlagsDecode
          )
          .toEither
          .left
          .map(DecodingError)
          .flatMap { case (jwtHeader, _, _) =>
            jwtHeader.keyId
              .map(Right(_))
              .getOrElse(
                Left(MissingKeyIdFieldError(accessToken))
              )
          }
      )
    )

  private def fetchJwk(keyId: String): EitherT[IO, JwtDecoderError, JsonWebKey] =
    EitherT(
      for {
        jwkOpt <- jwkProvider.getJsonWebKey(keyId)
        res <- jwkOpt match {
          case Some(jwk) => IO.pure(jwk.asRight)
          case None      => IO.pure(MatchingJwkNotFoundError(keyId).asLeft)
        }
      } yield res
    )

  private def generatePublicKey(jwk: JsonWebKey): EitherT[IO, JwtDecoderError, PublicKey] =
    EitherT(
      IO(
        publicKeyGenerator
          .generateFrom(jwk)
          .left
          .map(errors => PublicKeyGenerationError(errors.iterator.toSeq))
      ).flatTap {
        case Right(_)           => IO.pure(())
        case Left(decoderError) => logger.warn(s"${decoderError.message}. Provided JWK: $jwk")
      }
    )

  private def decodeToken(accessToken: String, publicKey: PublicKey): EitherT[IO, JwtDecoderError, JsonWebToken] =
    EitherT(
      IO(
        JwtCustom
          .decodeAll(
            token = accessToken,
            key = publicKey,
            algorithms = DecodeAlgorithms,
            options = JwtOptions.DEFAULT
          )
          .toEither
          .left
          .map(DecodingError)
          .map { case (jwtHeader, jwtClaim, signature) => JsonWebToken(accessToken, jwtHeader, jwtClaim, signature) }
      )
    )

  private def validateAudClaim(jwtClaim: JwtClaimCustom): Either[JwtDecoderError, JwtClaimCustom] =
    jwtClaim.audience match {
      case None => MissingAudienceClaimError.asLeft

      case Some(audienceSet) =>
        if (audienceSet(authConfig.audience))
          jwtClaim.asRight
        else
          IncorrectAudienceClaimError(audienceSet).asLeft
    }
}

object JwtDecoder {

  sealed trait JwtDecoderError {
    val message: String
  }

  case class DecodingError(exception: Throwable) extends JwtDecoderError {
    override val message: String =
      s"Exception occurred while decoding JWT: ${exception.getMessage}"
  }

  case class MissingKeyIdFieldError(accessToken: String) extends JwtDecoderError {
    override val message: String = s"Key ID (kid) claim is not provided in token: $accessToken"
  }

  case object MissingAudienceClaimError extends JwtDecoderError {
    override val message: String = "Audience (aud) claim is missing."
  }

  case class IncorrectAudienceClaimError(audience: Set[String]) extends JwtDecoderError {
    override val message: String = s"Audience (aud) claim is incorrect: ${audience.mkString("[", ", ", "]")}."
  }

  case class MatchingJwkNotFoundError(keyId: String) extends JwtDecoderError {
    override val message: String = s"Cannot find JWK with kid: $keyId."
  }

  case class PublicKeyGenerationError(errors: Seq[PublicKeyGenerator.PublicKeyGeneratorError]) extends JwtDecoderError {
    override val message: String =
      s"Cannot generate Public Key because: ${errors.map(_.message).mkString("[\"", "\", \"", "\"]")}."
  }

}
