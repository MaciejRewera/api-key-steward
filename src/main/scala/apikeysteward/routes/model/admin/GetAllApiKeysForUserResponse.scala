package apikeysteward.routes.model.admin

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetAllApiKeysForUserResponse(
    apiKeys: List[ApiKeyData]
)

object GetAllApiKeysForUserResponse {
  implicit val codec: Codec[GetAllApiKeysForUserResponse] = deriveCodec[GetAllApiKeysForUserResponse]
}
