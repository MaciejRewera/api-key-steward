package apikeysteward.routes.auth

import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebToken, JwtClaimCustom, JwtCustom}
import pdi.jwt._
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{KeyPair, KeyPairGenerator}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

private[routes] object AuthTestData {

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

  val nowInstant: Instant = Instant.ofEpochMilli(System.currentTimeMillis())
  val now: FiniteDuration = FiniteDuration.apply(nowInstant.toEpochMilli, TimeUnit.MILLISECONDS)

  val permissionRead_1 = "read:permission-1"
  val permissionWrite_1 = "write:permission-1"
  val permissionRead_2 = "read:permission-2"
  val permissionWrite_2 = "write:permission-2"

  val audience_1 = "test-audience-1"
  val audience_2 = "test-audience-2"

  val jwtClaim: JwtClaimCustom = JwtClaimCustom(
    issuer = Some("test-issuer"),
    subject = Some("test-subject"),
    audience = Some(Set(audience_1, audience_2)),
    expiration = Some((now + 5.minutes).toSeconds),
    issuedAt = Some(now.minus(1.minute).toSeconds),
    permissions = Some(Set(permissionRead_1, permissionWrite_1))
  )

  val jwtString: String = JwtCustom.encode(jwtHeader, jwtClaim, privateKey)
  val jwtWithMockedSignature: JsonWebToken = JsonWebToken(
    content = jwtString,
    jwtHeader = jwtHeader,
    jwtClaim = jwtClaim,
    signature = "test-signature"
  )

  val jwtHeaderWithoutKid: JwtHeader = JwtHeader(algorithm = Some(algorithm), typ = Some("JWT"), keyId = None)
  val jwtWithoutKidString: String = JwtCustom.encode(jwtHeaderWithoutKid, jwtClaim, privateKey)

  val expiredJwtClaim: JwtClaimCustom = jwtClaim.copy(
    expiration = Some((now - 1.minutes).toSeconds),
    issuedAt = Some((now - 6.minutes).toSeconds)
  )
  val expiredJwtString: String = JwtCustom.encode(jwtHeader, expiredJwtClaim, privateKey)
}
