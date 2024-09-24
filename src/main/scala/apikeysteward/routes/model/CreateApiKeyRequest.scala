package apikeysteward.routes.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._

case class CreateApiKeyRequest(
    override val name: String,
    override val description: Option[String] = None,
    ttl: Int,
    scopes: List[String]
) extends CreateUpdateApiKeyRequestBase

object CreateApiKeyRequest {
  implicit val codec: Codec[CreateApiKeyRequest] = deriveCodec[CreateApiKeyRequest]

  implicit val createApiKeyAdminRequestSchema: Schema[CreateApiKeyRequest] =
    TapirCustomSchemas.createApiKeyAdminRequestSchema
}
