package apikeysteward.model

import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.model.admin.tenant.UpdateTenantRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class TenantUpdate(
    tenantId: TenantId,
    name: String,
    description: Option[String]
)

object TenantUpdate {
  implicit val codec: Codec[TenantUpdate] = deriveCodec[TenantUpdate]

  def from(tenantId: TenantId, updateTenantRequest: UpdateTenantRequest): TenantUpdate =
    TenantUpdate(
      tenantId = tenantId,
      name = updateTenantRequest.name,
      description = updateTenantRequest.description
    )
}
