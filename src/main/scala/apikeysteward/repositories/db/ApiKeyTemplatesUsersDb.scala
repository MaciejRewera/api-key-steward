package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import doobie.Update
import doobie.implicits.toDoobieApplicativeErrorOps
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}

import java.sql.SQLException

class ApiKeyTemplatesUsersDb {

  def insertMany(
      entities: List[ApiKeyTemplatesUsersEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesUsersInsertionError, Int]] =
    Queries.insertMany(entities).attemptSql.map(_.left.map(recoverSqlException))

  private def recoverSqlException(
      sqlException: SQLException
  ): ApiKeyTemplatesUsersInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value =>
        val (apiKeyTemplateId, userId) = scrapeBothIds(sqlException.getMessage)
        ApiKeyTemplatesUsersAlreadyExistsError(apiKeyTemplateId, userId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_api_key_template_id") =>
        val apiKeyTemplateId = scrapeApiKeyTemplateId(sqlException.getMessage)
        ReferencedApiKeyTemplateDoesNotExistError(apiKeyTemplateId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_user_id") =>
        val userId = scrapeUserId(sqlException.getMessage)
        ReferencedUserDoesNotExistError(userId)

      case _ => ApiKeyTemplatesUsersInsertionErrorImpl(sqlException)
    }

  private def scrapeBothIds(message: String): (Long, Long) = {
    val rawArray = message
      .split("\\(api_key_template_id, user_id\\)=\\(")
      .drop(1)
      .head
      .takeWhile(_ != ')')
      .split(",")
      .map(_.trim)

    val (templateId, userId) =
      (rawArray.head.takeWhile(_.isDigit).toLong, rawArray(1).takeWhile(_.isDigit).toLong)

    (templateId, userId)
  }

  private def scrapeColumnValue(message: String, columnName: String): Long =
    message.split(s"\\($columnName\\)=\\(").apply(1).takeWhile(_.isDigit).toLong

  private def scrapeApiKeyTemplateId(message: String): Long =
    scrapeColumnValue(message, "api_key_template_id")

  private def scrapeUserId(message: String): Long =
    scrapeColumnValue(message, "user_id")

  private object Queries {

    def insertMany(entities: List[ApiKeyTemplatesUsersEntity.Write]): doobie.ConnectionIO[Int] = {
      val sql = s"INSERT INTO api_key_templates_users (api_key_template_id, user_id) VALUES (?, ?)"

      Update[ApiKeyTemplatesUsersEntity.Write](sql).updateMany(entities)
    }
  }
}
