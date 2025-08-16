package apikeysteward.connectors.model.auth0

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Auth0ResourceServer(
    id: String,
    name: String,
    is_system: Option[Boolean],
    identifier: String,
    scopes: Option[List[Auth0Scope]]
)

object Auth0ResourceServer {
  implicit val codec: Codec[Auth0ResourceServer] = deriveCodec[Auth0ResourceServer]
}
