package apikeysteward.routes.auth

import apikeysteward.base.FixedJwtCustom
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebToken, JwtClaimCustom}
import io.circe.parser
import pdi.jwt._
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{KeyPair, KeyPairGenerator}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

private[routes] object AuthTestData extends FixedJwtCustom {

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
  val encodedModulus: String  = JwtBase64.encodeString(publicKey.getModulus.toByteArray)

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

  val now: FiniteDuration = FiniteDuration.apply(nowInstant.toEpochMilli, TimeUnit.MILLISECONDS)

  val permissionRead_1  = "read:permission-1"
  val permissionWrite_1 = "write:permission-1"
  val permissionRead_2  = "read:permission-2"
  val permissionWrite_2 = "write:permission-2"

  val issuer_1 = "test-issuer-1"
  val issuer_2 = "test-issuer-2"
  val issuer_3 = "test-issuer-3"

  val subject_1 = "test-subject-1"

  val audience_1 = "test-audience-1"
  val audience_2 = "test-audience-2"
  val audience_3 = "test-audience-3"

  val algorithm: JwtAsymmetricAlgorithm = JwtAlgorithm.RS256
  val jwtHeader: JwtHeader              = JwtHeader(algorithm = Some(algorithm), typ = Some("JWT"), keyId = Some(kid_1))

  val jwtClaimJsonString: String =
    s"""{
       |  "iss": "$issuer_1",
       |  "sub": "$subject_1",
       |  "aud": ["$audience_1", "$audience_2"],
       |  "exp": ${now.plus(5.minutes).toSeconds},
       |  "nbf": ${now.minus(1.minute).toSeconds},
       |  "iat": ${now.minus(1.minute).toSeconds},
       |  "permissions": ["$permissionRead_1", "$permissionWrite_1"]
       |}
       |""".stripMargin

  val jwtClaim: JwtClaimCustom = JwtClaimCustom(
    content = parser.parse(jwtClaimJsonString).map(_.noSpaces).toOption.get,
    issuer = Some(issuer_1),
    subject = Some(subject_1),
    audience = Some(Set(audience_1, audience_2)),
    expiration = Some((now + 5.minutes).toSeconds),
    notBefore = Some(now.minus(1.minute).toSeconds),
    issuedAt = Some(now.minus(1.minute).toSeconds),
    permissions = Some(Set(permissionRead_1, permissionWrite_1)),
    userId = Some(subject_1)
  )

  val jwtString: String = jwtCustom.encode(jwtHeader, jwtClaim, privateKey)

  val jwtWithMockedSignature: JsonWebToken = JsonWebToken(
    content = jwtString,
    header = jwtHeader,
    claim = jwtClaim,
    signature = "test-signature"
  )

  val jwtHeaderWithoutKid: JwtHeader = JwtHeader(algorithm = Some(algorithm), typ = Some("JWT"), keyId = None)
  val jwtWithoutKidString: String    = jwtCustom.encode(jwtHeaderWithoutKid, jwtClaim, privateKey)

  val expiredJwtClaim: JwtClaimCustom = jwtClaim.copy(
    expiration = Some((now - 1.minutes).toSeconds),
    issuedAt = Some((now - 6.minutes).toSeconds)
  )

  val expiredJwtString: String = jwtCustom.encode(jwtHeader, expiredJwtClaim, privateKey)
}
