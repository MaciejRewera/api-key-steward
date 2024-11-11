package apikeysteward.model

import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.TenantEntity
import apikeysteward.routes.model.admin.tenant.CreateTenantRequest
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class Tenant(
    tenantId: TenantId,
    name: String,
    description: Option[String],
    isActive: Boolean
)

object Tenant {
  implicit val encoder: Encoder[Tenant] = deriveEncoder[Tenant].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[Tenant] = deriveDecoder[Tenant]

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
