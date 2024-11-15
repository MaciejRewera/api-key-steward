package apikeysteward.repositories

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError._
import apikeysteward.model.RepositoryErrors.ApplicationDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{Application, ApplicationUpdate}
import apikeysteward.repositories.db.entity.{ApplicationEntity, PermissionEntity}
import apikeysteward.repositories.db.{ApplicationDb, PermissionDb, TenantDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._
import fs2.Stream

import java.util.UUID

class ApplicationRepository(
    tenantDb: TenantDb,
    applicationDb: ApplicationDb,
    permissionDb: PermissionDb,
    permissionRepository: PermissionRepository
)(
    transactor: Transactor[IO]
) {

  def insert(publicTenantId: TenantId, application: Application): IO[Either[ApplicationInsertionError, Application]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      applicationEntityRead <- EitherT(applicationDb.insert(ApplicationEntity.Write.from(tenantId, application)))
      permissionEntities <- insertPermissions(applicationEntityRead.id, application)

      resultApplication = Application.from(applicationEntityRead, permissionEntities)
    } yield resultApplication).value.transact(transactor)

  private def insertPermissions(
      applicationId: Long,
      application: Application
  ): EitherT[doobie.ConnectionIO, ApplicationInsertionError, List[PermissionEntity.Read]] =
    EitherT {
      val permissionsToInsert = application.permissions.map(PermissionEntity.Write.from(applicationId, _))
      for {
        permissionEntities <- permissionsToInsert.traverse(permissionDb.insert).map(_.sequence)

        result = permissionEntities.left.map(cannotInsertPermissionError(application.applicationId, _))
      } yield result
    }

  def update(applicationUpdate: ApplicationUpdate): IO[Either[ApplicationNotFoundError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.update(ApplicationEntity.Update.from(applicationUpdate)))
      resultApplication <- EitherT.liftF[doobie.ConnectionIO, ApplicationNotFoundError, Application](
        constructApplication(applicationEntityRead)
      )
    } yield resultApplication).value.transact(transactor)

  def activate(publicApplicationId: ApplicationId): IO[Either[ApplicationNotFoundError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.activate(publicApplicationId))
      resultApplication <- EitherT.liftF[doobie.ConnectionIO, ApplicationNotFoundError, Application](
        constructApplication(applicationEntityRead)
      )
    } yield resultApplication).value.transact(transactor)

  def deactivate(publicApplicationId: ApplicationId): IO[Either[ApplicationNotFoundError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.deactivate(publicApplicationId))
      resultApplication <- EitherT.liftF[doobie.ConnectionIO, ApplicationNotFoundError, Application](
        constructApplication(applicationEntityRead)
      )
    } yield resultApplication).value.transact(transactor)

  def delete(publicApplicationId: ApplicationId): IO[Either[ApplicationDbError, Application]] =
    deleteOp(publicApplicationId).transact(transactor)

  private[repositories] def deleteOp(
      publicApplicationId: ApplicationId
  ): doobie.ConnectionIO[Either[ApplicationDbError, Application]] =
    (for {
      _ <- verifyApplicationIsDeactivatedOp(publicApplicationId)

      permissionEntitiesDeleted <- deletePermissions(publicApplicationId)
      applicationEntityRead <- EitherT(applicationDb.deleteDeactivated(publicApplicationId))

      resultApplication = Application.from(applicationEntityRead, permissionEntitiesDeleted)
    } yield resultApplication).value

  private[repositories] def verifyApplicationIsDeactivatedOp(
      publicApplicationId: ApplicationId
  ): EitherT[doobie.ConnectionIO, ApplicationDbError, Unit] =
    for {
      applicationToDelete <- EitherT(
        applicationDb
          .getByPublicApplicationId(publicApplicationId)
          .map(_.toRight(applicationNotFoundError(publicApplicationId)))
      )
      _ <- EitherT.cond[doobie.ConnectionIO](
        applicationToDelete.deactivatedAt.isDefined,
        (),
        applicationIsNotDeactivatedError(publicApplicationId)
      )
    } yield ()

  private def deletePermissions(
      publicApplicationId: ApplicationId
  ): EitherT[doobie.ConnectionIO, ApplicationDbError, List[PermissionEntity.Read]] =
    EitherT {
      for {
        permissionEntitiesToDelete <- permissionDb.getAllBy(publicApplicationId)(None).compile.toList
        permissionEntitiesDeletedE <- permissionEntitiesToDelete.traverse { entity =>
          permissionRepository.deleteOp(publicApplicationId, UUID.fromString(entity.publicPermissionId))
        }.map(_.sequence)

        result = permissionEntitiesDeletedE.left.map(cannotDeletePermissionError(publicApplicationId, _))
      } yield result
    }

  def getBy(publicApplicationId: ApplicationId): IO[Option[Application]] =
    (for {
      applicationEntityRead <- OptionT(applicationDb.getByPublicApplicationId(publicApplicationId))
      resultApplication <- OptionT.liftF(constructApplication(applicationEntityRead))
    } yield resultApplication).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[Application]] =
    getAllForTenantOp(publicTenantId).compile.toList.transact(transactor)

  private[repositories] def getAllForTenantOp(publicTenantId: TenantId): Stream[doobie.ConnectionIO, Application] =
    for {
      applicationEntityRead <- applicationDb.getAllForTenant(publicTenantId)
      resultApplication <- Stream.eval(constructApplication(applicationEntityRead))
    } yield resultApplication

  private def constructApplication(applicationEntity: ApplicationEntity.Read): doobie.ConnectionIO[Application] =
    for {
      permissionEntities <- permissionDb
        .getAllBy(UUID.fromString(applicationEntity.publicApplicationId))(None)
        .compile
        .toList

      resultApplication = Application.from(applicationEntity, permissionEntities)
    } yield resultApplication
}
