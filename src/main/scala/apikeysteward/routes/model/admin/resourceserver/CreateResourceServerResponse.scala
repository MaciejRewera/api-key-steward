package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateResourceServerResponse(
    resourceServer: ResourceServer
)

object CreateResourceServerResponse {
  implicit val codec: Codec[CreateResourceServerResponse] = deriveCodec[CreateResourceServerResponse]
}
