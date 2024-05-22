package apikeysteward.routes.model

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteApiKeyResponse(
    deletedApiKeyData: ApiKeyData
)

object DeleteApiKeyResponse {
  implicit val codec: Codec[DeleteApiKeyResponse] = deriveCodec[DeleteApiKeyResponse]
}
