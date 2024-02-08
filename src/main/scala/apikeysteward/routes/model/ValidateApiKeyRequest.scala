package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ValidateApiKeyRequest(
    apiKey: String
)

object ValidateApiKeyRequest {
  implicit val codec: Codec[ValidateApiKeyRequest] = deriveCodec[ValidateApiKeyRequest]
}
