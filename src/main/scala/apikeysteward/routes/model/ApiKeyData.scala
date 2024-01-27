package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ApiKeyData(
    userId: String,
    apiKeyName: String
)

object ApiKeyData {
  implicit val codec: Codec[ApiKeyData] = deriveCodec[ApiKeyData]

  def from(request: CreateApiKeyRequest): ApiKeyData = ApiKeyData(
    userId = request.userId, apiKeyName = request.apiKeyName
  )
}
