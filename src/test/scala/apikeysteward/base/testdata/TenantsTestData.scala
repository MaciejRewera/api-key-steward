package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.model.{Tenant, TenantUpdate}
import apikeysteward.repositories.db.entity.TenantEntity

import java.util.UUID

object TenantsTestData extends FixedClock {

  val tenantDbId_1: UUID = UUID.randomUUID()
  val tenantDbId_2: UUID = UUID.randomUUID()
  val tenantDbId_3: UUID = UUID.randomUUID()

  val publicTenantId_1: UUID = UUID.randomUUID()
  val publicTenantId_2: UUID = UUID.randomUUID()
  val publicTenantId_3: UUID = UUID.randomUUID()
  val publicTenantId_4: UUID = UUID.randomUUID()
  val publicTenantIdStr_1: String = publicTenantId_1.toString
  val publicTenantIdStr_2: String = publicTenantId_2.toString
  val publicTenantIdStr_3: String = publicTenantId_3.toString
  val publicTenantIdStr_4: String = publicTenantId_4.toString

  val tenantName_1 = "Tenant Name 1"
  val tenantName_2 = "Tenant Name 2"
  val tenantName_3 = "Tenant Name 3"
  val tenantNameUpdated = "Updated Tenant Name"

  val tenantDescription_1: Option[String] = Some("Test Tenant description no. 1.")
  val tenantDescription_2: Option[String] = Some("Test Tenant description no. 2.")
  val tenantDescription_3: Option[String] = Some("Test Tenant description no. 3.")
  val tenantDescriptionUpdated: Option[String] = Some("Test Updated Tenant description.")

  val tenant_1: Tenant =
    Tenant(tenantId = publicTenantId_1, name = tenantName_1, description = tenantDescription_1, isActive = true)
  val tenant_2: Tenant =
    Tenant(tenantId = publicTenantId_2, name = tenantName_2, description = tenantDescription_2, isActive = true)
  val tenant_3: Tenant =
    Tenant(tenantId = publicTenantId_3, name = tenantName_3, description = tenantDescription_3, isActive = true)

  val tenantUpdate_1: TenantUpdate =
    TenantUpdate(tenantId = publicTenantId_1, name = tenantNameUpdated, description = tenantDescriptionUpdated)

  val tenantEntityWrite_1: TenantEntity.Write =
    TenantEntity.Write(
      id = tenantDbId_1,
      publicTenantId = publicTenantIdStr_1,
      name = tenantName_1,
      description = tenantDescription_1
    )
  val tenantEntityRead_1: TenantEntity.Read = TenantEntity.Read(
    id = tenantDbId_1,
    publicTenantId = publicTenantIdStr_1,
    name = tenantName_1,
    description = tenantDescription_1,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val tenantEntityWrite_2: TenantEntity.Write =
    TenantEntity.Write(
      id = tenantDbId_2,
      publicTenantId = publicTenantIdStr_2,
      name = tenantName_2,
      description = tenantDescription_2
    )
  val tenantEntityRead_2: TenantEntity.Read = TenantEntity.Read(
    id = tenantDbId_2,
    publicTenantId = publicTenantIdStr_2,
    name = tenantName_2,
    description = tenantDescription_2,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val tenantEntityWrite_3: TenantEntity.Write =
    TenantEntity.Write(
      id = tenantDbId_3,
      publicTenantId = publicTenantIdStr_3,
      name = tenantName_3,
      description = tenantDescription_3
    )
  val tenantEntityRead_3: TenantEntity.Read = TenantEntity.Read(
    id = tenantDbId_3,
    publicTenantId = publicTenantIdStr_3,
    name = tenantName_3,
    description = tenantDescription_3,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val tenantEntityUpdate_1: TenantEntity.Update = TenantEntity.Update(
    publicTenantId = publicTenantIdStr_1,
    name = tenantNameUpdated,
    description = tenantDescriptionUpdated
  )

  val deactivatedTenantEntityRead_1: TenantEntity.Read = tenantEntityRead_1.copy(deactivatedAt = Some(nowInstant))
}
