package apikeysteward.repositories

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model._
import apikeysteward.repositories.db.TenantDb
import apikeysteward.repositories.db.entity.TenantEntity
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

class TenantRepository(
    tenantDb: TenantDb,
    applicationRepository: ApplicationRepository,
    apiKeyTemplateRepository: ApiKeyTemplateRepository,
    userRepository: UserRepository
)(transactor: Transactor[IO]) {

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
      _ <- verifyAllDependenciesAreDeactivated(publicTenantId)
      _ <- verifyTenantIsDeactivated(publicTenantId)

      _ <- deleteApplications(publicTenantId)
      _ <- deleteApiKeyTemplates(publicTenantId)
      _ <- deleteUsers(publicTenantId)

      tenantEntityRead <- EitherT(tenantDb.deleteDeactivated(publicTenantId))
      resultTenant = Tenant.from(tenantEntityRead)
    } yield resultTenant).value.transact(transactor)

  private def verifyAllDependenciesAreDeactivated(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, TenantDbError, Unit] =
    for {
      publicApplicationIds <- EitherT.liftF(
        applicationRepository
          .getAllForTenantOp(publicTenantId)
          .map(_.applicationId)
          .compile
          .toList
      )
      _ <- publicApplicationIds.traverse { publicApplicationId =>
        applicationRepository
          .verifyApplicationIsDeactivatedOp(publicApplicationId)
          .leftMap(cannotDeleteDependencyError(publicTenantId, _))
      }
    } yield ()

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

  private def deleteApplications(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, CannotDeleteDependencyError, List[Application]] =
    EitherT {
      for {
        applicationIdsToDelete <- applicationRepository
          .getAllForTenantOp(publicTenantId)
          .map(_.applicationId)
          .compile
          .toList
        deletedApplications <- applicationIdsToDelete.traverse(applicationRepository.deleteOp)
      } yield deletedApplications
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
        deletedApiKeyTemplates <- apiKeyTemplateIdsToDelete.traverse(apiKeyTemplateRepository.deleteOp)
      } yield deletedApiKeyTemplates
        .map(_.left.map(CannotDeleteDependencyError(publicTenantId, _)))
        .sequence
    }

  private def deleteUsers(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, CannotDeleteDependencyError, List[User]] =
    EitherT {
      for {
        userIdsToDelete <- userRepository.getAllForTenantOp(publicTenantId).map(_.userId).compile.toList
        deletedUsers <- userIdsToDelete.traverse(userRepository.deleteOp(publicTenantId, _))
      } yield deletedUsers
        .map(_.left.map(CannotDeleteDependencyError(publicTenantId, _)))
        .sequence
    }

}
