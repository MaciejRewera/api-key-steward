package apikeysteward.routes.model.admin.tenant

import apikeysteward.model.Tenant
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateTenantResponse(
    tenant: Tenant
)

object CreateTenantResponse {
  implicit val codec: Codec[CreateTenantResponse] = deriveCodec[CreateTenantResponse]

}
