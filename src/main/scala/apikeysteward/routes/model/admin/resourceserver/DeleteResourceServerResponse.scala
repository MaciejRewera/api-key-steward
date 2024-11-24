package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteResourceServerResponse(
    resourceServer: ResourceServer
)

object DeleteResourceServerResponse {
  implicit val codec: Codec[DeleteResourceServerResponse] = deriveCodec[DeleteResourceServerResponse]
}
