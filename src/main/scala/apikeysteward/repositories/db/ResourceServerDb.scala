package apikeysteward.repositories.db

import apikeysteward.model.errors.ResourceServerDbError
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.errors.ResourceServerDbError._
import apikeysteward.model.ResourceServer.ResourceServerId
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
          "updated_at"
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
      tenantDbId: UUID
  ): ResourceServerInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_resource_server_id") =>
        ResourceServerAlreadyExistsError(publicResourceServerId)

      case FOREIGN_KEY_VIOLATION.value => ReferencedTenantDoesNotExistError.fromDbId(tenantDbId)

      case _ => ResourceServerInsertionErrorImpl(sqlException)
    }

  def update(
      publicTenantId: TenantId,
      resourceServerEntity: ResourceServerEntity.Update
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicTenantId, resourceServerEntity.publicResourceServerId)(
      TenantIdScopedQueries(publicTenantId).update(resourceServerEntity, now).run
    )
  }

  private def performUpdate(publicTenantId: TenantId, publicResourceServerId: String)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[ResourceServerNotFoundError, ResourceServerEntity.Read]] =
    for {
      updateCount <- updateAction

      resOpt <-
        if (updateCount > 0) getByPublicResourceServerId(publicTenantId, publicResourceServerId)
        else Option.empty[ResourceServerEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(ResourceServerNotFoundError(publicResourceServerId))

  def delete(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Either[ResourceServerDbError, ResourceServerEntity.Read]] =
    for {
      resourceServerToDelete <- getByPublicResourceServerId(publicTenantId, publicResourceServerId).map(
        _.toRight(resourceServerNotFoundError(publicResourceServerId))
      )

      resultE <- resourceServerToDelete.traverse { result =>
        TenantIdScopedQueries(publicTenantId).delete(publicResourceServerId).run.map(_ => result)
      }
    } yield resultE

  def getByPublicResourceServerId(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId
  ): doobie.ConnectionIO[Option[ResourceServerEntity.Read]] =
    getByPublicResourceServerId(publicTenantId, publicResourceServerId.toString)

  private def getByPublicResourceServerId(
      publicTenantId: TenantId,
      publicResourceServerId: String
  ): doobie.ConnectionIO[Option[ResourceServerEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getBy(publicResourceServerId).option

  def getAllForTenant(publicTenantId: TenantId): Stream[doobie.ConnectionIO, ResourceServerEntity.Read] =
    TenantIdScopedQueries(publicTenantId).getAllForTenant.stream

  private object Queries {

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
  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "resource_server"

    private val columnNamesSelectFragment =
      fr"SELECT id, tenant_id, public_resource_server_id, name, description, created_at, updated_at"

    def update(resourceServerEntity: ResourceServerEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE resource_server
            SET name = ${resourceServerEntity.name},
                description = ${resourceServerEntity.description},
                updated_at = $now
            WHERE resource_server.public_resource_server_id = ${resourceServerEntity.publicResourceServerId}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    def delete(publicResourceServerId: ResourceServerId): doobie.Update0 =
      sql"""DELETE FROM resource_server
            WHERE resource_server.public_resource_server_id = ${publicResourceServerId.toString}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    def getBy(publicResourceServerId: String): doobie.Query0[ResourceServerEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM resource_server
              WHERE public_resource_server_id = $publicResourceServerId
                AND ${tenantIdFr(TableName)}
             """.stripMargin).query[ResourceServerEntity.Read]

    def getAllForTenant: doobie.Query0[ResourceServerEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM resource_server
              WHERE ${tenantIdFr(TableName)}
              ORDER BY resource_server.created_at DESC
             """.stripMargin).query[ResourceServerEntity.Read]

  }
}
