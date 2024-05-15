package apikeysteward.routes.auth.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class JsonWebKeySet(keys: Seq[JsonWebKey]) {
  private lazy val keysMap: Map[String, JsonWebKey] = keys.map(key => key.kid -> key).toMap

  def findBy(keyId: String): Option[JsonWebKey] = keysMap.get(keyId)
}

object JsonWebKeySet {
  implicit val codec: Codec[JsonWebKeySet] = deriveCodec[JsonWebKeySet]
}
