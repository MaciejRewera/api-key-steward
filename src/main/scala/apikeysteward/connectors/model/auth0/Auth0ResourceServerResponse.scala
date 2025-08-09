package apikeysteward.connectors.model.auth0

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Auth0ResourceServerResponse(
    total: Int,
    start: Int,
    limit: Int,
    resource_servers: List[Auth0ResourceServer]
)

object Auth0ResourceServerResponse {
  implicit val codec: Codec[Auth0ResourceServerResponse] = deriveCodec[Auth0ResourceServerResponse]
}
