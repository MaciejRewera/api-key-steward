package apikeysteward.model

import apikeysteward.routes.model.admin.tenant.UpdateTenantRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class TenantUpdate(
    tenantId: UUID,
    name: String
)

object TenantUpdate {
  implicit val codec: Codec[TenantUpdate] = deriveCodec[TenantUpdate]

  def from(updateTenantRequest: UpdateTenantRequest): TenantUpdate = TenantUpdate(
    tenantId = updateTenantRequest.tenantId,
    name = updateTenantRequest.name
  )
}
