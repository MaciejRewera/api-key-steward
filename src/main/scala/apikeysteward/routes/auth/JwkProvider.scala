package apikeysteward.routes.auth

import cats.effect.IO

trait JwkProvider {
  def getJsonWebKey(keyId: String): IO[Option[JsonWebKey]]
}
