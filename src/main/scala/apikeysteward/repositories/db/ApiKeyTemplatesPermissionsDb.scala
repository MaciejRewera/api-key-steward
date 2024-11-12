package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError._
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesPermissionsEntity, PermissionEntity}
import cats.data.NonEmptyList
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId, toTraverseOps}
import doobie.Fragments
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import doobie.util.update.Update
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}

class ApiKeyTemplatesPermissionsDb()(implicit clock: Clock) {

  def insertMany(
      entities: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): doobie.ConnectionIO[
    Either[ApiKeyTemplatesPermissionsInsertionError, List[ApiKeyTemplatesPermissionsEntity.Read]]
  ] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insertMany(entities, now)
        .attemptSql
        .map(_.left.map(recoverSqlException))

      res <- eitherResult.traverse(_ => getAllThatExistFrom(entities).compile.toList)
    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException
  ): ApiKeyTemplatesPermissionsInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value =>
        val (apiKeyTemplateId, permissionId) = scrapeBothIds(sqlException.getMessage)
        ApiKeyTemplatesPermissionsAlreadyExistsError(apiKeyTemplateId, permissionId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_api_key_template_id") =>
        val apiKeyTemplateId = scrapeApiKeyTemplateId(sqlException.getMessage)
        ReferencedApiKeyTemplateDoesNotExistError(apiKeyTemplateId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_permission_id") =>
        val permissionId = scrapePermissionId(sqlException.getMessage)
        ReferencedPermissionDoesNotExistError(permissionId)

      case _ => ApiKeyTemplatesPermissionsInsertionErrorImpl(sqlException)
    }

  private def scrapeBothIds(message: String): (Long, Long) = {
    val rawArray = message
      .split("\\(api_key_template_id, permission_id\\)=\\(")
      .drop(1)
      .head
      .takeWhile(_ != ')')
      .split(",")
      .map(_.trim)

    val (templateId, permissionId) =
      (rawArray.head.takeWhile(_.isDigit).toLong, rawArray(1).takeWhile(_.isDigit).toLong)

    (templateId, permissionId)
  }

  private def scrapeApiKeyTemplateId(message: String): Long =
    message.split("\\(api_key_template_id\\)=\\(").apply(1).takeWhile(_.isDigit).toLong

  private def scrapePermissionId(message: String): Long =
    message.split("\\(permission_id\\)=\\(").apply(1).takeWhile(_.isDigit).toLong

  def deleteMany(
      entities: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesPermissionsNotFoundError, List[ApiKeyTemplatesPermissionsEntity.Read]]] =
    for {
      entitiesFound <- getAllThatExistFrom(entities).compile.toList
      missingEntities = filterMissingEntities(entities, entitiesFound)

      resultE <-
        if (missingEntities.isEmpty)
          Queries.deleteMany(entities).run.map(_ => entitiesFound.asRight[ApiKeyTemplatesPermissionsNotFoundError])
        else
          ApiKeyTemplatesPermissionsNotFoundError(missingEntities)
            .asLeft[List[ApiKeyTemplatesPermissionsEntity.Read]]
            .pure[doobie.ConnectionIO]
    } yield resultE

  private def filterMissingEntities(
      entitiesToDelete: List[ApiKeyTemplatesPermissionsEntity.Write],
      entitiesFound: List[ApiKeyTemplatesPermissionsEntity.Read]
  ): List[ApiKeyTemplatesPermissionsEntity.Write] = {
    val entitiesFoundWrite = convertEntitiesReadToWrite(entitiesFound)
    entitiesToDelete.toSet.diff(entitiesFoundWrite.toSet).toList
  }

  private def convertEntitiesReadToWrite(
      entitiesRead: List[ApiKeyTemplatesPermissionsEntity.Read]
  ): List[ApiKeyTemplatesPermissionsEntity.Write] =
    entitiesRead.map { entityRead =>
      ApiKeyTemplatesPermissionsEntity.Write(
        apiKeyTemplateId = entityRead.apiKeyTemplateId,
        permissionId = entityRead.permissionId
      )
    }

  private[db] def getAllThatExistFrom(
      entitiesWrite: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): Stream[doobie.ConnectionIO, ApiKeyTemplatesPermissionsEntity.Read] =
    Queries.getAllThatExistFrom(entitiesWrite).stream

  private object Queries {

    def insertMany(entities: List[ApiKeyTemplatesPermissionsEntity.Write], now: Instant): doobie.ConnectionIO[Int] = {
      val sql =
        s"""INSERT INTO api_key_templates_permissions (api_key_template_id, permission_id, created_at, updated_at)
            VALUES (?, ?, '$now', '$now')""".stripMargin

      Update[ApiKeyTemplatesPermissionsEntity.Write](sql).updateMany(entities)
    }

    def deleteMany(entities: List[ApiKeyTemplatesPermissionsEntity.Write]): doobie.Update0 = {
      val values =
        NonEmptyList.fromListUnsafe(entities.map(entity => (entity.apiKeyTemplateId, entity.permissionId)))

      sql"""DELETE FROM api_key_templates_permissions
            WHERE (api_key_template_id, permission_id) IN (${Fragments.values(values)})
           """.stripMargin.update
    }

    def getAllThatExistFrom(
        entities: List[ApiKeyTemplatesPermissionsEntity.Write]
    ): doobie.Query0[ApiKeyTemplatesPermissionsEntity.Read] = {
      val values =
        NonEmptyList.fromListUnsafe(entities.map(entity => (entity.apiKeyTemplateId, entity.permissionId)))

      sql"""SELECT
              api_key_template_id,
              permission_id,
              created_at,
              updated_at
            FROM api_key_templates_permissions
            WHERE (api_key_template_id, permission_id) IN (${Fragments.values(values)})
            """.stripMargin.query[ApiKeyTemplatesPermissionsEntity.Read]
    }

  }
}
