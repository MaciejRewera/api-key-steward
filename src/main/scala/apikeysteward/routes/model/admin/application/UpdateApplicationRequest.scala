package apikeysteward.routes.model.admin.application

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateApplicationRequest(
    name: String,
    description: Option[String]
)

object UpdateApplicationRequest {
  implicit val codec: Codec[UpdateApplicationRequest] = deriveCodec[UpdateApplicationRequest]

  implicit val updateApplicationRequestSchema: Schema[UpdateApplicationRequest] =
    TapirCustomSchemas.updateApplicationRequestSchema
}
