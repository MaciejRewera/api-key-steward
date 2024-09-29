package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSingleTenantResponse(
    tenant: Tenant
)

object GetSingleTenantResponse {
  implicit val codec: Codec[GetSingleTenantResponse] = deriveCodec[GetSingleTenantResponse]
}
