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
import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class JwtDecoder(jwkProvider: JwkProvider, publicKeyGenerator: PublicKeyGenerator, authConfig: AuthConfig)(
    implicit clock: Clock
) extends Logging {

  private val FakeKey = "fakeKey"
  private val VerificationFlagsDecode: JwtOptions = new JwtOptions(signature = false, expiration = true)

  private val DecodeAlgorithms: Seq[JwtAlgorithm.RS256.type] = Seq(JwtAlgorithm.RS256)

  def decode(accessToken: String): IO[Either[JwtDecoderError, JsonWebToken]] =
    (for {
      keyId <- extractKeyId(accessToken)
      jwk <- fetchJwk(keyId)
      publicKey <- generatePublicKey(jwk)

      jsonWebToken <- decodeToken(accessToken, publicKey)
      _ <- validateIssuedAtClaim(jsonWebToken.jwtClaim)
      _ <- validateAudienceClaim(jsonWebToken.jwtClaim)

    } yield jsonWebToken).value

  private def extractKeyId(accessToken: String): EitherT[IO, JwtDecoderError, String] =
    EitherT.fromEither[IO](
      JwtCustom
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
    EitherT
      .fromEither[IO](
        publicKeyGenerator
          .generateFrom(jwk)
          .left
          .map[JwtDecoderError](errors => PublicKeyGenerationError(errors.iterator.toSeq))
      )
      .leftSemiflatTap(decoderError => logger.warn(s"${decoderError.message}. Provided JWK: $jwk"))

  private def decodeToken(accessToken: String, publicKey: PublicKey): EitherT[IO, JwtDecoderError, JsonWebToken] =
    EitherT.fromEither[IO](
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

  private def validateIssuedAtClaim(jwtClaim: JwtClaimCustom): EitherT[IO, JwtDecoderError, JwtClaimCustom] =
    EitherT.fromEither(
      jwtClaim.issuedAt match {
        case None => MissingIssuedAtClaimError.asLeft

        case Some(issuedAtClaim) =>
          authConfig.maxTokenAge.map { maxTokenAge =>
            val issuedAt = FiniteDuration(issuedAtClaim, TimeUnit.SECONDS)
            val nowInSeconds = FiniteDuration(Instant.now(clock).getEpochSecond, TimeUnit.SECONDS)

            if (issuedAt.plus(maxTokenAge) > nowInSeconds)
              jwtClaim.asRight
            else
              TokenTooOldError(maxTokenAge).asLeft
          }.getOrElse(jwtClaim.asRight)
      }
    )

  private def validateAudienceClaim(jwtClaim: JwtClaimCustom): EitherT[IO, JwtDecoderError, JwtClaimCustom] =
    EitherT.fromEither(
      jwtClaim.audience.map {
        case audienceSet if audienceSet(authConfig.audience) => jwtClaim.asRight

        case audienceSet if audienceSet.isEmpty => MissingAudienceClaimError.asLeft
        case audienceSet                        => IncorrectAudienceClaimError(audienceSet).asLeft

      }.getOrElse(MissingAudienceClaimError.asLeft)
    )
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

  case object MissingIssuedAtClaimError extends JwtDecoderError {
    override val message: String = "Issued at (iat) claim is missing."
  }

  case class TokenTooOldError(maxTokenAge: FiniteDuration) extends JwtDecoderError {
    override val message: String = s"The provided JWT is older than maximum allowed age of: ${maxTokenAge.toString}"
  }

}
