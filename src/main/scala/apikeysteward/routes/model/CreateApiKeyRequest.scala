package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._

case class CreateApiKeyRequest(
    name: String,
    description: Option[String] = None,
    ttl: Int,
    scopes: List[String]
)

object CreateApiKeyRequest {
  implicit val codec: Codec[CreateApiKeyRequest] = deriveCodec[CreateApiKeyRequest]

  implicit val createApiKeyAdminRequestSchema: Schema[CreateApiKeyRequest] =
    TapirCustomSchemas.createApiKeyAdminRequestSchema
}
