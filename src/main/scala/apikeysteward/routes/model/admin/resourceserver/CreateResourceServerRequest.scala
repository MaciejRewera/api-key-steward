package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.routes.model.TapirCustomSchemas
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateResourceServerRequest(
    name: String,
    description: Option[String],
    permissions: List[CreatePermissionRequest]
)

object CreateResourceServerRequest {
  implicit val codec: Codec[CreateResourceServerRequest] = deriveCodec[CreateResourceServerRequest]

  implicit val createResourceServerRequestSchema: Schema[CreateResourceServerRequest] =
    TapirCustomSchemas.createResourceServerRequestSchema

}
