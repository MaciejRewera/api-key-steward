package apikeysteward.routes.model.admin

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UpdateApiKeyResponse(
    apiKeyData: ApiKeyData
)

object UpdateApiKeyResponse {
  implicit val codec: Codec[UpdateApiKeyResponse] = deriveCodec[UpdateApiKeyResponse]
}
