package apikeysteward.routes.model.admin.permission

import apikeysteward.model.Permission
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreatePermissionResponse(
    permission: Permission
)

object CreatePermissionResponse {
  implicit val codec: Codec[CreatePermissionResponse] = deriveCodec[CreatePermissionResponse]
}
