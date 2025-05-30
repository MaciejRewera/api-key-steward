package apikeysteward.repositories

import apikeysteward.model.errors.TenantDbError
import apikeysteward.model.errors.TenantDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model._
import apikeysteward.repositories.db.TenantDb
import apikeysteward.repositories.db.entity.TenantEntity
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TenantRepository(
    uuidGenerator: UuidGenerator,
    tenantDb: TenantDb,
    resourceServerRepository: ResourceServerRepository,
    apiKeyTemplateRepository: ApiKeyTemplateRepository,
    userRepository: UserRepository
)(transactor: Transactor[IO]) {

  def insert(tenant: Tenant): IO[Either[TenantInsertionError, Tenant]] =
    for {
      tenantDbId <- uuidGenerator.generateUuid
      result     <- insert(tenantDbId, tenant)
    } yield result

  private def insert(tenantDbId: UUID, tenant: Tenant): IO[Either[TenantInsertionError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.insert(TenantEntity.Write.from(tenantDbId, tenant)))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def update(tenantUpdate: TenantUpdate): IO[Either[TenantNotFoundError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.update(TenantEntity.Update.from(tenantUpdate)))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def getBy(publicTenantId: TenantId): IO[Option[Tenant]] =
    (for {
      tenantEntityRead <- OptionT(tenantDb.getByPublicTenantId(publicTenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def getAll: IO[List[Tenant]] =
    (for {
      tenantEntityRead <- tenantDb.getAll
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).compile.toList.transact(transactor)

  def activate(publicTenantId: TenantId): IO[Either[TenantNotFoundError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.activate(publicTenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def deactivate(publicTenantId: TenantId): IO[Either[TenantNotFoundError, Tenant]] =
    (for {
      tenantEntityRead <- EitherT(tenantDb.deactivate(publicTenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  def delete(publicTenantId: TenantId): IO[Either[TenantDbError, Tenant]] =
    (for {
      _ <- verifyTenantIsDeactivated(publicTenantId)

      _ <- deleteUsers(publicTenantId)
      _ <- deleteApiKeyTemplates(publicTenantId)
      _ <- deleteResourceServers(publicTenantId)

      tenantEntityRead <- EitherT(tenantDb.deleteDeactivated(publicTenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  private def verifyTenantIsDeactivated(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, TenantDbError, Unit] =
    for {
      tenantToDelete <- EitherT(
        tenantDb
          .getByPublicTenantId(publicTenantId)
          .map(_.toRight(tenantNotFoundError(publicTenantId)))
      )
      _ <- EitherT.cond[doobie.ConnectionIO](
        tenantToDelete.deactivatedAt.isDefined,
        (),
        tenantIsNotDeactivatedError(publicTenantId)
      )
    } yield ()

  private def deleteUsers(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, CannotDeleteDependencyError, List[User]] =
    EitherT {
      for {
        userIdsToDelete <- userRepository.getAllForTenantOp(publicTenantId).map(_.userId).compile.toList
        deletedUsers    <- userIdsToDelete.traverse(userRepository.deleteOp(publicTenantId, _))
      } yield deletedUsers
        .map(_.left.map(CannotDeleteDependencyError(publicTenantId, _)))
        .sequence
    }

  private def deleteApiKeyTemplates(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, CannotDeleteDependencyError, List[ApiKeyTemplate]] =
    EitherT {
      for {
        apiKeyTemplateIdsToDelete <- apiKeyTemplateRepository
          .getAllForTenantOp(publicTenantId)
          .map(_.publicTemplateId)
          .compile
          .toList
        deletedApiKeyTemplates <- apiKeyTemplateIdsToDelete.traverse(
          apiKeyTemplateRepository.deleteOp(publicTenantId, _)
        )
      } yield deletedApiKeyTemplates
        .map(_.left.map(CannotDeleteDependencyError(publicTenantId, _)))
        .sequence
    }

  private def deleteResourceServers(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, CannotDeleteDependencyError, List[ResourceServer]] =
    EitherT {
      for {
        resourceServerIdsToDelete <- resourceServerRepository
          .getAllForTenantOp(publicTenantId)
          .map(_.resourceServerId)
          .compile
          .toList
        deletedResourceServers <- resourceServerIdsToDelete.traverse(
          resourceServerRepository.deleteOp(publicTenantId, _)
        )
      } yield deletedResourceServers
        .map(_.left.map(CannotDeleteDependencyError(publicTenantId, _)))
        .sequence
    }

}
