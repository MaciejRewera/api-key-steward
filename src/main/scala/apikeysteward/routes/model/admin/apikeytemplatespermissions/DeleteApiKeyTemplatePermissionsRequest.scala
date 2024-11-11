package apikeysteward.routes.model.admin.apikeytemplatespermissions

import apikeysteward.model.Permission.PermissionId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class DeleteApiKeyTemplatePermissionsRequest(
    permissionIds: List[PermissionId]
)

object DeleteApiKeyTemplatePermissionsRequest {
  implicit val codec: Codec[DeleteApiKeyTemplatePermissionsRequest] =
    deriveCodec[DeleteApiKeyTemplatePermissionsRequest]

  implicit val deleteApiKeyTemplatesPermissionsRequestSchema: Schema[DeleteApiKeyTemplatePermissionsRequest] =
    TapirCustomSchemas.deleteApiKeyTemplatesPermissionsRequestSchema
}
