package apikeysteward.repositories

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError
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

  def update(tenantUpdate: TenantUpdate): IO[Either[TenantDbError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.update(TenantEntity.Update.from(tenantUpdate)))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def getBy(tenantId: UUID): IO[Option[Tenant]] =
    (for {
      tenantEntityRead <- OptionT(tenantDb.getByPublicTenantId(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def getAll: IO[List[Tenant]] =
    (for {
      tenantEntityRead <- tenantDb.getAll
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).compile.toList.transact(transactor)

  def enable(tenantId: UUID): IO[Either[TenantDbError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.enable(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def disable(tenantId: UUID): IO[Either[TenantDbError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.disable(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def delete(tenantId: UUID): IO[Either[TenantDbError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.deleteDisabled(tenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

}
