package apikeysteward.routes.model.admin

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class DeleteApiKeyAdminRequest(
    userId: String,
    keyId: UUID
)

object DeleteApiKeyAdminRequest {
  implicit val codec: Codec[DeleteApiKeyAdminRequest] = deriveCodec[DeleteApiKeyAdminRequest]
}
