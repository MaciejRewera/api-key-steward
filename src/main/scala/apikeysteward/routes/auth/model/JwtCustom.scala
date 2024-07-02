package apikeysteward.routes.auth.model

import apikeysteward.config.JwtConfig
import io.circe.syntax.EncoderOps
import pdi.jwt.algorithms.{JwtAsymmetricAlgorithm, JwtHmacAlgorithm}
import pdi.jwt.exceptions.JwtValidationException
import pdi.jwt.{JwtCirceParser, JwtHeader}

import java.security.{Key, PrivateKey}
import java.time.Clock
import javax.crypto.SecretKey

class JwtCustom(override val clock: Clock, config: JwtConfig) extends JwtCirceParser[JwtHeader, JwtClaimCustom] {

  implicit private val implicitConfig: JwtConfig = config

  override def parseHeader(header: String): JwtHeader = parseHeaderHelp(header)

  override def parseClaim(claim: String): JwtClaimCustom = parseClaimHelp(claim)

  private def parseHeaderHelp(header: String): JwtHeader = {
    val cursor = parse(header).hcursor
    JwtHeader(
      algorithm = getAlg(cursor),
      typ = cursor.get[String]("typ").toOption,
      contentType = cursor.get[String]("cty").toOption,
      keyId = cursor.get[String]("kid").toOption
    )
  }

  private def parseClaimHelp(claim: String): JwtClaimCustom = JwtClaimCustom.codec
    .apply(parse(claim).hcursor)
    .fold(throw _, identity)

  override protected def extractExpiration(claim: JwtClaimCustom): Option[Long] = claim.expiration

  override protected def extractNotBefore(claim: JwtClaimCustom): Option[Long] = claim.notBefore

  def encode(header: JwtHeader, claim: JwtClaimCustom, key: Key): String = (header.algorithm, key) match {
    case (Some(algo: JwtHmacAlgorithm), k: SecretKey)        => encode(header.toJson, claim.asJson.spaces2, k, algo)
    case (Some(algo: JwtAsymmetricAlgorithm), k: PrivateKey) => encode(header.toJson, claim.asJson.spaces2, k, algo)
    case _ =>
      throw new JwtValidationException(
        "The key type doesn't match the algorithm type. It's either a SecretKey and a HMAC algorithm or a PrivateKey and a RSA or ECDSA algorithm. And an algorithm is required of course."
      )
  }
}
