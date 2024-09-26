package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError
import apikeysteward.model.Tenant
import cats.effect.IO

import java.util.UUID

class TenantRepository(tenantDb: TenantDb) {

  def insert(tenant: Tenant): IO[Either[TenantInsertionError, Tenant]] = ???

  def update(tenant: Tenant): IO[Either[TenantDbError, Tenant]] = ???

  def getBy(publicId: UUID): IO[Option[Tenant]] = ???

  def delete(publicId: UUID): IO[Either[TenantDbError, Tenant]] = ???

}
