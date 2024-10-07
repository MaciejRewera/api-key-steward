package apikeysteward.routes.model.admin.apikey

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateApiKeyAdminRequest(
    name: String,
    description: Option[String] = None
)

object UpdateApiKeyAdminRequest {
  implicit val codec: Codec[UpdateApiKeyAdminRequest] = deriveCodec[UpdateApiKeyAdminRequest]

  implicit val updateApiKeyAdminRequestSchema: Schema[UpdateApiKeyAdminRequest] =
    TapirCustomSchemas.updateApiKeyAdminRequestSchema
}
