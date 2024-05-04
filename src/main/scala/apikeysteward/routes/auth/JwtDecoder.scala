package apikeysteward.routes.auth

import cats.effect.IO
import pdi.jwt._

import java.security.PublicKey
import scala.util.{Failure, Success}

class JwtDecoder(jwkProvider: JwkProvider, publicKeyGenerator: PublicKeyGenerator) {

  private val FakeKey = "fakeKey"
  private val VerificationFlagsDecode: JwtOptions = new JwtOptions(signature = false, expiration = true)

  private val DecodeAlgorithms: Seq[JwtAlgorithm.RS256.type] = Seq(JwtAlgorithm.RS256)

  def decode(accessToken: String): IO[JsonWebToken] =
    for {
      keyId <- extractKeyId(accessToken)
      jwk <- fetchJwk(keyId)
      publicKey <- generatePublicKey(jwk)

      result <- decodeToken(accessToken, publicKey)
    } yield result

  private def extractKeyId(accessToken: String): IO[String] =
    IO.fromTry(
      JwtCirce
        .decodeAll(
          token = accessToken,
          key = FakeKey,
          algorithms = DecodeAlgorithms,
          options = VerificationFlagsDecode
        )
        .flatMap { case (jwtHeader, _, _) =>
          jwtHeader.keyId
            .map(Success(_))
            .getOrElse(
              Failure(new IllegalArgumentException(s"Key ID (kid) field is not provided in token: $accessToken"))
            )
        }
    )

  private def fetchJwk(keyId: String): IO[JsonWebKey] =
    for {
      jwkOpt <- jwkProvider.getJsonWebKey(keyId)
      res <- jwkOpt match {
        case Some(jwk) => IO.pure(jwk)
        case None      => IO.raiseError(new NoSuchElementException(s"Cannot find JWK with kid: $keyId."))
      }
    } yield res

  private def generatePublicKey(jwk: JsonWebKey): IO[PublicKey] =
    IO.fromEither(
      publicKeyGenerator
        .generateFrom(jwk)
        .left
        .map(errors =>
          new IllegalArgumentException(
            s"Cannot generate Public Key because: ${errors.iterator.map(_.message).mkString("[", ", ", "]")}. Provided JWK: $jwk"
          )
        )
    )

  private def decodeToken(accessToken: String, publicKey: PublicKey): IO[JsonWebToken] =
    IO.fromTry(
      JwtCirce
        .decodeAll(
          token = accessToken,
          key = publicKey,
          algorithms = DecodeAlgorithms,
          options = JwtOptions.DEFAULT
        )
        .map { case (jwtHeader, jwtClaim, signature) => JsonWebToken(accessToken, jwtHeader, jwtClaim, signature) }
    )
}
