package apikeysteward.repositories.db.entity

import apikeysteward.model.{Tenant, TenantUpdate}

import java.time.Instant

object TenantEntity {

  case class Read(
      id: Long,
      publicTenantId: String,
      name: String,
      createdAt: Instant,
      updatedAt: Instant,
      disabledAt: Option[Instant]
  )

  case class Write(
      publicTenantId: String,
      name: String
  )

  object Write {
    def from(tenant: Tenant): TenantEntity.Write = TenantEntity.Write(
      publicTenantId = tenant.tenantId.toString,
      name = tenant.name
    )
  }

  case class Update(
      publicTenantId: String,
      name: String
  )

  object Update {
    def from(tenantUpdate: TenantUpdate): TenantEntity.Update = TenantEntity.Update(
      publicTenantId = tenantUpdate.tenantId.toString,
      name = tenantUpdate.name
    )
  }
}
