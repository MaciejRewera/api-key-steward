package apikeysteward.routes.auth

import io.circe.Codec
import pdi.jwt.JwtHeader

case class JsonWebKey(
    alg: Option[String],
    kty: String,
    use: String,
    n: String,
    e: String,
    kid: String,
    x5t: Option[String],
    x5c: Option[Seq[String]]
)

object JsonWebKey {
  import io.circe.generic.semiauto.deriveCodec

  implicit val codec: Codec[JsonWebKey] = deriveCodec[JsonWebKey]
}
