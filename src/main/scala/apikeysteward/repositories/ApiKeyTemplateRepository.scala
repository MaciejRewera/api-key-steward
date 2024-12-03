package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import fs2.Stream

import java.util.UUID

class ApiKeyTemplateRepository(
    uuidGenerator: UuidGenerator,
    tenantDb: TenantDb,
    apiKeyTemplateDb: ApiKeyTemplateDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb,
    apiKeyTemplatesUsersDb: ApiKeyTemplatesUsersDb
)(transactor: Transactor[IO]) {

  def insert(
      publicTenantId: TenantId,
      apiKeyTemplate: ApiKeyTemplate
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
    for {
      apiKeyTemplateDbId <- uuidGenerator.generateUuid
      result <- insert(apiKeyTemplateDbId, publicTenantId, apiKeyTemplate)
    } yield result

  private def insert(
      apiKeyTemplateDbId: UUID,
      publicTenantId: TenantId,
      apiKeyTemplate: ApiKeyTemplate
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      templateEntityRead <- EitherT(
        apiKeyTemplateDb.insert(ApiKeyTemplateEntity.Write.from(apiKeyTemplateDbId, tenantId, apiKeyTemplate))
      )

      resultTemplate <- EitherT.liftF[ConnectionIO, ApiKeyTemplateInsertionError, ApiKeyTemplate](
        constructApiKeyTemplate(publicTenantId, templateEntityRead)
      )
    } yield resultTemplate).value.transact(transactor)

  def update(
      publicTenantId: TenantId,
      apiKeyTemplate: ApiKeyTemplateUpdate
  ): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    (for {
      templateEntityRead <- EitherT(apiKeyTemplateDb.update(ApiKeyTemplateEntity.Update.from(apiKeyTemplate)))

      resultTemplate <- EitherT.liftF[ConnectionIO, ApiKeyTemplateNotFoundError, ApiKeyTemplate](
        constructApiKeyTemplate(publicTenantId, templateEntityRead)
      )
    } yield resultTemplate).value.transact(transactor)

  def delete(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    deleteOp(publicTenantId, publicTemplateId).transact(transactor)

  private[repositories] def deleteOp(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): ConnectionIO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    for {
      permissionEntitiesToDeleteAssociationWith <- permissionDb
        .getAllForTemplate(publicTenantId, publicTemplateId)
        .compile
        .toList

      _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId)
      _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTemplateId)
      deletedTemplateEntity <- apiKeyTemplateDb.delete(publicTemplateId)

      deletedTemplate = deletedTemplateEntity.map(ApiKeyTemplate.from(_, permissionEntitiesToDeleteAssociationWith))
    } yield deletedTemplate

  def getBy(publicTenantId: TenantId, publicTemplateId: ApiKeyTemplateId): IO[Option[ApiKeyTemplate]] =
    (for {
      templateEntityRead <- OptionT(apiKeyTemplateDb.getByPublicTemplateId(publicTemplateId))
      resultTemplate <- OptionT.liftF(constructApiKeyTemplate(publicTenantId, templateEntityRead))
    } yield resultTemplate).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[ApiKeyTemplate]] =
    getAllForTenantOp(publicTenantId).compile.toList.transact(transactor)

  private[repositories] def getAllForTenantOp(publicTenantId: TenantId): Stream[ConnectionIO, ApiKeyTemplate] =
    for {
      templateEntityRead <- apiKeyTemplateDb.getAllForTenant(publicTenantId)
      resultTemplate <- Stream.eval(constructApiKeyTemplate(publicTenantId, templateEntityRead))
    } yield resultTemplate

  private def constructApiKeyTemplate(
      publicTenantId: TenantId,
      templateEntity: ApiKeyTemplateEntity.Read
  ): ConnectionIO[ApiKeyTemplate] =
    for {
      permissionEntities <- permissionDb
        .getAllForTemplate(publicTenantId, UUID.fromString(templateEntity.publicTemplateId))
        .compile
        .toList

      resultTemplate = ApiKeyTemplate.from(templateEntity, permissionEntities)
    } yield resultTemplate

  def getAllForUser(publicTenantId: TenantId, publicUserId: UserId): IO[List[ApiKeyTemplate]] =
    (for {
      templateEntityRead <- apiKeyTemplateDb.getAllForUser(publicTenantId, publicUserId)
      resultTemplate <- Stream.eval(constructApiKeyTemplate(publicTenantId, templateEntityRead))
    } yield resultTemplate).compile.toList.transact(transactor)

}
