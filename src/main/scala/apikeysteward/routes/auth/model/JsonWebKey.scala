package apikeysteward.routes.auth.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

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
  implicit val codec: Codec[JsonWebKey] = deriveCodec[JsonWebKey]
}
