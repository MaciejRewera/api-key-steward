package apikeysteward.routes.auth.model

import cats.Monoid
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class JsonWebKeySet(keys: Set[JsonWebKey]) {
  private lazy val keysMap: Map[String, JsonWebKey] = keys.map(key => key.kid -> key).toMap

  def findBy(keyId: String): Option[JsonWebKey] = keysMap.get(keyId)
}

object JsonWebKeySet {

  def empty: JsonWebKeySet = JsonWebKeySet(Set.empty)

  implicit val codec: Codec[JsonWebKeySet] = deriveCodec[JsonWebKeySet]

  implicit val jsonWebKeySetMonoid: Monoid[JsonWebKeySet] = Monoid.instance(
    JsonWebKeySet(Set.empty),
    (set1, set2) => JsonWebKeySet(set1.keys ++ set2.keys)
  )
}
