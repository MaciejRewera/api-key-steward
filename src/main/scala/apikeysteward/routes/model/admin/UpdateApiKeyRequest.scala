package apikeysteward.routes.model.admin

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateApiKeyRequest(
    name: String,
    description: Option[String] = None
)

object UpdateApiKeyRequest {
  implicit val codec: Codec[UpdateApiKeyRequest] = deriveCodec[UpdateApiKeyRequest]

  implicit val updateApiKeyRequestSchema: Schema[UpdateApiKeyRequest] = TapirCustomSchemas.updateApiKeyRequestSchema
}
