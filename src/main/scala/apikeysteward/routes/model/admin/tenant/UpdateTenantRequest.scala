package apikeysteward.routes.model.admin.tenant

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import java.util.UUID

case class UpdateTenantRequest(
    tenantId: UUID,
    name: String
)

object UpdateTenantRequest {
  implicit val codec: Codec[UpdateTenantRequest] = deriveCodec[UpdateTenantRequest]

  implicit val updateTenantRequestSchema: Schema[UpdateTenantRequest] = TapirCustomSchemas.updateTenantRequestSchema
}
