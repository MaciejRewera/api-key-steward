package apikeysteward.routes.model.admin.apikeytemplatespermissions

import apikeysteward.model.Permission.PermissionId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class DeleteApiKeyTemplatesPermissionsRequest(
    permissionIds: List[PermissionId]
)

object DeleteApiKeyTemplatesPermissionsRequest {
  implicit val codec: Codec[DeleteApiKeyTemplatesPermissionsRequest] =
    deriveCodec[DeleteApiKeyTemplatesPermissionsRequest]

  implicit val deleteApiKeyTemplatesPermissionsRequestSchema: Schema[DeleteApiKeyTemplatesPermissionsRequest] =
    TapirCustomSchemas.deleteApiKeyTemplatesPermissionsRequestSchema
}
