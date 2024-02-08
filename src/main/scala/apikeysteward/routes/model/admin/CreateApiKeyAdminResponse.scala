package apikeysteward.routes.model.admin

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyAdminResponse(
    apiKey: String,
    apiKeyData: ApiKeyData
)

object CreateApiKeyAdminResponse {
  implicit val codec: Codec[CreateApiKeyAdminResponse] = deriveCodec[CreateApiKeyAdminResponse]
}
