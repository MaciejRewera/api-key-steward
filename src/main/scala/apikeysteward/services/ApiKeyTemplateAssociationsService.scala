package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.{ApiKeyTemplatesPermissionsDbError, ApiKeyTemplatesUsersDbError}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.{ApiKeyTemplatesPermissionsRepository, ApiKeyTemplatesUsersRepository}
import apikeysteward.utils.Logging
import cats.effect.IO

class ApiKeyTemplateAssociationsService(
    apiKeyTemplatesPermissionsRepository: ApiKeyTemplatesPermissionsRepository,
    apiKeyTemplatesUsersRepository: ApiKeyTemplatesUsersRepository
) extends Logging {

  def associatePermissionsWithApiKeyTemplate(
      templateId: ApiKeyTemplateId,
      permissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsInsertionError, Unit]] =
    apiKeyTemplatesPermissionsRepository.insertMany(templateId, permissionIds).flatTap {
      case Right(_) =>
        logger.info(
          s"Associated Permissions with permissionIds: [${permissionIds.mkString(", ")}] with Template with templateId: [$templateId]."
        )
      case Left(e) =>
        logger.warn(s"Could not associate Permissions with permissionIds: [${permissionIds
          .mkString(", ")}] with Template with templateId: [$templateId] because: ${e.message}")
    }

  def removePermissionsFromApiKeyTemplate(
      templateId: ApiKeyTemplateId,
      permissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsDbError, Unit]] =
    apiKeyTemplatesPermissionsRepository.deleteMany(templateId, permissionIds).flatTap {
      case Right(_) =>
        logger.info(
          s"Removed associations between Permissions with permissionIds: [${permissionIds.mkString(", ")}] and Template with templateId: [$templateId]."
        )
      case Left(e) =>
        logger.warn(
          s"""Could not remove associations between Permissions with permissionIds: [${permissionIds.mkString(", ")}]
             | and Template with templateId: [$templateId] because: ${e.message}""".stripMargin
        )
    }

  def associateUsersWithApiKeyTemplate(
      tenantId: TenantId,
      templateId: ApiKeyTemplateId,
      userIds: List[UserId]
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] =
    apiKeyTemplatesUsersRepository.insertManyUsers(tenantId, templateId, userIds).flatTap {
      case Right(_) =>
        logger.info(
          s"Associated Users for Tenant with tenantId: [$tenantId] and userIds: [${userIds.mkString(", ")}] with Template with templateId: [$templateId]."
        )
      case Left(e) =>
        logger.warn(
          s"""Could not associate Users for Tenant with tenantId: [$tenantId] and userIds: [${userIds.mkString(", ")}]
             | with Template with templateId: [$templateId] because: ${e.message}""".stripMargin
        )
    }

  def associateApiKeyTemplatesWithUser(
      tenantId: TenantId,
      userId: UserId,
      templateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] = ???

  def removeApiKeyTemplatesFromUser(
      tenantId: TenantId,
      userId: UserId,
      templateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersDbError, Unit]] = ???

}
