package apikeysteward.routes.model.admin.permission

import apikeysteward.model.Permission
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeletePermissionResponse(
    permission: Permission
)

object DeletePermissionResponse {
  implicit val codec: Codec[DeletePermissionResponse] = deriveCodec[DeletePermissionResponse]
}
