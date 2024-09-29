package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ReactivateTenantResponse(
    tenant: Tenant
)

object ReactivateTenantResponse {
  implicit val codec: Codec[ReactivateTenantResponse] = deriveCodec[ReactivateTenantResponse]
}
