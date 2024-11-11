package apikeysteward.routes.model.admin.apikeytemplatespermissions

import apikeysteward.model.Permission.PermissionId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateApiKeyTemplatePermissionsRequest(
    permissionIds: List[PermissionId]
)

object CreateApiKeyTemplatePermissionsRequest {
  implicit val codec: Codec[CreateApiKeyTemplatePermissionsRequest] =
    deriveCodec[CreateApiKeyTemplatePermissionsRequest]

  implicit val createApiKeyTemplatesPermissionsRequestSchema: Schema[CreateApiKeyTemplatePermissionsRequest] =
    TapirCustomSchemas.createApiKeyTemplatesPermissionsRequestSchema
}
