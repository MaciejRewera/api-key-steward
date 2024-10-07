package apikeysteward.routes.model.admin.apikey

import apikeysteward.routes.model.TapirCustomSchemas
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateApiKeyAdminRequest(
    userId: String,
    name: String,
    description: Option[String] = None,
    ttl: Int,
    scopes: List[String]
) {
  def toUserRequest: (String, CreateApiKeyRequest) = userId -> CreateApiKeyRequest(
    name = this.name,
    description = this.description,
    ttl = this.ttl,
    scopes = this.scopes
  )

}

object CreateApiKeyAdminRequest {
  implicit val codec: Codec[CreateApiKeyAdminRequest] = deriveCodec[CreateApiKeyAdminRequest]

  implicit val createApiKeyAdminRequestSchema: Schema[CreateApiKeyAdminRequest] =
    TapirCustomSchemas.createApiKeyAdminRequestSchema
}
