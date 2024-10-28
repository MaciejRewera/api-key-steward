package apikeysteward.repositories.db

import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError._
import apikeysteward.model.RepositoryErrors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity
import cats.implicits.toTraverseOps
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}

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

      case FOREIGN_KEY_VIOLATION.value => ReferencedTenantDoesNotExistError(userEntity.tenantId)

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
      resultE <- userToDelete.traverse(result => Queries.delete(publicTenantId, publicUserId).run.map(_ => result))
    } yield resultE

  def getByPublicUserId(publicTenantId: TenantId, publicUserId: UserId): doobie.ConnectionIO[Option[UserEntity.Read]] =
    Queries.getBy(publicTenantId, publicUserId).option

  def getAllForTenant(publicTenantId: TenantId): Stream[doobie.ConnectionIO, UserEntity.Read] =
    Queries.getAllForTenant(publicTenantId).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"""SELECT
            tenant_user.id,
            tenant_user.tenant_id,
            tenant_user.public_user_id,
            tenant_user.created_at,
            tenant_user.updated_at
          """

    def insert(userEntity: UserEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO tenant_user(tenant_id, public_user_id, created_at, updated_at)
            VALUES (
              ${userEntity.tenantId},
              ${userEntity.publicUserId},
              $now,
              $now
            )
           """.stripMargin.update

    def delete(publicTenantId: TenantId, publicUserId: UserId): doobie.Update0 =
      sql"""DELETE FROM tenant_user
            USING tenant
            WHERE tenant.public_tenant_id = ${publicTenantId.toString}
              AND tenant_user.public_user_id = ${publicUserId.toString}
           """.stripMargin.update

    def getBy(publicTenantId: TenantId, publicUserId: UserId): doobie.Query0[UserEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM tenant_user
              JOIN tenant ON tenant.id = tenant_user.tenant_id
              WHERE tenant.public_tenant_id = ${publicTenantId.toString}
                AND tenant_user.public_user_id = ${publicUserId.toString}
             """).query[UserEntity.Read]

    def getAllForTenant(publicTenantId: TenantId): doobie.Query0[UserEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM tenant_user
              JOIN tenant ON tenant.id = tenant_user.tenant_id
              WHERE tenant.public_tenant_id = ${publicTenantId.toString}
              ORDER BY tenant_user.created_at DESC
             """).query[UserEntity.Read]
  }
}
