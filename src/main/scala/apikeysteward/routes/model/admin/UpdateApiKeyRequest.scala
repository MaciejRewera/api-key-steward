package apikeysteward.routes.model.admin

import apikeysteward.routes.model.{CreateUpdateApiKeyRequestBase, TapirCustomSchemas}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateApiKeyRequest(
    override val name: String,
    override val description: Option[String] = None
) extends CreateUpdateApiKeyRequestBase

object UpdateApiKeyRequest {
  implicit val codec: Codec[UpdateApiKeyRequest] = deriveCodec[UpdateApiKeyRequest]

  implicit val updateApiKeyRequestSchema: Schema[UpdateApiKeyRequest] = TapirCustomSchemas.updateApiKeyRequestSchema
}
