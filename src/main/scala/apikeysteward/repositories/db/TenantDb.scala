package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError._
import apikeysteward.model.RepositoryErrors.TenantDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.TenantEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class TenantDb()(implicit clock: Clock) {

  def insert(tenantEntity: TenantEntity.Write): doobie.ConnectionIO[Either[TenantInsertionError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(tenantEntity, now)
        .withUniqueGeneratedKeys[TenantEntity.Read](
          "id",
          "public_tenant_id",
          "name",
          "description",
          "created_at",
          "updated_at",
          "deactivated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, tenantEntity.publicTenantId))

    } yield res
  }

  private def recoverSqlException(sqlException: SQLException, publicTenantId: String): TenantInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_tenant_id") =>
        TenantAlreadyExistsError(publicTenantId)

      case _ => TenantInsertionErrorImpl(sqlException)
    }

  def update(tenantEntity: TenantEntity.Update): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(tenantEntity.publicTenantId)(Queries.update(tenantEntity, now).run)
  }

  def activate(publicTenantId: TenantId): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicTenantId)(Queries.activate(publicTenantId.toString, now).run)
  }

  def deactivate(publicTenantId: TenantId): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicTenantId)(Queries.deactivate(publicTenantId.toString, now).run)
  }

  private def performUpdate(publicTenantId: TenantId)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] =
    performUpdate(publicTenantId.toString)(updateAction)

  private def performUpdate(publicTenantId: String)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] =
    for {
      updateCount <- updateAction

      resOpt <-
        if (updateCount > 0) getByPublicTenantId(publicTenantId)
        else Option.empty[TenantEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(TenantNotFoundError(publicTenantId))

  def deleteDeactivated(publicTenantId: TenantId): doobie.ConnectionIO[Either[TenantDbError, TenantEntity.Read]] =
    for {
      tenantToDeleteE <- getByPublicTenantId(publicTenantId).map(
        _.toRight(tenantNotFoundError(publicTenantId.toString))
      )

      resultE <- tenantToDeleteE.flatTraverse { result =>
        Either
          .cond(
            result.deactivatedAt.isDefined,
            Queries.deleteDeactivated(publicTenantId.toString).run.map(_ => result),
            tenantIsNotDeactivatedError(publicTenantId)
          )
          .sequence
      }
    } yield resultE

  def getByPublicTenantId(publicTenantId: TenantId): doobie.ConnectionIO[Option[TenantEntity.Read]] =
    getByPublicTenantId(publicTenantId.toString)

  private def getByPublicTenantId(publicTenantId: String): doobie.ConnectionIO[Option[TenantEntity.Read]] =
    Queries.getBy(publicTenantId).option

  def getAll: Stream[doobie.ConnectionIO, TenantEntity.Read] =
    Queries.getAll.stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, public_tenant_id, name, description, created_at, updated_at, deactivated_at"

    def insert(tenantEntity: TenantEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO tenant(id, public_tenant_id, name, description, created_at, updated_at)
            VALUES (
              ${tenantEntity.id},
              ${tenantEntity.publicTenantId},
              ${tenantEntity.name},
              ${tenantEntity.description},
              $now,
              $now
            )
            """.stripMargin.update

    def update(tenantEntity: TenantEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE tenant
            SET name = ${tenantEntity.name},
                description = ${tenantEntity.description},
                updated_at = $now
            WHERE tenant.public_tenant_id = ${tenantEntity.publicTenantId}
           """.stripMargin.update

    def activate(publicTenantId: String, now: Instant): doobie.Update0 =
      sql"""UPDATE tenant
            SET deactivated_at = NULL,
                updated_at = $now
            WHERE tenant.public_tenant_id = $publicTenantId
           """.stripMargin.update

    def deactivate(publicTenantId: String, now: Instant): doobie.Update0 =
      sql"""UPDATE tenant
            SET deactivated_at = $now,
                updated_at = $now
            WHERE tenant.public_tenant_id = $publicTenantId
           """.stripMargin.update

    def deleteDeactivated(publicTenantId: String): doobie.Update0 =
      sql"""DELETE FROM tenant
           WHERE tenant.public_tenant_id = $publicTenantId AND tenant.deactivated_at IS NOT NULL
           """.stripMargin.update

    def getBy(publicTenantId: String): doobie.Query0[TenantEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM tenant WHERE public_tenant_id = $publicTenantId").query[TenantEntity.Read]

    val getAll: doobie.Query0[TenantEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM tenant").query[TenantEntity.Read]

  }
}
