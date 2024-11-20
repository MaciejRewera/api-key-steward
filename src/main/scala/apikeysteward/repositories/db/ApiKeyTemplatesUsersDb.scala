package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import cats.implicits.toTraverseOps
import doobie.Update
import doobie.implicits.toDoobieApplicativeErrorOps
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2.Stream

import java.sql.SQLException

class ApiKeyTemplatesUsersDb {

  def insertMany(
      entities: List[ApiKeyTemplatesUsersEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesUsersInsertionError, List[ApiKeyTemplatesUsersEntity.Read]]] =
    Queries
      .insertMany(entities)
      .attemptSql
      .map(_.left.map(recoverSqlException))
      .compile
      .toList
      .map(_.sequence)

  private def recoverSqlException(
      sqlException: SQLException
  ): ApiKeyTemplatesUsersInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value =>
        val (apiKeyTemplateId, userId) = extractBothIds(sqlException)
        ApiKeyTemplatesUsersAlreadyExistsError(apiKeyTemplateId, userId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_api_key_template_id") =>
        val apiKeyTemplateId = extractApiKeyTemplateId(sqlException)
        ReferencedApiKeyTemplateDoesNotExistError(apiKeyTemplateId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_user_id") =>
        val userId = extractUserId(sqlException)
        ReferencedUserDoesNotExistError(userId)

      case _ => ApiKeyTemplatesUsersInsertionErrorImpl(sqlException)
    }

  private def extractBothIds(sqlException: SQLException): (Long, Long) =
    ForeignKeyViolationSqlErrorExtractor.extractTwoColumnsLongValues(sqlException)("api_key_template_id", "user_id")

  private def extractApiKeyTemplateId(sqlException: SQLException): Long =
    ForeignKeyViolationSqlErrorExtractor.extractColumnLongValue(sqlException)("api_key_template_id")

  private def extractUserId(sqlException: SQLException): Long =
    ForeignKeyViolationSqlErrorExtractor.extractColumnLongValue(sqlException)("user_id")

  private object Queries {

    def insertMany(
        entities: List[ApiKeyTemplatesUsersEntity.Write]
    ): Stream[doobie.ConnectionIO, ApiKeyTemplatesUsersEntity.Read] = {
      val sql = s"INSERT INTO api_key_templates_users (api_key_template_id, user_id) VALUES (?, ?)"

      Update[ApiKeyTemplatesUsersEntity.Write](sql)
        .updateManyWithGeneratedKeys[ApiKeyTemplatesUsersEntity.Read](
          "api_key_template_id",
          "user_id"
        )(entities)
    }
  }
}
