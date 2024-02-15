package apikeysteward.routes.model.admin

import apikeysteward.model.ApiKeyData
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteApiKeyAdminResponse(
    deletedApiKeyData: ApiKeyData
)

object DeleteApiKeyAdminResponse {
  implicit val codec: Codec[DeleteApiKeyAdminResponse] = deriveCodec[DeleteApiKeyAdminResponse]
}
