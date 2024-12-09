package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.errors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError
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
      publicTenantId: TenantId,
      templateId: ApiKeyTemplateId,
      permissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsInsertionError, Unit]] =
    apiKeyTemplatesPermissionsRepository.insertMany(publicTenantId, templateId, permissionIds).flatTap {
      case Right(_) =>
        logger.info(
          s"""Associated Permissions with permissionIds: [${permissionIds.mkString(", ")}]
             | with Template with templateId: [$templateId].""".stripMargin
        )
      case Left(e) =>
        logger.warn(
          s"""Could not associate Permissions with permissionIds: [${permissionIds.mkString(", ")}]
             | with Template with templateId: [$templateId] because: ${e.message}""".stripMargin
        )
    }

  def removePermissionsFromApiKeyTemplate(
      publicTenantId: TenantId,
      templateId: ApiKeyTemplateId,
      permissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsDbError, Unit]] =
    apiKeyTemplatesPermissionsRepository.deleteMany(publicTenantId, templateId, permissionIds).flatTap {
      case Right(_) =>
        logger.info(
          s"""Removed associations between Permissions with permissionIds: [${permissionIds.mkString(", ")}]
             | and Template with templateId: [$templateId].""".stripMargin
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
          s"""Associated Users for Tenant with tenantId: [$tenantId] and userIds: [${userIds.mkString(", ")}]
             | with Template with templateId: [$templateId].""".stripMargin
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
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] =
    apiKeyTemplatesUsersRepository.insertManyTemplates(tenantId, userId, templateIds).flatTap {
      case Right(_) =>
        logger.info(
          s"""Associated Templates with templateIds: [${templateIds.mkString(", ")}]
             | with User for Tenant with tenantId: [$tenantId] and userId: [$userId].""".stripMargin
        )
      case Left(e) =>
        logger.warn(
          s"""Could not associate Templates with templateIds: [${templateIds.mkString(", ")}]
             | with User for Tenant with tenantId: [$tenantId] and userId: [$userId] because: ${e.message}""".stripMargin
        )
    }

  def removeApiKeyTemplatesFromUser(
      tenantId: TenantId,
      userId: UserId,
      templateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersDbError, Unit]] =
    apiKeyTemplatesUsersRepository.deleteManyTemplates(tenantId, userId, templateIds).flatTap {
      case Right(_) =>
        logger.info(
          s"""Removed associations between Templates with templateIds: [${templateIds.mkString(", ")}]
             | and User for Tenant with tenantId: [$tenantId] and userId: [$userId].""".stripMargin
        )
      case Left(e) =>
        logger.warn(
          s"""Could not remove associations between Templates with templateIds: [${templateIds.mkString(", ")}]
             | and User for Tenant with tenantId: [$tenantId] and userId: [$userId] because: ${e.message}""".stripMargin
        )
    }

}
