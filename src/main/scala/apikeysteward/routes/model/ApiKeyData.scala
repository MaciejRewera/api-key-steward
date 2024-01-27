package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ApiKeyData(
    userId: String,
    apiKeyName: String,
    scope: List[String]
)

object ApiKeyData {
  implicit val codec: Codec[ApiKeyData] = deriveCodec[ApiKeyData]
}
