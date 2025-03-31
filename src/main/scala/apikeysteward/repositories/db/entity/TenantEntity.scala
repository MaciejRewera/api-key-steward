package apikeysteward.repositories.db.entity

import apikeysteward.model.{Tenant, TenantUpdate}

import java.time.Instant
import java.util.UUID

object TenantEntity {

  case class Read(
      id: UUID,
      publicTenantId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant,
      deactivatedAt: Option[Instant]
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      publicTenantId: String,
      name: String,
      description: Option[String]
  )

  object Write {

    def from(id: UUID, tenant: Tenant): TenantEntity.Write =
      TenantEntity.Write(
        id = id,
        publicTenantId = tenant.tenantId.toString,
        name = tenant.name,
        description = tenant.description
      )

  }

  case class Update(
      publicTenantId: String,
      name: String,
      description: Option[String]
  )

  object Update {

    def from(tenantUpdate: TenantUpdate): TenantEntity.Update =
      TenantEntity.Update(
        publicTenantId = tenantUpdate.tenantId.toString,
        name = tenantUpdate.name,
        description = tenantUpdate.description
      )

  }

}
