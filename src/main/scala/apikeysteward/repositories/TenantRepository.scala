package apikeysteward.repositories

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantInsertionError, TenantNotFoundError}
import apikeysteward.model.{Tenant, TenantUpdate}
import apikeysteward.repositories.db.TenantDb
import apikeysteward.repositories.db.entity.TenantEntity
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TenantRepository(tenantDb: TenantDb)(transactor: Transactor[IO]) {

  def insert(tenant: Tenant): IO[Either[TenantInsertionError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.insert(TenantEntity.Write.from(tenant)))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def update(tenant: TenantUpdate): IO[Either[TenantDbError, Tenant]] = ???
//    (for {
//      tenantEntityRead <- OptionT(tenantDb.update(TenantEntity.Update.from(tenant)))
//      resultTenant = Tenant.from(tenantEntityRead)
//    } yield resultTenant)
//      .toRight(TenantNotFoundError(tenant.tenantId))
//      .value
//      .transact(transactor)

  def getBy(publicId: UUID): IO[Option[Tenant]] = ???

  def delete(publicId: UUID): IO[Either[TenantDbError, Tenant]] = ???

}
