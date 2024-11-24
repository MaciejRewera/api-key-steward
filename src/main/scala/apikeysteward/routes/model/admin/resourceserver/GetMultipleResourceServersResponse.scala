package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.model.ResourceServer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleResourceServersResponse(
    resourceServers: List[ResourceServer]
)

object GetMultipleResourceServersResponse {
  implicit val codec: Codec[GetMultipleResourceServersResponse] = deriveCodec[GetMultipleResourceServersResponse]
}
