package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyRequest(
    apiKeyData: ApiKeyData
)

object CreateApiKeyRequest {
  implicit val codec: Codec[CreateApiKeyRequest] = deriveCodec[CreateApiKeyRequest]
}
