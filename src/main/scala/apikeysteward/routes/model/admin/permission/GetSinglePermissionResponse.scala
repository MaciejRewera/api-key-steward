package apikeysteward.routes.model.admin.permission

import apikeysteward.model.Permission
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSinglePermissionResponse(
    permission: Permission
)

object GetSinglePermissionResponse {
  implicit val codec: Codec[GetSinglePermissionResponse] = deriveCodec[GetSinglePermissionResponse]
}
