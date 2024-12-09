package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.errors.PermissionDbError.PermissionInsertionError.ReferencedResourceServerDoesNotExistError
import apikeysteward.model.errors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.repositories.db.{ApiKeyTemplatesPermissionsDb, PermissionDb, ResourceServerDb, TenantDb}
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class PermissionRepository(
    uuidGenerator: UuidGenerator,
    tenantDb: TenantDb,
    resourceServerDb: ResourceServerDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb
)(transactor: Transactor[IO]) {

  def insert(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      permission: Permission
  ): IO[Either[PermissionInsertionError, Permission]] =
    for {
      permissionDbId <- uuidGenerator.generateUuid
      result <- insert(permissionDbId, publicTenantId, publicResourceServerId, permission)
    } yield result

  private def insert(
      permissionDbId: UUID,
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      permission: Permission
  ): IO[Either[PermissionInsertionError, Permission]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(
          tenantDb.getByPublicTenantId(publicTenantId),
          PermissionInsertionError.ReferencedTenantDoesNotExistError(publicTenantId)
        )
        .map(_.id)

      resourceServerId <- EitherT
        .fromOptionF(
          resourceServerDb.getByPublicResourceServerId(publicTenantId, publicResourceServerId),
          ReferencedResourceServerDoesNotExistError(publicResourceServerId)
        )
        .map(_.id)

      permissionEntityRead <- EitherT(
        permissionDb.insert(PermissionEntity.Write.from(tenantId, resourceServerId, permissionDbId, permission))
      )

      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def delete(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): IO[Either[PermissionNotFoundError, Permission]] =
    (for {
      permissionEntityRead <- EitherT(deleteOp(publicTenantId, publicResourceServerId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  private[repositories] def deleteOp(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): ConnectionIO[Either[PermissionNotFoundError, PermissionEntity.Read]] =
    for {
      _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId, publicPermissionId)
      deletedPermissionEntity <- permissionDb.delete(publicTenantId, publicResourceServerId, publicPermissionId)
    } yield deletedPermissionEntity

  def getBy(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): IO[Option[Permission]] =
    (for {
      permissionEntityRead <- OptionT(permissionDb.getBy(publicTenantId, publicResourceServerId, publicPermissionId))
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).value.transact(transactor)

  def getAllFor(publicTenantId: TenantId, publicTemplateId: ApiKeyTemplateId): IO[List[Permission]] =
    permissionDb
      .getAllForTemplate(publicTenantId, publicTemplateId)
      .map(Permission.from)
      .compile
      .toList
      .transact(transactor)

  def getAllBy(publicTenantId: TenantId, publicResourceServerId: ResourceServerId)(
      nameFragment: Option[String]
  ): IO[List[Permission]] =
    (for {
      permissionEntityRead <- permissionDb.getAllBy(publicTenantId, publicResourceServerId)(nameFragment)
      resultPermission = Permission.from(permissionEntityRead)
    } yield resultPermission).compile.toList.transact(transactor)

}
