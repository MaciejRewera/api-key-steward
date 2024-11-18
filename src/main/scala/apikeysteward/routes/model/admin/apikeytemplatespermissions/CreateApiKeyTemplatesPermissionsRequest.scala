package apikeysteward.routes.model.admin.apikeytemplatespermissions

import apikeysteward.model.Permission.PermissionId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateApiKeyTemplatesPermissionsRequest(
    permissionIds: List[PermissionId]
)

object CreateApiKeyTemplatesPermissionsRequest {
  implicit val codec: Codec[CreateApiKeyTemplatesPermissionsRequest] =
    deriveCodec[CreateApiKeyTemplatesPermissionsRequest]

  implicit val createApiKeyTemplatesPermissionsRequestSchema: Schema[CreateApiKeyTemplatesPermissionsRequest] =
    TapirCustomSchemas.createApiKeyTemplatesPermissionsRequestSchema
}
