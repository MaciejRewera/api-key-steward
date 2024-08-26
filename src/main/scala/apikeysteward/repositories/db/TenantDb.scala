package apikeysteward.repositories.db

import apikeysteward.repositories.db.DbCommons.TenantDbError
import apikeysteward.repositories.db.DbCommons.TenantDbError.TenantInsertionError
import apikeysteward.repositories.db.entity.TenantEntity

import java.util.UUID

class TenantDb {

  def insert(tenantEntity: TenantEntity.Write): doobie.ConnectionIO[Either[TenantInsertionError, TenantEntity.Read]] =
    ???

  def update(tenantEntity: TenantEntity.Write): doobie.ConnectionIO[Either[TenantDbError, TenantEntity.Read]] = ???

  def getBy(publicId: UUID): doobie.ConnectionIO[Option[TenantEntity.Read]] = ???

  def delete(publicId: UUID): doobie.ConnectionIO[Either[TenantDbError, TenantEntity.Read]] = ???

}
