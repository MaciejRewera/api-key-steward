package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UpdateTenantResponse(
    tenant: Tenant
)

object UpdateTenantResponse {
  implicit val codec: Codec[UpdateTenantResponse] = deriveCodec[UpdateTenantResponse]

}
