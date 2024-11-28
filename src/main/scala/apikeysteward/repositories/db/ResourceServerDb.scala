package apikeysteward.repositories.db

import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.RepositoryErrors.ResourceServerDbError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.RepositoryErrors.ResourceServerDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ResourceServerEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2._

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class ResourceServerDb()(implicit clock: Clock) {

  def insert(
      resourceServerEntity: ResourceServerEntity.Write
  ): doobie.ConnectionIO[Either[ResourceServerInsertionError, ResourceServerEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(resourceServerEntity, now)
        .withUniqueGeneratedKeys[ResourceServerEntity.Read](
          "id",
          "tenant_id",
          "public_resource_server_id",
          "name",
          "description",
          "created_at",
          "updated_at",
          "deactivated_at"
        )
        .attemptSql

      res = eitherResult.left.map(
        recoverSqlException(_, resourceServerEntity.publicResourceServerId, resourceServerEntity.tenantId)
      )

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      publicResourceServerId: String,
      tenantId: UUID
  ): ResourceServerInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_resource_server_id") =>
        ResourceServerAlreadyExistsError(publicResourceServerId)

      case FOREIGN_KEY_VIOLATION.value => ReferencedTenantDoesNotExistError.fromDbId(tenantId)

      case _ => ResourceServerInsertionErrorImpl(sqlException)
    }

  def update(
      resourceServerEntity: ResourceServerEntity.Update
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(resourceServerEntity.publicResourceServerId)(Queries.update(resourceServerEntity, now).run)
  }

  def activate(
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicResourceServerId)(Queries.activate(publicResourceServerId, now).run)
  }

  def deactivate(
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicResourceServerId)(Queries.deactivate(publicResourceServerId, now).run)
  }

  private def performUpdate(publicResourceServerId: ResourceServerId)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] =
    performUpdate(publicResourceServerId.toString)(updateAction)

  private def performUpdate(publicResourceServerId: String)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] =
    for {
      updateCount <- updateAction

      resOpt <-
        if (updateCount > 0) getByPublicResourceServerId(publicResourceServerId)
        else Option.empty[ResourceServerEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(ResourceServerNotFoundError(publicResourceServerId))

  def deleteDeactivated(
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Either[ResourceServerDbError, ResourceServerEntity.Read]] =
    for {
      resourceServerToDelete <- getByPublicResourceServerId(publicResourceServerId).map(
        _.toRight(resourceServerNotFoundError(publicResourceServerId.toString))
      )

      resultE <- resourceServerToDelete.flatTraverse { result =>
        Either
          .cond(
            result.deactivatedAt.isDefined,
            Queries.deleteDeactivated(publicResourceServerId).run.map(_ => result),
            resourceServerIsNotDeactivatedError(publicResourceServerId)
          )
          .sequence
      }
    } yield resultE

  def getByPublicResourceServerId(
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Option[ResourceServerEntity.Read]] =
    getByPublicResourceServerId(publicResourceServerId.toString)

  private def getByPublicResourceServerId(
      publicResourceServerId: String
  ): doobie.ConnectionIO[Option[ResourceServerEntity.Read]] =
    Queries.getBy(publicResourceServerId).option

  def getAllForTenant(publicTenantId: TenantId): Stream[doobie.ConnectionIO, ResourceServerEntity.Read] =
    Queries.getAllForTenant(publicTenantId).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, tenant_id, public_resource_server_id, name, description, created_at, updated_at, deactivated_at"

    def insert(resourceServerEntity: ResourceServerEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO resource_server(id, tenant_id, public_resource_server_id, name, description, created_at, updated_at)
            VALUES(
              ${resourceServerEntity.id},
              ${resourceServerEntity.tenantId},
              ${resourceServerEntity.publicResourceServerId},
              ${resourceServerEntity.name},
              ${resourceServerEntity.description},
              $now,
              $now
            )
           """.stripMargin.update

    def update(resourceServerEntity: ResourceServerEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE resource_server
            SET name = ${resourceServerEntity.name},
                description = ${resourceServerEntity.description},
                updated_at = $now
            WHERE resource_server.public_resource_server_id = ${resourceServerEntity.publicResourceServerId}
           """.stripMargin.update

    def activate(publicResourceServerId: ResourceServerId, now: Instant): doobie.Update0 =
      sql"""UPDATE resource_server
            SET deactivated_at = NULL,
                updated_at = $now
            WHERE resource_server.public_resource_server_id = ${publicResourceServerId.toString}
           """.stripMargin.update

    def deactivate(publicResourceServerId: ResourceServerId, now: Instant): doobie.Update0 =
      sql"""UPDATE resource_server
            SET deactivated_at = $now,
                updated_at = $now
            WHERE resource_server.public_resource_server_id = ${publicResourceServerId.toString}
           """.stripMargin.update

    def deleteDeactivated(publicResourceServerId: ResourceServerId): doobie.Update0 =
      sql"""DELETE FROM resource_server
            WHERE resource_server.public_resource_server_id = ${publicResourceServerId.toString} AND resource_server.deactivated_at IS NOT NULL
           """.stripMargin.update

    def getBy(publicResourceServerId: String): doobie.Query0[ResourceServerEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM resource_server WHERE public_resource_server_id = $publicResourceServerId")
        .query[ResourceServerEntity.Read]

    def getAllForTenant(publicTenantId: TenantId): doobie.Query0[ResourceServerEntity.Read] =
      sql"""SELECT
              res.id,
              res.tenant_id,
              res.public_resource_server_id,
              res.name,
              res.description,
              res.created_at,
              res.updated_at,
              res.deactivated_at
            FROM resource_server AS res
            JOIN tenant ON tenant.id = res.tenant_id
            WHERE tenant.public_tenant_id = ${publicTenantId.toString}
            ORDER BY res.created_at DESC
           """.stripMargin.query[ResourceServerEntity.Read]

  }
}
