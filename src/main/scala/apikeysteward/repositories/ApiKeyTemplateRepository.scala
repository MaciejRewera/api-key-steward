package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesPermissionsDb, PermissionDb, TenantDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import fs2.Stream

import java.util.UUID

class ApiKeyTemplateRepository(
    tenantDb: TenantDb,
    apiKeyTemplateDb: ApiKeyTemplateDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb
)(transactor: Transactor[IO]) {

  def insert(
      publicTenantId: TenantId,
      apiKeyTemplate: ApiKeyTemplate
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      templateEntityRead <- EitherT(apiKeyTemplateDb.insert(ApiKeyTemplateEntity.Write.from(tenantId, apiKeyTemplate)))

      resultTemplate <- EitherT.liftF[ConnectionIO, ApiKeyTemplateInsertionError, ApiKeyTemplate](
        constructApiKeyTemplate(templateEntityRead)
      )
    } yield resultTemplate).value.transact(transactor)

  def update(apiKeyTemplate: ApiKeyTemplateUpdate): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    (for {
      templateEntityRead <- EitherT(apiKeyTemplateDb.update(ApiKeyTemplateEntity.Update.from(apiKeyTemplate)))

      resultTemplate <- EitherT.liftF[ConnectionIO, ApiKeyTemplateNotFoundError, ApiKeyTemplate](
        constructApiKeyTemplate(templateEntityRead)
      )
    } yield resultTemplate).value.transact(transactor)

  def delete(publicTemplateId: ApiKeyTemplateId): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    (for {
      templateEntityRead <- EitherT(deleteOp(publicTemplateId))

      resultTemplate <- EitherT.liftF[ConnectionIO, ApiKeyTemplateNotFoundError, ApiKeyTemplate](
        constructApiKeyTemplate(templateEntityRead)
      )
    } yield resultTemplate).value.transact(transactor)

  private[repositories] def deleteOp(
      publicTemplateId: ApiKeyTemplateId
  ): ConnectionIO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplateEntity.Read]] =
    for {
      _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId)
      deletedTemplateEntity <- apiKeyTemplateDb.delete(publicTemplateId)
    } yield deletedTemplateEntity

  def getBy(publicTemplateId: ApiKeyTemplateId): IO[Option[ApiKeyTemplate]] =
    (for {
      templateEntityRead <- OptionT(apiKeyTemplateDb.getByPublicTemplateId(publicTemplateId))
      resultTemplate <- OptionT.liftF(constructApiKeyTemplate(templateEntityRead))
    } yield resultTemplate).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[ApiKeyTemplate]] =
    (for {
      templateEntityRead <- apiKeyTemplateDb.getAllForTenant(publicTenantId)
      resultTemplate <- Stream.eval(constructApiKeyTemplate(templateEntityRead))
    } yield resultTemplate).compile.toList.transact(transactor)

  private def constructApiKeyTemplate(templateEntity: ApiKeyTemplateEntity.Read): ConnectionIO[ApiKeyTemplate] =
    for {
      permissionEntities <- permissionDb
        .getAllPermissionsForTemplate(UUID.fromString(templateEntity.publicTemplateId))
        .compile
        .toList

      resultTemplate = ApiKeyTemplate.from(templateEntity, permissionEntities)
    } yield resultTemplate
}
