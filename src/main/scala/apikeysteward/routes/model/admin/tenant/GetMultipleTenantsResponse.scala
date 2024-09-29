package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleTenantsResponse(
    tenants: List[Tenant],
    total: Int
)

object GetMultipleTenantsResponse {
  implicit val codec: Codec[GetMultipleTenantsResponse] = deriveCodec[GetMultipleTenantsResponse]
}
