package apikeysteward.routes.model.apikey

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._

case class CreateApiKeyRequest(
    name: String,
    description: Option[String],
    ttl: Int
)

object CreateApiKeyRequest {
  implicit val codec: Codec[CreateApiKeyRequest] = deriveCodec[CreateApiKeyRequest]

  implicit val createApiKeyRequestSchema: Schema[CreateApiKeyRequest] = TapirCustomSchemas.createApiKeyRequestSchema
}
