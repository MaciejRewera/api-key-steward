package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError
import apikeysteward.repositories.ApiKeyTemplatesPermissionsRepository
import apikeysteward.utils.Logging
import cats.effect.IO

class ApiKeyTemplatesPermissionsService(apiKeyTemplatesPermissionsRepository: ApiKeyTemplatesPermissionsRepository)
    extends Logging {

  def associatePermissionsWithApiKeyTemplate(
      templateId: ApiKeyTemplateId,
      permissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsInsertionError, Unit]] =
    apiKeyTemplatesPermissionsRepository.insertMany(templateId, permissionIds)

  def removePermissionsFromApiKeyTemplate(
      templateId: ApiKeyTemplateId,
      permissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsDbError, Unit]] =
    apiKeyTemplatesPermissionsRepository.deleteMany(templateId, permissionIds)

  def getAllPermissionsForApiKeyTemplate(templateId: ApiKeyTemplateId): IO[List[Permission]] =
    apiKeyTemplatesPermissionsRepository.getAllPermissionsForTemplate(templateId)

}
