package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteTenantResponse(
    tenant: Tenant
)

object DeleteTenantResponse {
  implicit val codec: Codec[DeleteTenantResponse] = deriveCodec[DeleteTenantResponse]
}
