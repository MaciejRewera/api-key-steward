package apikeysteward.routes.model.admin.application

import apikeysteward.routes.model.TapirCustomSchemas
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateApplicationRequest(
    name: String,
    description: Option[String],
    permissions: List[CreatePermissionRequest]
)

object CreateApplicationRequest {
  implicit val codec: Codec[CreateApplicationRequest] = deriveCodec[CreateApplicationRequest]

  implicit val createApplicationRequestSchema: Schema[CreateApplicationRequest] =
    TapirCustomSchemas.createApplicationRequestSchema
}
