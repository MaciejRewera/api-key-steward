package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeactivateResourceServerResponse(
    resourceServer: ResourceServer
)

object DeactivateResourceServerResponse {
  implicit val codec: Codec[DeactivateResourceServerResponse] = deriveCodec[DeactivateResourceServerResponse]
}
