package apikeysteward.repositories

import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.RepositoryErrors.ResourceServerDbError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.RepositoryErrors.ResourceServerDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ResourceServer, ResourceServerUpdate}
import apikeysteward.repositories.db.entity.{ResourceServerEntity, PermissionEntity}
import apikeysteward.repositories.db.{ResourceServerDb, PermissionDb, TenantDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._
import fs2.Stream

import java.util.UUID

class ResourceServerRepository(
    tenantDb: TenantDb,
    resourceServerDb: ResourceServerDb,
    permissionDb: PermissionDb,
    permissionRepository: PermissionRepository
)(
    transactor: Transactor[IO]
) {

  def insert(
      publicTenantId: TenantId,
      resourceServer: ResourceServer
  ): IO[Either[ResourceServerInsertionError, ResourceServer]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      resourceServerEntityRead <- EitherT(
        resourceServerDb.insert(ResourceServerEntity.Write.from(tenantId, resourceServer))
      )
      permissionEntities <- insertPermissions(resourceServerEntityRead.id, resourceServer)

      resultResourceServer = ResourceServer.from(resourceServerEntityRead, permissionEntities)
    } yield resultResourceServer).value.transact(transactor)

  private def insertPermissions(
      resourceServerId: Long,
      resourceServer: ResourceServer
  ): EitherT[doobie.ConnectionIO, ResourceServerInsertionError, List[PermissionEntity.Read]] =
    EitherT {
      val permissionsToInsert = resourceServer.permissions.map(PermissionEntity.Write.from(resourceServerId, _))
      for {
        permissionEntities <- permissionsToInsert.traverse(permissionDb.insert).map(_.sequence)

        result = permissionEntities.left.map(cannotInsertPermissionError(resourceServer.resourceServerId, _))
      } yield result
    }

  def update(resourceServerUpdate: ResourceServerUpdate): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    (for {
      resourceServerEntityRead <- EitherT(
        resourceServerDb.update(ResourceServerEntity.Update.from(resourceServerUpdate))
      )
      resultResourceServer <- EitherT.liftF[doobie.ConnectionIO, ResourceServerNotFoundError, ResourceServer](
        constructResourceServer(resourceServerEntityRead)
      )
    } yield resultResourceServer).value.transact(transactor)

  def activate(publicResourceServerId: ResourceServerId): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    (for {
      resourceServerEntityRead <- EitherT(resourceServerDb.activate(publicResourceServerId))
      resultResourceServer <- EitherT.liftF[doobie.ConnectionIO, ResourceServerNotFoundError, ResourceServer](
        constructResourceServer(resourceServerEntityRead)
      )
    } yield resultResourceServer).value.transact(transactor)

  def deactivate(publicResourceServerId: ResourceServerId): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    (for {
      resourceServerEntityRead <- EitherT(resourceServerDb.deactivate(publicResourceServerId))
      resultResourceServer <- EitherT.liftF[doobie.ConnectionIO, ResourceServerNotFoundError, ResourceServer](
        constructResourceServer(resourceServerEntityRead)
      )
    } yield resultResourceServer).value.transact(transactor)

  def delete(publicResourceServerId: ResourceServerId): IO[Either[ResourceServerDbError, ResourceServer]] =
    deleteOp(publicResourceServerId).transact(transactor)

  private[repositories] def deleteOp(
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Either[ResourceServerDbError, ResourceServer]] =
    (for {
      _ <- verifyResourceServerIsDeactivatedOp(publicResourceServerId)

      permissionEntitiesDeleted <- deletePermissions(publicResourceServerId)
      resourceServerEntityRead <- EitherT(resourceServerDb.deleteDeactivated(publicResourceServerId))

      resultResourceServer = ResourceServer.from(resourceServerEntityRead, permissionEntitiesDeleted)
    } yield resultResourceServer).value

  private[repositories] def verifyResourceServerIsDeactivatedOp(
      publicResourceServerId: ResourceServerId
  ): EitherT[doobie.ConnectionIO, ResourceServerDbError, Unit] =
    for {
      resourceServerToDelete <- EitherT(
        resourceServerDb
          .getByPublicResourceServerId(publicResourceServerId)
          .map(_.toRight(resourceServerNotFoundError(publicResourceServerId)))
      )
      _ <- EitherT.cond[doobie.ConnectionIO](
        resourceServerToDelete.deactivatedAt.isDefined,
        (),
        resourceServerIsNotDeactivatedError(publicResourceServerId)
      )
    } yield ()

  private def deletePermissions(
      publicResourceServerId: ResourceServerId
  ): EitherT[doobie.ConnectionIO, ResourceServerDbError, List[PermissionEntity.Read]] =
    EitherT {
      for {
        permissionEntitiesToDelete <- permissionDb.getAllBy(publicResourceServerId)(None).compile.toList
        permissionEntitiesDeletedE <- permissionEntitiesToDelete.traverse { entity =>
          permissionRepository.deleteOp(publicResourceServerId, UUID.fromString(entity.publicPermissionId))
        }.map(_.sequence)

        result = permissionEntitiesDeletedE.left.map(cannotDeletePermissionError(publicResourceServerId, _))
      } yield result
    }

  def getBy(publicResourceServerId: ResourceServerId): IO[Option[ResourceServer]] =
    (for {
      resourceServerEntityRead <- OptionT(resourceServerDb.getByPublicResourceServerId(publicResourceServerId))
      resultResourceServer <- OptionT.liftF(constructResourceServer(resourceServerEntityRead))
    } yield resultResourceServer).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[ResourceServer]] =
    getAllForTenantOp(publicTenantId).compile.toList.transact(transactor)

  private[repositories] def getAllForTenantOp(publicTenantId: TenantId): Stream[doobie.ConnectionIO, ResourceServer] =
    for {
      resourceServerEntityRead <- resourceServerDb.getAllForTenant(publicTenantId)
      resultResourceServer <- Stream.eval(constructResourceServer(resourceServerEntityRead))
    } yield resultResourceServer

  private def constructResourceServer(
      resourceServerEntity: ResourceServerEntity.Read
  ): doobie.ConnectionIO[ResourceServer] =
    for {
      permissionEntities <- permissionDb
        .getAllBy(UUID.fromString(resourceServerEntity.publicResourceServerId))(None)
        .compile
        .toList

      resultResourceServer = ResourceServer.from(resourceServerEntity, permissionEntities)
    } yield resultResourceServer
}
