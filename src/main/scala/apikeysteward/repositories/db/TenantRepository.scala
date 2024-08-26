package apikeysteward.repositories.db

import apikeysteward.model.Tenant
import apikeysteward.repositories.db.DbCommons.TenantDbError
import apikeysteward.repositories.db.DbCommons.TenantDbError.TenantInsertionError
import cats.effect.IO

import java.util.UUID

class TenantRepository(tenantDb: TenantDb) {

  def insert(tenant: Tenant): IO[Either[TenantInsertionError, Tenant]] = ???

  def update(tenant: Tenant): IO[Either[TenantDbError, Tenant]] = ???

  def getBy(publicId: UUID): IO[Option[Tenant]] = ???

  def delete(publicId: UUID): IO[Either[TenantDbError, Tenant]] = ???

}
