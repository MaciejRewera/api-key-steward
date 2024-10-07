package apikeysteward.routes.model.admin.tenant

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class UpdateTenantRequest(
    name: String,
    description: Option[String] = None
)

object UpdateTenantRequest {
  implicit val codec: Codec[UpdateTenantRequest] = deriveCodec[UpdateTenantRequest]

  implicit val updateTenantRequestSchema: Schema[UpdateTenantRequest] = TapirCustomSchemas.updateTenantRequestSchema
}
