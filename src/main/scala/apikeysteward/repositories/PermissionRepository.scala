package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.ReferencedResourceServerDoesNotExistError
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.repositories.db.{ApiKeyTemplatesPermissionsDb, PermissionDb, ResourceServerDb}
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class PermissionRepository(
    uuidGenerator: UuidGenerator,
    resourceServerDb: ResourceServerDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb
)(transactor: Transactor[IO]) {

  def insert(
      publicResourceServerId: ResourceServerId,
      permission: Permission
  ): IO[Either[PermissionInsertionError, Permission]] =
    for {
      permissionDbId <- uuidGenerator.generateUuid
      result <- insert(permissionDbId, publicResourceServerId, permission)
    } yield result

  private def insert(
      permissionDbId: UUID,
      publicResourceServerId: ResourceServerId,
      permission: Permission
  ): IO[Either[PermissionInsertionError, Permission]] = {
    val publicTenantId = UUID.randomUUID()
    (for {
      resourceServerId <- EitherT
        .fromOptionF(
          resourceServerDb.getByPublicResourceServerId(publicTenantId, publicResourceServerId),
          ReferencedResourceServerDoesNotExistError(publicResourceServerId)
        )
        .map(_.id)

      permissionEntityRead <- EitherT(
        permissionDb.insert(PermissionEntity.Write.from(permissionDbId, resourceServerId, permission))
      )

      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)
  }

  def delete(
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): IO[Either[PermissionNotFoundError, Permission]] =
    (for {
      permissionEntityRead <- EitherT(deleteOp(publicResourceServerId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  private[repositories] def deleteOp(
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): ConnectionIO[Either[PermissionNotFoundError, PermissionEntity.Read]] =
    for {
      _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId)
      deletedPermissionEntity <- permissionDb.delete(publicResourceServerId, publicPermissionId)
    } yield deletedPermissionEntity

  def getBy(publicResourceServerId: ResourceServerId, publicPermissionId: PermissionId): IO[Option[Permission]] =
    (for {
      permissionEntityRead <- OptionT(permissionDb.getBy(publicResourceServerId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def getAllFor(publicTemplateId: ApiKeyTemplateId): IO[List[Permission]] =
    permissionDb
      .getAllForTemplate(publicTemplateId)
      .map(Permission.from)
      .compile
      .toList
      .transact(transactor)

  def getAllBy(publicResourceServerId: ResourceServerId)(nameFragment: Option[String]): IO[List[Permission]] =
    (for {
      permissionEntityRead <- permissionDb.getAllBy(publicResourceServerId)(nameFragment)
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).compile.toList.transact(transactor)

}
