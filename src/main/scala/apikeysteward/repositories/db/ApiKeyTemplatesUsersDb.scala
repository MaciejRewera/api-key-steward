package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import cats.data.NonEmptyList
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId, toTraverseOps}
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import doobie.{Fragments, Update}
import fs2.Stream

import java.sql.SQLException
import java.util.UUID

class ApiKeyTemplatesUsersDb {

  def insertMany(
      entities: List[ApiKeyTemplatesUsersEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesUsersInsertionError, List[ApiKeyTemplatesUsersEntity.Read]]] =
    Queries
      .insertMany(entities)
      .attemptSql
      .map(_.left.map(recoverSqlException))
      .compile
      .toList
      .map(_.sequence)

  private def recoverSqlException(
      sqlException: SQLException
  ): ApiKeyTemplatesUsersInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value =>
        val (apiKeyTemplateId, userId) = extractBothIds(sqlException)
        ApiKeyTemplatesUsersAlreadyExistsError(apiKeyTemplateId, userId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_api_key_template_id") =>
        val apiKeyTemplateId = extractApiKeyTemplateId(sqlException)
        ReferencedApiKeyTemplateDoesNotExistError.fromDbId(apiKeyTemplateId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_user_id") =>
        val userId = extractUserId(sqlException)
        ReferencedUserDoesNotExistError.fromDbId(userId)

      case _ => ApiKeyTemplatesUsersInsertionErrorImpl(sqlException)
    }

  private def extractBothIds(sqlException: SQLException): (UUID, UUID) =
    ForeignKeyViolationSqlErrorExtractor.extractTwoColumnsUuidValues(sqlException)("api_key_template_id", "user_id")

  private def extractApiKeyTemplateId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("api_key_template_id")

  private def extractUserId(sqlException: SQLException): UUID =
    ForeignKeyViolationSqlErrorExtractor.extractColumnUuidValue(sqlException)("user_id")

  def deleteAllForUser(publicTenantId: TenantId, publicUserId: UserId): doobie.ConnectionIO[Int] =
    Queries.deleteAllForUser(publicTenantId, publicUserId).run

  def deleteAllForApiKeyTemplate(publicTemplateId: ApiKeyTemplateId): doobie.ConnectionIO[Int] =
    Queries.deleteAllForApiKeyTemplate(publicTemplateId).run

  def deleteMany(
      entities: List[ApiKeyTemplatesUsersEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesUsersNotFoundError, List[ApiKeyTemplatesUsersEntity.Read]]] =
    NonEmptyList.fromList(entities) match {
      case Some(values) => performDeleteMany(values)
      case None =>
        List
          .empty[ApiKeyTemplatesUsersEntity.Read]
          .asRight[ApiKeyTemplatesUsersNotFoundError]
          .pure[doobie.ConnectionIO]
    }

  private def performDeleteMany(
      entities: NonEmptyList[ApiKeyTemplatesUsersEntity.Write]
  ): doobie.ConnectionIO[Either[ApiKeyTemplatesUsersNotFoundError, List[ApiKeyTemplatesUsersEntity.Read]]] =
    for {
      entitiesFound <- Queries.getAllThatExistFrom(entities).stream.compile.toList
      missingEntities = filterMissingEntities(entities, entitiesFound)

      resultE <-
        if (missingEntities.isEmpty)
          Queries.deleteMany(entities).run.map(_ => entitiesFound.asRight[ApiKeyTemplatesUsersNotFoundError])
        else
          ApiKeyTemplatesUsersNotFoundError(missingEntities)
            .asLeft[List[ApiKeyTemplatesUsersEntity.Read]]
            .pure[doobie.ConnectionIO]
    } yield resultE

  private def filterMissingEntities(
      entitiesToDelete: NonEmptyList[ApiKeyTemplatesUsersEntity.Write],
      entitiesFound: List[ApiKeyTemplatesUsersEntity.Read]
  ): List[ApiKeyTemplatesUsersEntity.Write] = {
    val entitiesFoundWrite = convertEntitiesReadToWrite(entitiesFound)
    entitiesToDelete.iterator.toSet.diff(entitiesFoundWrite.toSet).toList
  }

  private def convertEntitiesReadToWrite(
      entitiesRead: List[ApiKeyTemplatesUsersEntity.Read]
  ): List[ApiKeyTemplatesUsersEntity.Write] =
    entitiesRead.map { entityRead =>
      ApiKeyTemplatesUsersEntity.Write(
        apiKeyTemplateId = entityRead.apiKeyTemplateId,
        userId = entityRead.userId
      )
    }
  private object Queries {

    def insertMany(
        entities: List[ApiKeyTemplatesUsersEntity.Write]
    ): Stream[doobie.ConnectionIO, ApiKeyTemplatesUsersEntity.Read] = {
      val sql = s"INSERT INTO api_key_templates_users (api_key_template_id, user_id) VALUES (?, ?)"

      Update[ApiKeyTemplatesUsersEntity.Write](sql)
        .updateManyWithGeneratedKeys[ApiKeyTemplatesUsersEntity.Read](
          "api_key_template_id",
          "user_id"
        )(entities)
    }

    def deleteAllForUser(publicTenantId: TenantId, publicUserId: UserId): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_users
            USING tenant_user, tenant
            WHERE api_key_templates_users.user_id = tenant_user.id
              AND tenant_user.public_user_id = ${publicUserId.toString}
              AND tenant_user.tenant_id = tenant.id
              AND tenant.public_tenant_id = ${publicTenantId.toString}
           """.stripMargin.update

    def deleteAllForApiKeyTemplate(publicTemplateId: ApiKeyTemplateId): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_users
            USING api_key_template
            WHERE api_key_templates_users.api_key_template_id = api_key_template.id
              AND api_key_template.public_template_id = ${publicTemplateId.toString}
           """.stripMargin.update

    def deleteMany(entities: NonEmptyList[ApiKeyTemplatesUsersEntity.Write]): doobie.Update0 =
      sql"""DELETE FROM api_key_templates_users
            WHERE (api_key_template_id, user_id) IN (${Fragments.values(entities)})
           """.stripMargin.update

    def getAllThatExistFrom(
        entities: NonEmptyList[ApiKeyTemplatesUsersEntity.Write]
    ): doobie.Query0[ApiKeyTemplatesUsersEntity.Read] =
      sql"""SELECT
              api_key_template_id,
              user_id
            FROM api_key_templates_users
            WHERE (api_key_template_id, user_id) IN (${Fragments.values(entities)})
            """.stripMargin.query[ApiKeyTemplatesUsersEntity.Read]

  }
}
