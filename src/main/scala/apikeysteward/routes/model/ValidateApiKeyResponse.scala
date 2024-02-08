package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ValidateApiKeyResponse(
    apiKeyData: ApiKeyData
)

object ValidateApiKeyResponse {
  implicit val codec: Codec[ValidateApiKeyResponse] = deriveCodec[ValidateApiKeyResponse]
}
