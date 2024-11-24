package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ReactivateResourceServerResponse(
    resourceServer: ResourceServer
)

object ReactivateResourceServerResponse {
  implicit val codec: Codec[ReactivateResourceServerResponse] = deriveCodec[ReactivateResourceServerResponse]
}
