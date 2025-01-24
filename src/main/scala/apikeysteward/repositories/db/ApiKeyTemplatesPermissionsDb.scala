package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError._
import apikeysteward.model.Tenant.TenantId
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
import java.util.UUID

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

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_tenant_id") =>
        val tenantId = extractTenantId(sqlException)
        ReferencedTenantDoesNotExistError.fromDbId(tenantId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_api_key_template_id") =>
        val apiKeyTemplateId = extractApiKeyTemplateId(sqlException)
        ReferencedApiKeyTemplateDoesNotExistError.fromDbId(apiKeyTemplateId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_permission_id") =>
        val permissionId = extractPermissionId(sqlException)
        ReferencedPermissionDoesNotExistError.fromDbId(permissionId)

      case _ => ApiKeyTemplatesPermissionsInsertionErrorImpl(sqlException)
    }

  private def extractBothIds(sqlException: SQLException): (UUID, UUID) =
    ForeignKeyViolationSqlErrorExtractor.extractTwoColumnsUuidValues(sqlException)(
      "api_key_template_id",
      "permission_id"
    )

  private def extractTenantId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("tenant_id")

  private def extractApiKeyTemplateId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("api_key_template_id")

  private def extractPermissionId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("permission_id")

  def deleteAllForPermission(publicTenantId: TenantId, publicPermissionId: PermissionId): doobie.ConnectionIO[Int] =
    TenantIdScopedQueries(publicTenantId).deleteAllForPermission(publicPermissionId).run

  def deleteAllForApiKeyTemplate(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): doobie.ConnectionIO[Int] =
    TenantIdScopedQueries(publicTenantId).deleteAllForApiKeyTemplate(publicTemplateId).run

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
        tenantId = entityRead.tenantId,
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
      val sql =
        s"INSERT INTO api_key_templates_permissions (tenant_id, api_key_template_id, permission_id) VALUES (?, ?, ?)"

      Update[ApiKeyTemplatesPermissionsEntity.Write](sql)
        .updateManyWithGeneratedKeys[ApiKeyTemplatesPermissionsEntity.Read](
          "tenant_id",
          "api_key_template_id",
          "permission_id"
        )(entities)
    }

    def deleteMany(entities: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write]): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_permissions
            WHERE (tenant_id, api_key_template_id, permission_id) IN (${Fragments.values(entities)})
           """.stripMargin.update

    def getAllThatExistFrom(
        entities: NonEmptyList[ApiKeyTemplatesPermissionsEntity.Write]
    ): doobie.Query0[ApiKeyTemplatesPermissionsEntity.Read] =
      sql"""SELECT
              tenant_id,
              api_key_template_id,
              permission_id
            FROM api_key_templates_permissions
            WHERE (tenant_id, api_key_template_id, permission_id) IN (${Fragments.values(entities)})
            """.stripMargin.query[ApiKeyTemplatesPermissionsEntity.Read]

  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "api_key_templates_permissions"

    def deleteAllForPermission(publicPermissionId: PermissionId): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_permissions
            USING permission
            WHERE api_key_templates_permissions.permission_id = permission.id
              AND permission.public_permission_id = ${publicPermissionId.toString}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    def deleteAllForApiKeyTemplate(publicTemplateId: ApiKeyTemplateId): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_permissions
            USING api_key_template
            WHERE api_key_templates_permissions.api_key_template_id = api_key_template.id
              AND api_key_template.public_template_id = ${publicTemplateId.toString}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

  }
}
