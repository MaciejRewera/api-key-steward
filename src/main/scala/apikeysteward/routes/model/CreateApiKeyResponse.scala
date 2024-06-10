package apikeysteward.routes.model

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyResponse(
    apiKey: String,
    apiKeyData: ApiKeyData
)

object CreateApiKeyResponse {
  implicit val codec: Codec[CreateApiKeyResponse] = deriveCodec[CreateApiKeyResponse]
}