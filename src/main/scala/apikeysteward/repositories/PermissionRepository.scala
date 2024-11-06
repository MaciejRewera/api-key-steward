package apikeysteward.repositories

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.ReferencedApplicationDoesNotExistError
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.repositories.db.{ApplicationDb, PermissionDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

class PermissionRepository(applicationDb: ApplicationDb, permissionDb: PermissionDb)(transactor: Transactor[IO]) {

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
      permissionEntityRead <- EitherT(permissionDb.delete(publicApplicationId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def getBy(publicApplicationId: ApplicationId, publicPermissionId: PermissionId): IO[Option[Permission]] =
    (for {
      permissionEntityRead <- OptionT(permissionDb.getBy(publicApplicationId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def getAllBy(publicApplicationId: ApplicationId)(nameFragment: Option[String]): IO[List[Permission]] =
    (for {
      permissionEntityRead <- permissionDb.getAllBy(publicApplicationId)(nameFragment)
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).compile.toList.transact(transactor)

}
