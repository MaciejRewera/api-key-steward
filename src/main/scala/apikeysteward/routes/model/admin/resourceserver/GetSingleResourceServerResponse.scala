package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSingleResourceServerResponse(
    resourceServer: ResourceServer
)

object GetSingleResourceServerResponse {
  implicit val codec: Codec[GetSingleResourceServerResponse] = deriveCodec[GetSingleResourceServerResponse]
}
