package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyResponse(
    userId: String,
    apiKeyName: String,
    apiKey: String
)

object CreateApiKeyResponse {
  implicit val codec: Codec[CreateApiKeyResponse] = deriveCodec[CreateApiKeyResponse]
}
