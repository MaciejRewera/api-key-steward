package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.ApiKeysPermissionsDbError
import apikeysteward.model.errors.ApiKeysPermissionsDbError._
import apikeysteward.model.errors.ApiKeysPermissionsDbError.ApiKeysPermissionsInsertionError._
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesPermissionsEntity, ApiKeysPermissionsEntity}
import cats.implicits.toTraverseOps
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.util.update.Update
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2.Stream

import java.sql.SQLException
import java.util.UUID

class ApiKeysPermissionsDb {

  def insertMany(
      entities: List[ApiKeysPermissionsEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeysPermissionsDbError, List[ApiKeysPermissionsEntity.Read]]] =
    Queries
      .insertMany(entities)
      .attemptSql
      .map(_.left.map(recoverSqlException))
      .compile
      .toList
      .map(_.sequence)

  private def recoverSqlException(
      sqlException: SQLException
  ): ApiKeysPermissionsInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value =>
        val (apiKeyId, permissionId) = extractBothIds(sqlException)
        ApiKeysPermissionsAlreadyExistsError(apiKeyId, permissionId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_tenant_id") =>
        val tenantId = extractTenantId(sqlException)
        ReferencedTenantDoesNotExistError.fromDbId(tenantId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_api_key_data_id") =>
        val apiKeyId = extractApiKeyId(sqlException)
        ReferencedApiKeyDoesNotExistError.fromDbId(apiKeyId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_permission_id") =>
        val permissionId = extractPermissionId(sqlException)
        ReferencedPermissionDoesNotExistError.fromDbId(permissionId)

      case _ => ApiKeysPermissionsInsertionErrorImpl(sqlException)
    }

  private def extractBothIds(sqlException: SQLException): (UUID, UUID) =
    ForeignKeyViolationSqlErrorExtractor.extractTwoColumnsUuidValues(sqlException)(
      "api_key_data_id",
      "permission_id"
    )

  private def extractTenantId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("tenant_id")

  private def extractApiKeyId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("api_key_data_id")

  private def extractPermissionId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("permission_id")

  def deleteAllForPermission(publicTenantId: TenantId, permissionId: PermissionId): doobie.ConnectionIO[Int] =
    TenantIdScopedQueries(publicTenantId).deleteAllForPermission(permissionId).run

  def deleteAllForApiKey(publicTenantId: TenantId, apiKeyId: ApiKeyId): doobie.ConnectionIO[Int] =
    TenantIdScopedQueries(publicTenantId).deleteAllForApiKey(apiKeyId).run

  private object Queries {

    def insertMany(
        entities: List[ApiKeysPermissionsEntity.Write]
    ): Stream[doobie.ConnectionIO, ApiKeysPermissionsEntity.Read] = {
      val sql = s"INSERT INTO api_keys_permissions (tenant_id, api_key_data_id, permission_id) VALUES (?, ?, ?)"

      Update[ApiKeysPermissionsEntity.Write](sql)
        .updateManyWithGeneratedKeys[ApiKeysPermissionsEntity.Read](
          "tenant_id",
          "api_key_data_id",
          "permission_id"
        )(entities)
    }

  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "api_keys_permissions"

    def deleteAllForPermission(permissionId: PermissionId): doobie.Update0 =
      sql"""DELETE FROM api_keys_permissions
           |USING permission
           |WHERE api_keys_permissions.permission_id = permission.id
           |  AND permission.public_permission_id = ${permissionId.toString}
           |  AND ${tenantIdFr(TableName)}
           |""".stripMargin.update

    def deleteAllForApiKey(apiKeyId: ApiKeyId): doobie.Update0 =
      sql"""DELETE FROM api_keys_permissions
           |USING api_key_data
           |WHERE api_keys_permissions.api_key_data_id = api_key_data.id
           |  AND api_key_data.public_key_id = ${apiKeyId.toString}
           |  AND ${tenantIdFr(TableName)}
           |""".stripMargin.update

  }
}
