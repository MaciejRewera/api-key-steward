package apikeysteward.generators

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.repositories.ApiKeyTemplateRepository
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class ApiKeyPrefixProvider(apiKeyTemplateRepository: ApiKeyTemplateRepository) {

  def fetchPrefix(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): IO[Either[ApiKeyTemplateNotFoundError, String]] =
    apiKeyTemplateRepository.getBy(publicTenantId, publicTemplateId).map {
      case Some(template) => template.apiKeyPrefix.asRight
      case None           => ApiKeyTemplateNotFoundError(publicTemplateId.toString).asLeft
    }

}
