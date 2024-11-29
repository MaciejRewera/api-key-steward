package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import cats.implicits.{catsSyntaxApplicativeId, toTraverseOps}
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}

class ApiKeyTemplateDb()(implicit clock: Clock) extends DoobieCustomMeta {

  def insert(
      templateEntity: ApiKeyTemplateEntity.Write
  ): doobie.ConnectionIO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplateEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(templateEntity, now)
        .withUniqueGeneratedKeys[ApiKeyTemplateEntity.Read](
          "id",
          "tenant_id",
          "public_template_id",
          "name",
          "description",
          "is_default",
          "api_key_max_expiry_period",
          "api_key_prefix",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, templateEntity))

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      templateEntity: ApiKeyTemplateEntity.Write
  ): ApiKeyTemplateInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_template_id") =>
        ApiKeyTemplateAlreadyExistsError(templateEntity.publicTemplateId)

      case FOREIGN_KEY_VIOLATION.value => ReferencedTenantDoesNotExistError.fromDbId(templateEntity.tenantId)

      case _ => ApiKeyTemplateInsertionErrorImpl(sqlException)
    }

  def update(
      templateEntity: ApiKeyTemplateEntity.Update
  ): doobie.ConnectionIO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplateEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      updateCount <- Queries.update(templateEntity, now).run

      resOpt <-
        if (updateCount > 0) getByPublicTemplateId(templateEntity.publicTemplateId)
        else Option.empty[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]
    } yield resOpt.toRight(ApiKeyTemplateNotFoundError(templateEntity.publicTemplateId))
  }

  def delete(
      publicTemplateId: ApiKeyTemplateId
  ): doobie.ConnectionIO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplateEntity.Read]] =
    for {
      templateToDelete <- getByPublicTemplateId(publicTemplateId).map(
        _.toRight(ApiKeyTemplateNotFoundError(publicTemplateId.toString))
      )
      resultE <- templateToDelete.traverse(result => Queries.delete(publicTemplateId).run.map(_ => result))
    } yield resultE

  def getByPublicTemplateId(
      publicTemplateId: ApiKeyTemplateId
  ): doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    getByPublicTemplateId(publicTemplateId.toString)

  private def getByPublicTemplateId(
      publicTemplateId: String
  ): doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Queries.getByPublicTemplateId(publicTemplateId).option

  def getAllForUser(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): Stream[doobie.ConnectionIO, ApiKeyTemplateEntity.Read] =
    Queries.getAllForUser(publicTenantId, publicUserId).stream

  def getAllForTenant(publicTenantId: TenantId): Stream[doobie.ConnectionIO, ApiKeyTemplateEntity.Read] =
    Queries.getAllForTenant(publicTenantId).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"""SELECT
            api_key_template.id,
            api_key_template.tenant_id,
            api_key_template.public_template_id,
            api_key_template.name,
            api_key_template.description,
            api_key_template.is_default,
            api_key_template.api_key_max_expiry_period,
            api_key_template.api_key_prefix,
            api_key_template.created_at,
            api_key_template.updated_at
          """

    def insert(templateEntity: ApiKeyTemplateEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key_template(id, tenant_id, public_template_id, name, description, is_default, api_key_max_expiry_period, api_key_prefix, created_at, updated_at)
            VALUES(
              ${templateEntity.id},
              ${templateEntity.tenantId},
              ${templateEntity.publicTemplateId},
              ${templateEntity.name},
              ${templateEntity.description},
              ${templateEntity.isDefault},
              ${templateEntity.apiKeyMaxExpiryPeriod.toString},
              ${templateEntity.apiKeyPrefix},
              $now,
              $now
            )
           """.stripMargin.update

    def update(templateEntity: ApiKeyTemplateEntity.Update, now: Instant): doobie.Update0 =
      sql"""UPDATE api_key_template
            SET is_default = ${templateEntity.isDefault},
                name = ${templateEntity.name},
                description = ${templateEntity.description},
                api_key_max_expiry_period = ${templateEntity.apiKeyMaxExpiryPeriod.toString},
                updated_at = $now
            WHERE api_key_template.public_template_id = ${templateEntity.publicTemplateId}
           """.stripMargin.update

    def delete(publicTemplateId: ApiKeyTemplateId): doobie.Update0 =
      sql"DELETE FROM api_key_template WHERE api_key_template.public_template_id = ${publicTemplateId.toString}".update

    def getByPublicTemplateId(publicTemplateId: String): doobie.Query0[ApiKeyTemplateEntity.Read] =
      (
        columnNamesSelectFragment ++
          sql"""FROM api_key_template
                WHERE api_key_template.public_template_id = $publicTemplateId
                """.stripMargin
      ).query[ApiKeyTemplateEntity.Read]

    def getAllForUser(publicTenantId: TenantId, publicUserId: UserId): doobie.Query0[ApiKeyTemplateEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM api_key_template
              JOIN api_key_templates_users ON api_key_template.id = api_key_templates_users.api_key_template_id
              JOIN tenant_user ON tenant_user.id = api_key_templates_users.user_id
              JOIN tenant ON tenant.id = tenant_user.tenant_id
              WHERE tenant.public_tenant_id = ${publicTenantId.toString}
                AND tenant_user.public_user_id = ${publicUserId.toString}
             """.stripMargin).query[ApiKeyTemplateEntity.Read]

    def getAllForTenant(publicTenantId: TenantId): doobie.Query0[ApiKeyTemplateEntity.Read] =
      sql"""SELECT
              template.id,
              template.tenant_id,
              template.public_template_id,
              template.name,
              template.description,
              template.is_default,
              template.api_key_max_expiry_period,
              template.api_key_prefix,
              template.created_at,
              template.updated_at
            FROM api_key_template AS template
            JOIN tenant ON tenant.id = template.tenant_id
            WHERE tenant.public_tenant_id = ${publicTenantId.toString}
            ORDER BY template.created_at DESC
           """.stripMargin.query[ApiKeyTemplateEntity.Read]
  }

}
