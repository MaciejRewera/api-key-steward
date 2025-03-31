package apikeysteward.routes.model.admin.permission

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreatePermissionRequest(
    name: String,
    description: Option[String]
)

object CreatePermissionRequest {
  implicit val codec: Codec[CreatePermissionRequest] = deriveCodec[CreatePermissionRequest]

  implicit val createPermissionRequestSchema: Schema[CreatePermissionRequest] =
    TapirCustomSchemas.createPermissionRequestSchema

}
