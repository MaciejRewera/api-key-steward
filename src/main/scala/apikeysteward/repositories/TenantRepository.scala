package apikeysteward.repositories

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantInsertionError, TenantNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{Tenant, TenantUpdate}
import apikeysteward.repositories.db.TenantDb
import apikeysteward.repositories.db.entity.TenantEntity
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

class TenantRepository(tenantDb: TenantDb)(transactor: Transactor[IO]) {

  def insert(tenant: Tenant): IO[Either[TenantInsertionError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.insert(TenantEntity.Write.from(tenant)))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def update(tenantUpdate: TenantUpdate): IO[Either[TenantNotFoundError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.update(TenantEntity.Update.from(tenantUpdate)))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def getBy(tenantId: TenantId): IO[Option[Tenant]] =
    (for {
      tenantEntityRead <- OptionT(tenantDb.getByPublicTenantId(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def getAll: IO[List[Tenant]] =
    (for {
      tenantEntityRead <- tenantDb.getAll
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).compile.toList.transact(transactor)

  def activate(tenantId: TenantId): IO[Either[TenantNotFoundError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.activate(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def deactivate(tenantId: TenantId): IO[Either[TenantNotFoundError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.deactivate(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def delete(tenantId: TenantId): IO[Either[TenantDbError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.deleteDeactivated(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

}
