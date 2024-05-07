package apikeysteward.routes.auth

import apikeysteward.routes.auth.model.JsonWebKey
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm
import pdi.jwt._

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{KeyPair, KeyPairGenerator}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

private[auth] object AuthTestData {

  private def generateKeyPair: KeyPair = {
    val keyGen: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(512)

    keyGen.generateKeyPair
  }

  val (privateKey, publicKey) = {
    val keyPair = generateKeyPair
    (keyPair.getPrivate.asInstanceOf[RSAPrivateKey], keyPair.getPublic.asInstanceOf[RSAPublicKey])
  }
  val encodedExponent: String = JwtBase64.encodeString(publicKey.getPublicExponent.toByteArray)
  val encodedModulus: String = JwtBase64.encodeString(publicKey.getModulus.toByteArray)

  val kid_1 = "test-key-id-001"
  val kid_2 = "test-key-id-002"
  val kid_3 = "test-key-id-003"
  val kid_4 = "test-key-id-004"

  val jsonWebKey: JsonWebKey = JsonWebKey(
    alg = Some("RS256"),
    kty = "RSA",
    use = "sig",
    n = encodedModulus,
    e = encodedExponent,
    kid = kid_1,
    x5t = Some("jRYd85jYya3Ve"),
    x5c = Some(Seq("nedesMA0GCSqGSIb3DQEBCwUAMCwxKjAoBgNVBAMTIWRldi1oeDFpMjh4eHZhcWY"))
  )

  val algorithm: JwtAsymmetricAlgorithm = JwtAlgorithm.RS256
  val jwtHeader: JwtHeader = JwtHeader(algorithm = Some(algorithm), typ = Some("JWT"), keyId = Some(kid_1))
  val now: FiniteDuration = FiniteDuration.apply(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
  val jwtClaim: JwtClaim = JwtClaim(
    issuer = Some("test-issuer"),
    subject = Some("test-subject"),
    audience = Some(Set("test-audience-1", "test-audience-2")),
    expiration = Some((now + 5.minutes).toSeconds),
    issuedAt = Some(now.toSeconds)
  )

  val jwtString: String = JwtCirce.encode(jwtHeader, jwtClaim, privateKey)

  val expiredJwtClaim: JwtClaim = JwtClaim(
    issuer = Some("test-issuer"),
    subject = Some("test-subject"),
    audience = Some(Set("test-audience-1", "test-audience-2")),
    expiration = Some((now - 1.minutes).toSeconds),
    issuedAt = Some((now - 6.minutes).toSeconds)
  )
  val expiredJwtString: String = JwtCirce.encode(jwtHeader, expiredJwtClaim, privateKey)
}
