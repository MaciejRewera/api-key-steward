package apikeysteward.routes.model.admin.resourceserver

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateResourceServerRequest(
    name: String,
    description: Option[String]
)

object UpdateResourceServerRequest {
  implicit val codec: Codec[UpdateResourceServerRequest] = deriveCodec[UpdateResourceServerRequest]

  implicit val updateResourceServerRequestSchema: Schema[UpdateResourceServerRequest] =
    TapirCustomSchemas.updateResourceServerRequestSchema
}
