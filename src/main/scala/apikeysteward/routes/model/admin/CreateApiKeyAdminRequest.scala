package apikeysteward.routes.model.admin

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyAdminRequest(
    userId: String,
    name: String,
    description: Option[String] = None,
    scope: List[String] = List.empty,
    ttl: Int
)

object CreateApiKeyAdminRequest {
  implicit val codec: Codec[CreateApiKeyAdminRequest] = deriveCodec[CreateApiKeyAdminRequest]
}
