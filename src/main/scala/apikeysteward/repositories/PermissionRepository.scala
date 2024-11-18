package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.ReferencedApplicationDoesNotExistError
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.repositories.db.{ApiKeyTemplatesPermissionsDb, ApplicationDb, PermissionDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

class PermissionRepository(
    applicationDb: ApplicationDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb
)(transactor: Transactor[IO]) {

  def insert(
      publicApplicationId: ApplicationId,
      permission: Permission
  ): IO[Either[PermissionInsertionError, Permission]] =
    (for {
      applicationId <- EitherT
        .fromOptionF(
          applicationDb.getByPublicApplicationId(publicApplicationId),
          ReferencedApplicationDoesNotExistError(publicApplicationId)
        )
        .map(_.id)

      permissionEntityRead <- EitherT(permissionDb.insert(PermissionEntity.Write.from(applicationId, permission)))

      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def delete(
      publicApplicationId: ApplicationId,
      publicPermissionId: PermissionId
  ): IO[Either[PermissionNotFoundError, Permission]] =
    (for {
      permissionEntityRead <- EitherT(deleteOp(publicApplicationId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  private[repositories] def deleteOp(
      publicApplicationId: ApplicationId,
      publicPermissionId: PermissionId
  ): ConnectionIO[Either[PermissionNotFoundError, PermissionEntity.Read]] =
    for {
      _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId)
      deletedPermissionEntity <- permissionDb.delete(publicApplicationId, publicPermissionId)
    } yield deletedPermissionEntity

  def getBy(publicApplicationId: ApplicationId, publicPermissionId: PermissionId): IO[Option[Permission]] =
    (for {
      permissionEntityRead <- OptionT(permissionDb.getBy(publicApplicationId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def getAllFor(publicTemplateId: ApiKeyTemplateId): IO[List[Permission]] =
    permissionDb
      .getAllPermissionsForTemplate(publicTemplateId)
      .map(Permission.from)
      .compile
      .toList
      .transact(transactor)

  def getAllBy(publicApplicationId: ApplicationId)(nameFragment: Option[String]): IO[List[Permission]] =
    (for {
      permissionEntityRead <- permissionDb.getAllBy(publicApplicationId)(nameFragment)
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).compile.toList.transact(transactor)

}
