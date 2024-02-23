package apikeysteward.routes.model.admin

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyAdminRequest(
    name: String,
    description: Option[String] = None,
    ttl: Int
)

object CreateApiKeyAdminRequest {
  implicit val codec: Codec[CreateApiKeyAdminRequest] = deriveCodec[CreateApiKeyAdminRequest]
}
