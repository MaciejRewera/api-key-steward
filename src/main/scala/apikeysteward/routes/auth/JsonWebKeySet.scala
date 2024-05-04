package apikeysteward.routes.auth

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class JsonWebKeySet(keys: Seq[JsonWebKey]) {
  def findBy(keyId: String): Option[JsonWebKey] = keys.find(_.kid == keyId)
}

object JsonWebKeySet {
  implicit val codec: Codec[JsonWebKeySet] = deriveCodec[JsonWebKeySet]
}
