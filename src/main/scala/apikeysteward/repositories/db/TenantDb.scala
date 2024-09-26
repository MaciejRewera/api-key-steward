package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError.TenantAlreadyExistsError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantInsertionError, TenantNotFoundError, tenantNotDisabledError, tenantNotFoundError}
import apikeysteward.repositories.db.entity.TenantEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class TenantDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(tenantEntity: TenantEntity.Write): doobie.ConnectionIO[Either[TenantInsertionError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(tenantEntity, now)
        .withUniqueGeneratedKeys[TenantEntity.Read](
          "id",
          "public_tenant_id",
          "name",
          "created_at",
          "updated_at",
          "disabled_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, tenantEntity.publicTenantId))

    } yield res
  }

  private def recoverSqlException(sqlException: SQLException, publicTenantId: String): TenantInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_tenant_id") =>
        TenantAlreadyExistsError(publicTenantId)
    }

  def update(tenantEntity: TenantEntity.Update): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      updateCount <- Queries.update(tenantEntity, now).run

      resOpt <-
        if (updateCount > 0) getByPublicTenantId(tenantEntity.publicTenantId)
        else Option.empty[TenantEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(TenantNotFoundError(tenantEntity.publicTenantId))
  }

  def getByPublicTenantId(publicTenantId: UUID): doobie.ConnectionIO[Option[TenantEntity.Read]] =
    getByPublicTenantId(publicTenantId.toString)

  private def getByPublicTenantId(publicTenantId: String): doobie.ConnectionIO[Option[TenantEntity.Read]] =
    Queries.getBy(publicTenantId).option

  def getAll: Stream[doobie.ConnectionIO, TenantEntity.Read] =
    Queries.getAll.stream

  def enable(publicTenantId: UUID): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] =
    for {
      disabledCount <- Queries.enable(publicTenantId.toString).run

      resOpt <-
        if (disabledCount > 0) getByPublicTenantId(publicTenantId)
        else Option.empty[TenantEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(TenantNotFoundError(publicTenantId.toString))

  def disable(publicTenantId: UUID): doobie.ConnectionIO[Either[TenantNotFoundError, TenantEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      disabledCount <- Queries.disable(publicTenantId.toString, now).run

      resOpt <-
        if (disabledCount > 0) getByPublicTenantId(publicTenantId)
        else Option.empty[TenantEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(TenantNotFoundError(publicTenantId.toString))
  }

  def deleteDisabled(publicTenantId: UUID): doobie.ConnectionIO[Either[TenantDbError, TenantEntity.Read]] =
    for {
      tenantToDeleteE <- getByPublicTenantId(publicTenantId).map(
        _.toRight(tenantNotFoundError(publicTenantId.toString))
      )

      resultE <- tenantToDeleteE.flatTraverse { result =>
        Either
          .cond(
            result.disabledAt.isDefined,
            Queries.deleteDisabled(publicTenantId.toString).run.map(_ => result),
            tenantNotDisabledError(publicTenantId)
          )
          .sequence
      }
    } yield resultE

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, public_tenant_id, name, created_at, updated_at, disabled_at"

    def insert(tenantEntity: TenantEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO tenant(public_tenant_id, name, created_at, updated_at)
            VALUES (
              ${tenantEntity.publicTenantId},
              ${tenantEntity.name},
              $now,
              $now
            )
            """.stripMargin.update

    def update(tenantEntity: TenantEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE tenant
            SET name = ${tenantEntity.name},
                updated_at = $now
            WHERE tenant.public_tenant_id = ${tenantEntity.publicTenantId}
           """.stripMargin.update

    def getBy(publicTenantId: String): doobie.Query0[TenantEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM tenant WHERE public_tenant_id = $publicTenantId").query[TenantEntity.Read]

    val getAll: doobie.Query0[TenantEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM tenant").query[TenantEntity.Read]

    def enable(publicTenantId: String): doobie.Update0 =
      sql"""UPDATE tenant
            SET disabled_at = NULL
            WHERE tenant.public_tenant_id = $publicTenantId
           """.stripMargin.update

    def disable(publicTenantId: String, now: Instant): doobie.Update0 =
      sql"""UPDATE tenant
            SET disabled_at = $now
            WHERE tenant.public_tenant_id = $publicTenantId
           """.stripMargin.update

    def deleteDisabled(publicTenantId: String): doobie.Update0 =
      sql"""DELETE FROM tenant
           WHERE tenant.public_tenant_id = $publicTenantId AND tenant.disabled_at IS NOT NULL
           """.stripMargin.update
  }
}
