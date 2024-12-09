package apikeysteward.repositories

import apikeysteward.model.errors.ResourceServerDbError
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.errors.ResourceServerDbError._
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ResourceServer, ResourceServerUpdate}
import apikeysteward.repositories.db.entity.{PermissionEntity, ResourceServerEntity}
import apikeysteward.repositories.db.{PermissionDb, ResourceServerDb, TenantDb}
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._
import fs2.Stream

import java.util.UUID

class ResourceServerRepository(
    uuidGenerator: UuidGenerator,
    tenantDb: TenantDb,
    resourceServerDb: ResourceServerDb,
    permissionDb: PermissionDb,
    permissionRepository: PermissionRepository
)(transactor: Transactor[IO]) {

  def insert(
      publicTenantId: TenantId,
      resourceServer: ResourceServer
  ): IO[Either[ResourceServerInsertionError, ResourceServer]] =
    for {
      resourceServerDbId <- uuidGenerator.generateUuid
      permissionDbIds <- resourceServer.permissions.traverse(_ => uuidGenerator.generateUuid)
      result <- insert(resourceServerDbId, permissionDbIds, publicTenantId, resourceServer)
    } yield result

  private def insert(
      resourceServerDbId: UUID,
      permissionDbIds: List[UUID],
      publicTenantId: TenantId,
      resourceServer: ResourceServer
  ): IO[Either[ResourceServerInsertionError, ResourceServer]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      resourceServerEntityRead <- EitherT(
        resourceServerDb.insert(ResourceServerEntity.Write.from(resourceServerDbId, tenantId, resourceServer))
      )
      permissionEntities <- insertPermissions(tenantId, resourceServerEntityRead.id, permissionDbIds, resourceServer)

      resultResourceServer = ResourceServer.from(resourceServerEntityRead, permissionEntities)
    } yield resultResourceServer).value.transact(transactor)

  private def insertPermissions(
      tenantDbId: UUID,
      resourceServerDbId: UUID,
      permissionDbIds: List[UUID],
      resourceServer: ResourceServer
  ): EitherT[doobie.ConnectionIO, ResourceServerInsertionError, List[PermissionEntity.Read]] =
    EitherT {
      val permissionsToInsert = (resourceServer.permissions zip permissionDbIds).map {
        case (permission, permissionDbId) =>
          PermissionEntity.Write.from(tenantDbId, resourceServerDbId, permissionDbId, permission)
      }

      for {
        permissionEntities <- permissionsToInsert.traverse(permissionDb.insert).map(_.sequence)

        result = permissionEntities.left.map(cannotInsertPermissionError(resourceServer.resourceServerId, _))
      } yield result
    }

  def update(
      publicTenantId: TenantId,
      resourceServerUpdate: ResourceServerUpdate
  ): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    (for {
      resourceServerEntityRead <- EitherT(
        resourceServerDb.update(publicTenantId, ResourceServerEntity.Update.from(resourceServerUpdate))
      )
      resultResourceServer <- EitherT.liftF[doobie.ConnectionIO, ResourceServerNotFoundError, ResourceServer](
        constructResourceServer(publicTenantId, resourceServerEntityRead)
      )
    } yield resultResourceServer).value.transact(transactor)

  def activate(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    (for {
      resourceServerEntityRead <- EitherT(resourceServerDb.activate(publicTenantId, publicResourceServerId))
      resultResourceServer <- EitherT.liftF[doobie.ConnectionIO, ResourceServerNotFoundError, ResourceServer](
        constructResourceServer(publicTenantId, resourceServerEntityRead)
      )
    } yield resultResourceServer).value.transact(transactor)

  def deactivate(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    (for {
      resourceServerEntityRead <- EitherT(resourceServerDb.deactivate(publicTenantId, publicResourceServerId))
      resultResourceServer <- EitherT.liftF[doobie.ConnectionIO, ResourceServerNotFoundError, ResourceServer](
        constructResourceServer(publicTenantId, resourceServerEntityRead)
      )
    } yield resultResourceServer).value.transact(transactor)

  def delete(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): IO[Either[ResourceServerDbError, ResourceServer]] =
    deleteOp(publicTenantId, publicResourceServerId).transact(transactor)

  private[repositories] def deleteOp(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Either[ResourceServerDbError, ResourceServer]] =
    (for {
      _ <- verifyResourceServerIsDeactivatedOp(publicTenantId, publicResourceServerId)

      permissionEntitiesDeleted <- deletePermissions(publicTenantId, publicResourceServerId)
      resourceServerEntityRead <- EitherT(resourceServerDb.deleteDeactivated(publicTenantId, publicResourceServerId))

      resultResourceServer = ResourceServer.from(resourceServerEntityRead, permissionEntitiesDeleted)
    } yield resultResourceServer).value

  private[repositories] def verifyResourceServerIsDeactivatedOp(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): EitherT[doobie.ConnectionIO, ResourceServerDbError, Unit] =
    for {
      resourceServerToDelete <- EitherT(
        resourceServerDb
          .getByPublicResourceServerId(publicTenantId, publicResourceServerId)
          .map(_.toRight(resourceServerNotFoundError(publicResourceServerId)))
      )
      _ <- EitherT.cond[doobie.ConnectionIO](
        resourceServerToDelete.deactivatedAt.isDefined,
        (),
        resourceServerIsNotDeactivatedError(publicResourceServerId)
      )
    } yield ()

  private def deletePermissions(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): EitherT[doobie.ConnectionIO, ResourceServerDbError, List[PermissionEntity.Read]] =
    EitherT {
      for {
        permissionEntitiesToDelete <- permissionDb.getAllBy(publicTenantId, publicResourceServerId)(None).compile.toList
        permissionEntitiesDeletedE <- permissionEntitiesToDelete.traverse { entity =>
          permissionRepository.deleteOp(
            publicTenantId,
            publicResourceServerId,
            UUID.fromString(entity.publicPermissionId)
          )
        }.map(_.sequence)

        result = permissionEntitiesDeletedE.left.map(cannotDeletePermissionError(publicResourceServerId, _))
      } yield result
    }

  def getBy(publicTenantId: TenantId, publicResourceServerId: ResourceServerId): IO[Option[ResourceServer]] =
    (for {
      resourceServerEntityRead <- OptionT(
        resourceServerDb.getByPublicResourceServerId(publicTenantId, publicResourceServerId)
      )
      resultResourceServer <- OptionT.liftF(constructResourceServer(publicTenantId, resourceServerEntityRead))
    } yield resultResourceServer).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[ResourceServer]] =
    getAllForTenantOp(publicTenantId).compile.toList.transact(transactor)

  private[repositories] def getAllForTenantOp(publicTenantId: TenantId): Stream[doobie.ConnectionIO, ResourceServer] =
    for {
      resourceServerEntityRead <- resourceServerDb.getAllForTenant(publicTenantId)
      resultResourceServer <- Stream.eval(constructResourceServer(publicTenantId, resourceServerEntityRead))
    } yield resultResourceServer

  private def constructResourceServer(
      publicTenantId: TenantId,
      resourceServerEntity: ResourceServerEntity.Read
  ): doobie.ConnectionIO[ResourceServer] =
    for {
      permissionEntities <- permissionDb
        .getAllBy(publicTenantId, UUID.fromString(resourceServerEntity.publicResourceServerId))(None)
        .compile
        .toList

      resultResourceServer = ResourceServer.from(resourceServerEntity, permissionEntities)
    } yield resultResourceServer
}
