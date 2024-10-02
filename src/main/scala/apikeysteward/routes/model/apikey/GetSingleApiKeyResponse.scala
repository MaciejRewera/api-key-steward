package apikeysteward.routes.model.apikey

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSingleApiKeyResponse(
    apiKeyData: ApiKeyData
)

object GetSingleApiKeyResponse {
  implicit val codec: Codec[GetSingleApiKeyResponse] = deriveCodec[GetSingleApiKeyResponse]
}
