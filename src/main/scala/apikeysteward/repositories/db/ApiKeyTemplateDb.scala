package apikeysteward.repositories.db

import apikeysteward.repositories.db.DbCommons.ApiKeyTemplateInsertionError.ApiKeyTemplateAlreadyExistsError
import apikeysteward.repositories.db.DbCommons.ApiKeyTemplateUpdateError.ApiKeyTemplateNotFoundError
import apikeysteward.repositories.db.DbCommons.{ApiKeyTemplateInsertionError, ApiKeyTemplateUpdateError}
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import doobie.SqlState
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION

import java.time.{Clock, Instant}
import java.util.UUID

class ApiKeyTemplateDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(
      apiKeyTemplate: ApiKeyTemplateEntity.Write
  ): doobie.ConnectionIO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplateEntity.Read]] = {
    val now = Instant.now(clock)
    Queries
      .insert(apiKeyTemplate, now)
      .withUniqueGeneratedKeys[ApiKeyTemplateEntity.Read](
        "id",
        "public_id",
        "api_key_expiry_period_max_seconds",
        "created_at",
        "updated_at"
      )
      .attemptSomeSqlState[ApiKeyTemplateInsertionError] {
        case sqlState: SqlState if sqlState == UNIQUE_VIOLATION =>
          ApiKeyTemplateAlreadyExistsError(apiKeyTemplate.publicId)
      }
  }

  def update(
      apiKeyTemplate: ApiKeyTemplateEntity.Write
  ): doobie.ConnectionIO[Either[ApiKeyTemplateUpdateError, ApiKeyTemplateEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      _ <- Queries.update(apiKeyTemplate, now).run

      resOpt <- getBy(apiKeyTemplate.publicId)
      res = resOpt.toRight(ApiKeyTemplateNotFoundError(apiKeyTemplate.publicId))
    } yield res
  }

  def getBy(publicId: UUID): doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    getBy(publicId.toString)

  private def getBy(publicId: String): doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Queries.getByPublicId(publicId).option

  private object Queries {

    def insert(apiKeyTemplateEntityWrite: ApiKeyTemplateEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key_template(
           public_id,
           api_key_expiry_period_max_seconds,
           created_at,
           updated_at
         ) VALUES (
            ${apiKeyTemplateEntityWrite.publicId},
            ${apiKeyTemplateEntityWrite.apiKeyExpiryPeriodMaxSeconds},
            $now,
            $now
         )""".stripMargin.update

    def update(apiKeyTemplateEntityWrite: ApiKeyTemplateEntity.Write, now: Instant): doobie.Update0 =
      sql"""UPDATE api_key_template
           |SET
           |  api_key_expiry_period_max_seconds = ${apiKeyTemplateEntityWrite.apiKeyExpiryPeriodMaxSeconds},
           |  updated_at = $now
           |WHERE
           |  public_id = ${apiKeyTemplateEntityWrite.publicId}
           |""".stripMargin.update

    def getByPublicId(publicId: String): doobie.Query0[ApiKeyTemplateEntity.Read] =
      sql"""SELECT
           |  id,
           |  public_id,
           |  api_key_expiry_period_max_seconds,
           |  created_at,
           |  updated_at
           |FROM api_key_template
           |WHERE public_id = $publicId
           |""".stripMargin.query[ApiKeyTemplateEntity.Read]
  }
}
