package apikeysteward.repositories.db

import apikeysteward.model.HashedApiKey
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.ApiKeyDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ApiKeyEntity
import cats.implicits.toTraverseOps
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class ApiKeyDb()(implicit clock: Clock) {

  def insert(apiKeyEntity: ApiKeyEntity.Write): doobie.ConnectionIO[Either[ApiKeyInsertionError, ApiKeyEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(apiKeyEntity, now)
        .withUniqueGeneratedKeys[ApiKeyEntity.Read]("id", "tenant_id", "created_at", "updated_at")
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, apiKeyEntity.tenantId))

    } yield res
  }

  private def recoverSqlException(sqlException: SQLException, tenantDbId: UUID): ApiKeyInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("api_key") => ApiKeyAlreadyExistsError

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_tenant_id") =>
        ReferencedTenantDoesNotExistError.fromDbId(tenantDbId)

      case _ => ApiKeyInsertionErrorImpl(sqlException)
    }

  def getByApiKey(
      publicTenantId: TenantId,
      hashedApiKey: HashedApiKey
  ): doobie.ConnectionIO[Option[ApiKeyEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getByApiKey(hashedApiKey.value).option

  def delete(
      publicTenantId: TenantId,
      apiKeyDbId: UUID
  ): doobie.ConnectionIO[Either[ApiKeyNotFoundError.type, ApiKeyEntity.Read]] =
    for {
      apiKeyToDeleteE <- TenantIdScopedQueries(publicTenantId)
        .getBy(apiKeyDbId)
        .option
        .map(_.toRight(ApiKeyNotFoundError))

      resultE <- apiKeyToDeleteE.traverse(result =>
        TenantIdScopedQueries(publicTenantId).delete(apiKeyDbId).run.map(_ => result)
      )
    } yield resultE

  private object Queries {

    def insert(apiKeyEntityWrite: ApiKeyEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key(id, tenant_id, api_key, created_at, updated_at)
            VALUES (
              ${apiKeyEntityWrite.id},
              ${apiKeyEntityWrite.tenantId},
              ${apiKeyEntityWrite.apiKey},
              $now,
              $now
            )""".stripMargin.update
  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "api_key"

    private val columnNamesSelectFragment = fr"SELECT id, tenant_id, created_at, updated_at"

    def getByApiKey(apiKey: String): doobie.Query0[ApiKeyEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key
              WHERE api_key = $apiKey
                AND ${tenantIdFr(TableName)}
             """).query[ApiKeyEntity.Read]

    def getBy(apiKeyDbId: UUID): doobie.Query0[ApiKeyEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key
              WHERE id = $apiKeyDbId
                AND ${tenantIdFr(TableName)}
             """).query[ApiKeyEntity.Read]

    def delete(apiKeyDbId: UUID): doobie.Update0 =
      sql"""DELETE FROM api_key
            WHERE api_key.id = $apiKeyDbId
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update
  }
}
