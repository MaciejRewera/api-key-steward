package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.UserDbError.UserInsertionError._
import apikeysteward.model.errors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.repositories.db.entity.UserEntity
import cats.implicits.toTraverseOps
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}
import java.util.UUID

class UserDb()(implicit clock: Clock) {

  def insert(userEntity: UserEntity.Write): doobie.ConnectionIO[Either[UserInsertionError, UserEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(userEntity, now)
        .withUniqueGeneratedKeys[UserEntity.Read](
          "id",
          "tenant_id",
          "public_user_id",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, userEntity))

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      userEntity: UserEntity.Write
  ): UserInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("tenant_id, public_user_id") =>
        UserAlreadyExistsForThisTenantError(userEntity.publicUserId, userEntity.tenantId)

      case FOREIGN_KEY_VIOLATION.value => ReferencedTenantDoesNotExistError.fromDbId(userEntity.tenantId)

      case _ => UserInsertionErrorImpl(sqlException)
    }

  def delete(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): doobie.ConnectionIO[Either[UserNotFoundError, UserEntity.Read]] =
    for {
      userToDelete <- getByPublicUserId(publicTenantId, publicUserId).map(
        _.toRight(UserNotFoundError(publicTenantId, publicUserId))
      )
      resultE <- userToDelete.traverse(result =>
        TenantIdScopedQueries(publicTenantId).delete(publicUserId).run.map(_ => result)
      )
    } yield resultE

  def getByPublicUserId(publicTenantId: TenantId, publicUserId: UserId): doobie.ConnectionIO[Option[UserEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getBy(publicUserId).option

  def getAllForTemplate(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): Stream[doobie.ConnectionIO, UserEntity.Read] =
    TenantIdScopedQueries(publicTenantId).getAllForTemplate(publicTemplateId).stream

  def getAllForTenant(publicTenantId: TenantId): Stream[doobie.ConnectionIO, UserEntity.Read] =
    TenantIdScopedQueries(publicTenantId).getAllForTenant.stream

  def getByDbId(publicTenantId: TenantId, userDbId: UUID): doobie.ConnectionIO[Option[UserEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getByDbId(userDbId).option

  private object Queries {

    def insert(userEntity: UserEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO tenant_user(id, tenant_id, public_user_id, created_at, updated_at)
            VALUES (
              ${userEntity.id},
              ${userEntity.tenantId},
              ${userEntity.publicUserId},
              $now,
              $now
            )
           """.stripMargin.update
  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "tenant_user"

    private val columnNamesSelectFragment =
      fr"""SELECT
            tenant_user.id,
            tenant_user.tenant_id,
            tenant_user.public_user_id,
            tenant_user.created_at,
            tenant_user.updated_at
          """

    def delete(publicUserId: UserId): doobie.Update0 =
      sql"""DELETE FROM tenant_user
            USING tenant
            WHERE tenant_user.public_user_id = ${publicUserId.toString}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    def getBy(publicUserId: UserId): doobie.Query0[UserEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM tenant_user
              WHERE tenant_user.public_user_id = ${publicUserId.toString}
                AND ${tenantIdFr(TableName)}
             """).query[UserEntity.Read]

    def getAllForTemplate(publicTemplateId: ApiKeyTemplateId): doobie.Query0[UserEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM tenant_user
            JOIN api_key_templates_users ON tenant_user.id = api_key_templates_users.user_id
            JOIN api_key_template ON api_key_template.id = api_key_templates_users.api_key_template_id
            WHERE api_key_template.public_template_id = ${publicTemplateId.toString}
              AND ${tenantIdFr(TableName)}
           """.stripMargin).query[UserEntity.Read]

    def getAllForTenant: doobie.Query0[UserEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM tenant_user
              WHERE ${tenantIdFr(TableName)}
              ORDER BY tenant_user.created_at DESC
             """).query[UserEntity.Read]

    def getByDbId(userDbId: UUID): doobie.Query0[UserEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM tenant_user
             |WHERE tenant_user.id = $userDbId
             |  AND ${tenantIdFr(TableName)}
             |""".stripMargin).query[UserEntity.Read]

  }
}
