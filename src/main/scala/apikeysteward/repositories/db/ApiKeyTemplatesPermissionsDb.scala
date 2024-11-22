package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError._
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
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

class ApiKeyTemplatesPermissionsDb {

  def insertMany(
      entities: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): doobie.ConnectionIO[
    Either[ApiKeyTemplatesPermissionsInsertionError, List[ApiKeyTemplatesPermissionsEntity.Read]]
  ] =
    Queries
      .insertMany(entities)
      .attemptSql
      .map(_.left.map(recoverSqlException))
      .compile
      .toList
      .map(_.sequence)

  private def recoverSqlException(
      sqlException: SQLException
  ): ApiKeyTemplatesPermissionsInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value =>
        val (apiKeyTemplateId, permissionId) = extractBothIds(sqlException)
        ApiKeyTemplatesPermissionsAlreadyExistsError(apiKeyTemplateId, permissionId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_api_key_template_id") =>
        val apiKeyTemplateId = extractApiKeyTemplateId(sqlException)
        ReferencedApiKeyTemplateDoesNotExistError(apiKeyTemplateId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fk_permission_id") =>
        val permissionId = extractPermissionId(sqlException)
        ReferencedPermissionDoesNotExistError(permissionId)

      case _ => ApiKeyTemplatesPermissionsInsertionErrorImpl(sqlException)
    }

  private def extractBothIds(sqlException: SQLException): (Long, Long) =
    ForeignKeyViolationSqlErrorExtractor.extractTwoColumnsLongValues(sqlException)(
      "api_key_template_id",
      "permission_id"
    )

  private def extractApiKeyTemplateId(sqlException: SQLException): Long =
    ForeignKeyViolationSqlErrorExtractor.extractColumnLongValue(sqlException)("api_key_template_id")

  private def extractPermissionId(sqlException: SQLException): Long =
    ForeignKeyViolationSqlErrorExtractor.extractColumnLongValue(sqlException)("permission_id")

  def deleteAllForPermission(publicPermissionId: PermissionId): doobie.ConnectionIO[Int] =
    Queries.deleteAllForPermission(publicPermissionId).run

  def deleteAllForApiKeyTemplate(publicTemplateId: ApiKeyTemplateId): doobie.ConnectionIO[Int] =
    Queries.deleteAllForApiKeyTemplate(publicTemplateId).run

  def deleteMany(
      entities: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesPermissionsNotFoundError, List[ApiKeyTemplatesPermissionsEntity.Read]]] =
    NonEmptyList.fromList(entities) match {
      case Some(values) => performDeleteMany(values)
      case None =>
        List
          .empty[ApiKeyTemplatesPermissionsEntity.Read]
          .asRight[ApiKeyTemplatesPermissionsNotFoundError]
          .pure[doobie.ConnectionIO]
    }

  private def performDeleteMany(
      entities: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write]
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
      entitiesToDelete: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write],
      entitiesFound: List[ApiKeyTemplatesPermissionsEntity.Read]
  ): List[ApiKeyTemplatesPermissionsEntity.Write] = {
    val entitiesFoundWrite = convertEntitiesReadToWrite(entitiesFound)
    entitiesToDelete.iterator.toSet.diff(entitiesFoundWrite.toSet).toList
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
      entitiesWrite: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write]
  ): Stream[doobie.ConnectionIO, ApiKeyTemplatesPermissionsEntity.Read] =
    Queries.getAllThatExistFrom(entitiesWrite).stream

  private object Queries {

    def insertMany(
        entities: List[ApiKeyTemplatesPermissionsEntity.Write]
    ): Stream[doobie.ConnectionIO, ApiKeyTemplatesPermissionsEntity.Read] = {
      val sql = s"INSERT INTO api_key_templates_permissions (api_key_template_id, permission_id) VALUES (?, ?)"

      Update[ApiKeyTemplatesPermissionsEntity.Write](sql)
        .updateManyWithGeneratedKeys[ApiKeyTemplatesPermissionsEntity.Read](
          "api_key_template_id",
          "permission_id"
        )(entities)
    }

    def deleteAllForPermission(publicPermissionId: PermissionId): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_permissions
            USING permission
            WHERE api_key_templates_permissions.permission_id = permission.id
              AND permission.public_permission_id = ${publicPermissionId.toString}
           """.stripMargin.update

    def deleteAllForApiKeyTemplate(publicTemplateId: ApiKeyTemplateId): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_permissions
            USING api_key_template
            WHERE api_key_templates_permissions.api_key_template_id = api_key_template.id
              AND api_key_template.public_template_id = ${publicTemplateId.toString}
           """.stripMargin.update

    def deleteMany(entities: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write]): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_permissions
            WHERE (api_key_template_id, permission_id) IN (${Fragments.values(entities)})
           """.stripMargin.update

    def getAllThatExistFrom(
        entities: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write]
    ): doobie.Query0[ApiKeyTemplatesPermissionsEntity.Read] =
      sql"""SELECT
              api_key_template_id,
              permission_id
            FROM api_key_templates_permissions
            WHERE (api_key_template_id, permission_id) IN (${Fragments.values(entities)})
            """.stripMargin.query[ApiKeyTemplatesPermissionsEntity.Read]

  }
}
