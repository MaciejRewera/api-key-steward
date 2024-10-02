package apikeysteward.routes.model.apikey

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleApiKeysResponse(
    apiKeyData: List[ApiKeyData]
)

object GetMultipleApiKeysResponse {
  implicit val codec: Codec[GetMultipleApiKeysResponse] = deriveCodec[GetMultipleApiKeysResponse]
}
