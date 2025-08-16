package apikeysteward.connectors.model.auth0

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Auth0Scope(
    value: String,
    description: String
)

object Auth0Scope {
  implicit val codec: Codec[Auth0Scope] = deriveCodec[Auth0Scope]
}
