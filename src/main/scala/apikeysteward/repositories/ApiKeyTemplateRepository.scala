package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import apikeysteward.repositories.db.{ApiKeyTemplateDb, TenantDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

class ApiKeyTemplateRepository(tenantDb: TenantDb, apiKeyTemplateDb: ApiKeyTemplateDb)(transactor: Transactor[IO]) {

  def insert(
      publicTenantId: TenantId,
      apiKeyTemplate: ApiKeyTemplate
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      templateEntityRead <- EitherT(apiKeyTemplateDb.insert(ApiKeyTemplateEntity.Write.from(tenantId, apiKeyTemplate)))

      resultTemplate = ApiKeyTemplate.from(templateEntityRead)
    } yield resultTemplate).value.transact(transactor)

  def update(apiKeyTemplate: ApiKeyTemplate): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    (for {
      templateEntityRead <- EitherT(apiKeyTemplateDb.update(ApiKeyTemplateEntity.Update.from(apiKeyTemplate)))
      resultTemplate = ApiKeyTemplate.from(templateEntityRead)
    } yield resultTemplate).value.transact(transactor)

  def delete(publicTemplateId: ApiKeyTemplateId): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    (for {
      templateEntityRead <- EitherT(apiKeyTemplateDb.delete(publicTemplateId))
      resultTemplate = ApiKeyTemplate.from(templateEntityRead)
    } yield resultTemplate).value.transact(transactor)

  def getBy(publicTemplateId: ApiKeyTemplateId): IO[Option[ApiKeyTemplate]] =
    (for {
      templateEntityRead <- OptionT(apiKeyTemplateDb.getByPublicTemplateId(publicTemplateId))
      resultTemplate = ApiKeyTemplate.from(templateEntityRead)
    } yield resultTemplate).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[ApiKeyTemplate]] =
    (for {
      templateEntityRead <- apiKeyTemplateDb.getAllForTenant(publicTenantId)
      resultTemplate = ApiKeyTemplate.from(templateEntityRead)
    } yield resultTemplate).compile.toList.transact(transactor)
}
