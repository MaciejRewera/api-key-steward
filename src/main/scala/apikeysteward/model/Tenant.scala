package apikeysteward.model

import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.TenantEntity
import apikeysteward.routes.model.admin.tenant.CreateTenantRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class Tenant(
    tenantId: TenantId,
    name: String,
    description: Option[String],
    isActive: Boolean
)

object Tenant {
  implicit val codec: Codec[Tenant] = deriveCodec[Tenant]

  type TenantId = UUID

  def from(tenantEntityRead: TenantEntity.Read): Tenant = Tenant(
    tenantId = UUID.fromString(tenantEntityRead.publicTenantId),
    name = tenantEntityRead.name,
    description = tenantEntityRead.description,
    isActive = tenantEntityRead.deactivatedAt.isEmpty
  )

  def from(tenantId: TenantId, createTenantRequest: CreateTenantRequest): Tenant = Tenant(
    tenantId = tenantId,
    name = createTenantRequest.name,
    description = createTenantRequest.description,
    isActive = true
  )
}
