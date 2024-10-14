package apikeysteward.routes.model.admin.permission

import apikeysteward.model.Permission
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultiplePermissionsResponse(
    permissions: List[Permission]
)

object GetMultiplePermissionsResponse {
  implicit val codec: Codec[GetMultiplePermissionsResponse] = deriveCodec[GetMultiplePermissionsResponse]
}
