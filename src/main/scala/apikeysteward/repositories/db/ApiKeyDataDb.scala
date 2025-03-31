package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.ApiKeyDbError._
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import doobie.util.fragment
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class ApiKeyDataDb()(implicit clock: Clock) {

  def insert(
      apiKeyDataEntity: ApiKeyDataEntity.Write
  ): doobie.ConnectionIO[Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(apiKeyDataEntity, now)
        .withUniqueGeneratedKeys[ApiKeyDataEntity.Read](
          "id",
          "tenant_id",
          "api_key_id",
          "user_id",
          "template_id",
          "public_key_id",
          "name",
          "description",
          "expires_at",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, apiKeyDataEntity))

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      apiKeyDataEntity: ApiKeyDataEntity.Write
  ): ApiKeyInsertionError =
    sqlException.getSQLState match {
      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_api_key_id") =>
        ReferencedApiKeyDoesNotExistError(apiKeyDataEntity.apiKeyId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_tenant_id") =>
        ReferencedTenantDoesNotExistError.fromDbId(apiKeyDataEntity.tenantId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_user_id") =>
        ReferencedUserDoesNotExistError.fromDbId(apiKeyDataEntity.userId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_template_id") =>
        ReferencedApiKeyTemplateDoesNotExistError.fromDbId(apiKeyDataEntity.templateId)

      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("api_key_id") =>
        ApiKeyIdAlreadyExistsError

      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_key_id") =>
        PublicKeyIdAlreadyExistsError

      case _ => ApiKeyInsertionErrorImpl(sqlException)
    }

  def update(
      publicTenantId: TenantId,
      apiKeyDataEntity: ApiKeyDataEntity.Update
  ): doobie.ConnectionIO[Either[ApiKeyDbError, ApiKeyDataEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      updateCount <- TenantIdScopedQueries(publicTenantId).update(apiKeyDataEntity, now).run

      resOpt <-
        if (updateCount > 0) getByPublicKeyId(publicTenantId, apiKeyDataEntity.publicKeyId)
        else Option.empty[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(
      ApiKeyDataNotFoundError(apiKeyDataEntity.publicKeyId)
    )
  }

  def getByApiKeyId(publicTenantId: TenantId, apiKeyDbId: UUID): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getByApiKeyId(apiKeyDbId).option

  def getByUserId(publicTenantId: TenantId, publicUserId: UserId): Stream[doobie.ConnectionIO, ApiKeyDataEntity.Read] =
    TenantIdScopedQueries(publicTenantId).getByPublicUserId(publicUserId).stream

  def getByPublicKeyId(
      publicTenantId: TenantId,
      publicKeyId: ApiKeyId
  ): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    getByPublicKeyId(publicTenantId, publicKeyId.toString)

  private def getByPublicKeyId(
      publicTenantId: TenantId,
      publicKeyId: String
  ): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getByPublicKeyId(publicKeyId).option

  def getBy(
      publicTenantId: TenantId,
      publicUserId: UserId,
      publicKeyId: ApiKeyId
  ): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    getBy(publicTenantId, publicUserId, publicKeyId.toString)

  private def getBy(
      publicTenantId: TenantId,
      publicUserId: String,
      publicKeyId: String
  ): doobie.ConnectionIO[Option[ApiKeyDataEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getBy(publicUserId, publicKeyId).option

  def delete(
      publicTenantId: TenantId,
      publicKeyId: ApiKeyId
  ): doobie.ConnectionIO[Either[ApiKeyDbError, ApiKeyDataEntity.Read]] =
    for {
      apiKeyToDeleteE <- getByPublicKeyId(publicTenantId, publicKeyId).map(
        _.toRight(ApiKeyDataNotFoundError(publicKeyId))
      )
      resultE <- apiKeyToDeleteE.traverse(result =>
        TenantIdScopedQueries(publicTenantId).delete(publicKeyId.toString).run.map(_ => result)
      )
    } yield resultE

  private object Queries {

    private val columnNamesInsertFragment =
      fr"INSERT INTO api_key_data(id, tenant_id, api_key_id, user_id, template_id, public_key_id, name, description, expires_at, created_at, updated_at)"

    def insert(apiKeyDataEntityWrite: ApiKeyDataEntity.Write, now: Instant): doobie.Update0 =
      (columnNamesInsertFragment ++
        sql"""VALUES (
                ${apiKeyDataEntityWrite.id},
                ${apiKeyDataEntityWrite.tenantId},
                ${apiKeyDataEntityWrite.apiKeyId},
                ${apiKeyDataEntityWrite.userId},
                ${apiKeyDataEntityWrite.templateId},
                ${apiKeyDataEntityWrite.publicKeyId},
                ${apiKeyDataEntityWrite.name},
                ${apiKeyDataEntityWrite.description},
                ${apiKeyDataEntityWrite.expiresAt},
                $now,
                $now
             )""".stripMargin).update

  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "api_key_data"

    private val columnNamesSelectFragment =
      fr"SELECT id, tenant_id, api_key_id, user_id, template_id, public_key_id, name, description, expires_at, created_at, updated_at"

    def update(apiKeyDataEntityUpdate: ApiKeyDataEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE api_key_data
            SET name = ${apiKeyDataEntityUpdate.name},
                description = ${apiKeyDataEntityUpdate.description},
                updated_at = $now
            WHERE api_key_data.public_key_id = ${apiKeyDataEntityUpdate.publicKeyId}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    def getByApiKeyId(apiKeyDbId: UUID): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key_data
              WHERE api_key_id = $apiKeyDbId
                AND ${tenantIdFr(TableName)}
             """).query[ApiKeyDataEntity.Read]

    def getByPublicUserId(publicUserId: UserId): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key_data
              WHERE ${userIdFr(publicUserId)}
                AND ${tenantIdFr(TableName)}
             """).query[ApiKeyDataEntity.Read]

    def getByPublicKeyId(publicKeyId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key_data
              WHERE public_key_id = $publicKeyId
                AND ${tenantIdFr(TableName)}
             """).query[ApiKeyDataEntity.Read]

    def getBy(publicUserId: UserId, publicKeyId: String): doobie.Query0[ApiKeyDataEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key_data
              WHERE public_key_id = $publicKeyId
                AND ${userIdFr(publicUserId)}
                AND ${tenantIdFr(TableName)}
             """).query[ApiKeyDataEntity.Read]

    def delete(publicKeyId: String): doobie.Update0 =
      sql"""DELETE FROM api_key_data
            WHERE api_key_data.public_key_id = $publicKeyId
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    private def userIdFr(publicUserId: UserId): fragment.Fragment =
      fr""" user_id = (
          |   SELECT tenant_user.id
          |   FROM tenant_user
          |   WHERE tenant_user.public_user_id = $publicUserId
          | )
          |""".stripMargin

  }

}
