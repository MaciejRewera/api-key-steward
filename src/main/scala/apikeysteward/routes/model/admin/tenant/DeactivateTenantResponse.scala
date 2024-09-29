package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeactivateTenantResponse(
    tenant: Tenant
)

object DeactivateTenantResponse {
  implicit val codec: Codec[DeactivateTenantResponse] = deriveCodec[DeactivateTenantResponse]
}
