package apikeysteward.routes.model.admin

import apikeysteward.routes.model.{TapirCustomSchemas, CreateUpdateApiKeyRequestBase}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateApiKeyRequest(
    override val name: String,
    override val description: Option[String] = None,
    override val ttl: Int,
    override val scopes: List[String]
) extends CreateUpdateApiKeyRequestBase

object UpdateApiKeyRequest {
  implicit val codec: Codec[UpdateApiKeyRequest] = deriveCodec[UpdateApiKeyRequest]

  implicit val updateApiKeyAdminRequestSchema: Schema[UpdateApiKeyRequest] =
    TapirCustomSchemas.updateApiKeyAdminRequestSchema
}
