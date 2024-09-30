package apikeysteward.routes.model.apikey

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteApiKeyResponse(
    apiKeyData: ApiKeyData
)

object DeleteApiKeyResponse {
  implicit val codec: Codec[DeleteApiKeyResponse] = deriveCodec[DeleteApiKeyResponse]
}
