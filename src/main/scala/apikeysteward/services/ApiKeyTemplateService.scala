package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.ApiKeyTemplateAlreadyExistsError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.ReferencedUserDoesNotExistError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories._
import apikeysteward.routes.model.admin.apikeytemplate.{CreateApiKeyTemplateRequest, UpdateApiKeyTemplateRequest}
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class ApiKeyTemplateService(
    uuidGenerator: UuidGenerator,
    apiKeyTemplateRepository: ApiKeyTemplateRepository,
    userRepository: UserRepository,
    apiKeyTemplatesPermissionsRepository: ApiKeyTemplatesPermissionsRepository,
    apiKeyTemplatesUsersRepository: ApiKeyTemplatesUsersRepository
) extends Logging {

  def createApiKeyTemplate(
      tenantId: TenantId,
      createApiKeyTemplateRequest: CreateApiKeyTemplateRequest
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
    createApiKeyTemplateWithRetry(tenantId, createApiKeyTemplateRequest)

  private def createApiKeyTemplateWithRetry(
      tenantId: TenantId,
      createApiKeyTemplateRequest: CreateApiKeyTemplateRequest
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] = {

    val createApiKeyTemplateMaxRetries = 3
    def isWorthRetrying(err: ApiKeyTemplateInsertionError): Boolean = err match {
      case _: ApiKeyTemplateAlreadyExistsError => true
      case _                                   => false
    }

    def createApiKeyTemplateAction: IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
      for {
        _ <- logger.info("Generating API Key Template ID...")
        templateId <- uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated API Key Template ID."))

        _ <- logger.info("Inserting API Key Template into database...")
        apiKeyTemplateToInsert = ApiKeyTemplate.from(templateId, createApiKeyTemplateRequest)

        newApiKeyTemplate <- apiKeyTemplateRepository.insert(tenantId, apiKeyTemplateToInsert).flatTap {
          case Right(_) => logger.info(s"Inserted API Key Template with templateId: [$templateId] into database.")
          case Left(e)  => logger.warn(s"Could not insert API Key Template into database because: ${e.message}")
        }

      } yield newApiKeyTemplate

    Retry
      .retry(maxRetries = createApiKeyTemplateMaxRetries, isWorthRetrying)(createApiKeyTemplateAction)
      .map(_.asRight)
      .recover { case exc: RetryException[ApiKeyTemplateInsertionError] => exc.error.asLeft[ApiKeyTemplate] }
  }

  def updateApiKeyTemplate(
      templateId: ApiKeyTemplateId,
      updateApiKeyTemplateRequest: UpdateApiKeyTemplateRequest
  ): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    apiKeyTemplateRepository.update(ApiKeyTemplateUpdate.from(templateId, updateApiKeyTemplateRequest)).flatTap {
      case Right(_) => logger.info(s"Updated API Key Template with templateId: [$templateId].")
      case Left(e) =>
        logger.warn(s"Could not update API Key Template with templateId: [$templateId] because: ${e.message}")
    }

  def deleteApiKeyTemplate(templateId: ApiKeyTemplateId): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    apiKeyTemplateRepository.delete(templateId).flatTap {
      case Right(_) => logger.info(s"Deleted API Key Template with templateId: [$templateId].")
      case Left(e) =>
        logger.warn(s"Could not delete API Key Template with templateId: [$templateId] because: ${e.message}")
    }

  def getBy(templateId: ApiKeyTemplateId): IO[Option[ApiKeyTemplate]] =
    apiKeyTemplateRepository.getBy(templateId)

  def getAllForTenant(tenantId: TenantId): IO[List[ApiKeyTemplate]] =
    apiKeyTemplateRepository.getAllForTenant(tenantId)

  def getAllForUser(
      tenantId: TenantId,
      userId: UserId
  ): IO[Either[ReferencedUserDoesNotExistError, List[ApiKeyTemplate]]] =
    (for {
      _ <- EitherT(
        userRepository.getBy(tenantId, userId).map(_.toRight(ReferencedUserDoesNotExistError(userId, tenantId)))
      )

      result <- EitherT.liftF[IO, ReferencedUserDoesNotExistError, List[ApiKeyTemplate]](
        apiKeyTemplateRepository.getAllForUser(tenantId, userId)
      )
    } yield result).value

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

}
