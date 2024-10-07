package apikeysteward.routes.model.admin.apikey

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UpdateApiKeyAdminResponse(
    apiKeyData: ApiKeyData
)

object UpdateApiKeyAdminResponse {
  implicit val codec: Codec[UpdateApiKeyAdminResponse] = deriveCodec[UpdateApiKeyAdminResponse]
}
