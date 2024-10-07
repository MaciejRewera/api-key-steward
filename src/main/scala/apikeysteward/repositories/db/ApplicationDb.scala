package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.{
  ApplicationAlreadyExistsError,
  ApplicationNotFoundError,
  applicationIsNotDeactivatedError,
  applicationNotFoundError
}
import apikeysteward.repositories.db.entity.ApplicationEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION
import fs2._

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class ApplicationDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(
      applicationEntity: ApplicationEntity.Write
  ): doobie.ConnectionIO[Either[ApplicationAlreadyExistsError, ApplicationEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(applicationEntity, now)
        .withUniqueGeneratedKeys[ApplicationEntity.Read](
          "id",
          "tenant_id",
          "public_application_id",
          "name",
          "description",
          "created_at",
          "updated_at",
          "deactivated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, applicationEntity.publicApplicationId))

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      publicApplicationId: String
  ): ApplicationAlreadyExistsError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_application_id") =>
        ApplicationAlreadyExistsError(publicApplicationId)
    }

  def update(
      applicationEntity: ApplicationEntity.Update
  ): doobie.ConnectionIO[Either[ApplicationNotFoundError, ApplicationEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(applicationEntity.publicApplicationId)(Queries.update(applicationEntity, now).run)
  }

  def activate(
      publicApplicationId: UUID
  ): doobie.ConnectionIO[Either[ApplicationNotFoundError, ApplicationEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicApplicationId)(Queries.activate(publicApplicationId.toString, now).run)
  }

  def deactivate(
      publicApplicationId: UUID
  ): doobie.ConnectionIO[Either[ApplicationNotFoundError, ApplicationEntity.Read]] = {
    val now = Instant.now(clock)
    performUpdate(publicApplicationId)(Queries.deactivate(publicApplicationId.toString, now).run)
  }

  private def performUpdate(publicApplicationId: UUID)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[ApplicationNotFoundError, ApplicationEntity.Read]] =
    performUpdate(publicApplicationId.toString)(updateAction)

  private def performUpdate(publicApplicationId: String)(
      updateAction: => doobie.ConnectionIO[Int]
  ): doobie.ConnectionIO[Either[ApplicationNotFoundError, ApplicationEntity.Read]] =
    for {
      updateCount <- updateAction

      resOpt <-
        if (updateCount > 0) getByPublicApplicationId(publicApplicationId)
        else Option.empty[ApplicationEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(ApplicationNotFoundError(publicApplicationId))

  def deleteDeactivated(
      publicApplicationId: UUID
  ): doobie.ConnectionIO[Either[ApplicationDbError, ApplicationEntity.Read]] =
    for {
      applicationToDelete <- getByPublicApplicationId(publicApplicationId).map(
        _.toRight(applicationNotFoundError(publicApplicationId.toString))
      )

      resultE <- applicationToDelete.flatTraverse { result =>
        Either
          .cond(
            result.deactivatedAt.isDefined,
            Queries.deleteDeactivated(publicApplicationId.toString).run.map(_ => result),
            applicationIsNotDeactivatedError(publicApplicationId)
          )
          .sequence
      }
    } yield resultE

  def getByPublicApplicationId(publicApplicationId: UUID): doobie.ConnectionIO[Option[ApplicationEntity.Read]] =
    getByPublicApplicationId(publicApplicationId.toString)

  private def getByPublicApplicationId(
      publicApplicationId: String
  ): doobie.ConnectionIO[Option[ApplicationEntity.Read]] =
    Queries.getBy(publicApplicationId).option

  // TODO: This method can potentially be removed.
  def getAll: Stream[doobie.ConnectionIO, ApplicationEntity.Read] =
    Queries.getAll.stream

  def getAllForTenant(publicTenantId: UUID): Stream[doobie.ConnectionIO, ApplicationEntity.Read] =
    Queries.getAllForTenant(publicTenantId.toString).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, tenant_id, public_application_id, name, description, created_at, updated_at, deactivated_at"

    def insert(applicationEntity: ApplicationEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO application(tenant_id, public_application_id, name, description, created_at, updated_at)
            VALUES(
              ${applicationEntity.tenantId},
              ${applicationEntity.publicApplicationId},
              ${applicationEntity.name},
              ${applicationEntity.description},
              $now,
              $now
            )
           """.stripMargin.update

    def update(applicationEntity: ApplicationEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE application
            SET name = ${applicationEntity.name},
                description = ${applicationEntity.description},
                updated_at = $now
            WHERE application.public_application_id = ${applicationEntity.publicApplicationId}
           """.stripMargin.update

    def activate(publicApplicationId: String, now: Instant): doobie.Update0 =
      sql"""UPDATE application
            SET deactivated_at = NULL,
                updated_at = $now
            WHERE application.public_application_id = $publicApplicationId
           """.stripMargin.update

    def deactivate(publicApplicationId: String, now: Instant): doobie.Update0 =
      sql"""UPDATE application
            SET deactivated_at = $now,
                updated_at = $now
            WHERE application.public_application_id = $publicApplicationId
           """.stripMargin.update

    def deleteDeactivated(publicApplicationId: String): doobie.Update0 =
      sql"""DELETE FROM application
            WHERE application.public_application_id = $publicApplicationId AND application.deactivated_at IS NOT NULL
           """.stripMargin.update

    def getBy(publicApplicationId: String): doobie.Query0[ApplicationEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM application WHERE public_application_id = $publicApplicationId").query[ApplicationEntity.Read]

    val getAll: doobie.Query0[ApplicationEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM application").query[ApplicationEntity.Read]

    def getAllForTenant(publicTenantId: String): doobie.Query0[ApplicationEntity.Read] =
      sql"""SELECT
              app.id,
              app.tenant_id,
              app.public_application_id,
              app.name,
              app.description,
              app.created_at,
              app.updated_at,
              app.deactivated_at
            FROM application AS app
            JOIN tenant ON tenant.id = app.tenant_id
            WHERE tenant.public_tenant_id = $publicTenantId
            ORDER BY app.created_at DESC
           """.stripMargin.query[ApplicationEntity.Read]

  }
}
