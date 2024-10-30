package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.{
  ApiKeyTemplateInsertionError,
  ApiKeyTemplateNotFoundError
}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import cats.implicits.toTraverseOps
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
          "is_default",
          "name",
          "description",
          "api_key_max_expiry_period",
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

      case FOREIGN_KEY_VIOLATION.value => ReferencedTenantDoesNotExistError(templateEntity.tenantId)

      case _ => ApiKeyTemplateInsertionErrorImpl(sqlException)
    }

  def delete(
      publicTemplateId: ApiKeyTemplateId
  ): doobie.ConnectionIO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplateEntity.Read]] =
    for {
      templateToDelete <- getByPublicTemplateId(publicTemplateId).map(
        _.toRight(ApiKeyTemplateNotFoundError(publicTemplateId))
      )
      resultE <- templateToDelete.traverse(result => Queries.delete(publicTemplateId).run.map(_ => result))
    } yield resultE

  def getByPublicTemplateId(
      publicTemplateId: ApiKeyTemplateId
  ): doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Queries.getByPublicTemplateId(publicTemplateId).option

  def getAllForTenant(tenantId: TenantId): Stream[doobie.ConnectionIO, ApiKeyTemplateEntity.Read] =
    Queries.getAllForTenant(tenantId).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, tenant_id, public_template_id, is_default, name, description, api_key_max_expiry_period, created_at, updated_at"

    def insert(templateEntity: ApiKeyTemplateEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key_template(tenant_id, public_template_id, is_default, name, description, api_key_max_expiry_period, created_at, updated_at)
            VALUES(
              ${templateEntity.tenantId},
              ${templateEntity.publicTemplateId},
              ${templateEntity.isDefault},
              ${templateEntity.name},
              ${templateEntity.description},
              ${templateEntity.apiKeyMaxExpiryPeriod},
              $now,
              $now
            )
           """.stripMargin.update

    def delete(publicTemplateId: ApiKeyTemplateId): doobie.Update0 =
      sql"DELETE FROM api_key_template WHERE api_key_template.public_template_id = ${publicTemplateId.toString}".update

    def getByPublicTemplateId(publicTemplateId: ApiKeyTemplateId): doobie.Query0[ApiKeyTemplateEntity.Read] =
      (
        columnNamesSelectFragment ++
          sql"""FROM api_key_template
                WHERE api_key_template.public_template_id = ${publicTemplateId.toString}
                """.stripMargin
      ).query[ApiKeyTemplateEntity.Read]

    def getAllForTenant(publicTenantId: TenantId): doobie.Query0[ApiKeyTemplateEntity.Read] =
      sql"""SELECT
              template.id,
              template.tenant_id,
              template.public_template_id,
              template.is_default,
              template.name,
              template.description,
              template.api_key_max_expiry_period,
              template.created_at,
              template.updated_at
            FROM api_key_template AS template
            JOIN tenant ON tenant.id = template.tenant_id
            WHERE tenant.public_tenant_id = ${publicTenantId.toString}
            ORDER BY template.created_at DESC
           """.stripMargin.query[ApiKeyTemplateEntity.Read]
  }

}
