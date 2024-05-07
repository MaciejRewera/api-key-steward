package apikeysteward.routes.auth

import apikeysteward.routes.auth.model.JsonWebKey
import cats.effect.IO

trait JwkProvider {
  def getJsonWebKey(keyId: String): IO[Option[JsonWebKey]]
}
