package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleApplicationsResponse(
    applications: List[Application]
)

object GetMultipleApplicationsResponse {
  implicit val codec: Codec[GetMultipleApplicationsResponse] = deriveCodec[GetMultipleApplicationsResponse]
}
