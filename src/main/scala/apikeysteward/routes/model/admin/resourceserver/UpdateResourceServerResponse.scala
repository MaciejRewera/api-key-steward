package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UpdateResourceServerResponse(
    resourceServer: ResourceServer
)

object UpdateResourceServerResponse {
  implicit val codec: Codec[UpdateResourceServerResponse] = deriveCodec[UpdateResourceServerResponse]
}
