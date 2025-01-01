package apikeysteward.services

import apikeysteward.config.ApiKeyConfig
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.CustomError
import apikeysteward.model.{ApiKeyTemplate, User}
import apikeysteward.repositories.{ApiKeyTemplateRepository, UserRepository}
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError._
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO

import scala.concurrent.duration.{Duration, FiniteDuration}

class CreateApiKeyRequestValidator(
    apiKeyConfig: ApiKeyConfig,
    userRepository: UserRepository,
    apiKeyTemplateRepository: ApiKeyTemplateRepository
) extends Logging {

  def validateCreateRequest(
      publicTenantId: TenantId,
      publicUserId: UserId,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[CreateApiKeyRequestValidatorError, CreateApiKeyRequest]] =
    (for {
      _ <- validateUserId(publicTenantId, publicUserId)
      _ <- validateTemplate(publicTenantId, createApiKeyRequest)
      _ <- validateTimeToLive(createApiKeyRequest)
    } yield createApiKeyRequest).value.flatTap {
      case Right(_) =>
        logger.info(s"CreateApiKeyRequest validation was successful.")
      case Left(validationError) =>
        logger.warn(s"CreateApiKeyRequest validation failed because: ${validationError.message}")
    }

  private def validateUserId(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): EitherT[IO, CreateApiKeyRequestValidatorError, User] =
    EitherT {
      for {
        userOpt <- userRepository.getBy(publicTenantId, publicUserId)

        res = userOpt.toRight(UserNotFoundError(publicTenantId, publicUserId))
      } yield res
    }

  private def validateTemplate(
      publicTenantId: TenantId,
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, CreateApiKeyRequestValidatorError, CreateApiKeyRequest] =
    for {
      apiKeyTemplate <- validateTemplateExists(publicTenantId, createApiKeyRequest)
      _ <- validateRequestPermissions(publicTenantId, createApiKeyRequest, apiKeyTemplate)
    } yield createApiKeyRequest

  private def validateTemplateExists(
      publicTenantId: TenantId,
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, CreateApiKeyRequestValidatorError, ApiKeyTemplate] =
    EitherT {
      for {
        templateOpt <- apiKeyTemplateRepository.getBy(publicTenantId, createApiKeyRequest.templateId)

        res = templateOpt.toRight(ApiKeyTemplateNotFoundError(publicTenantId, createApiKeyRequest.templateId))
      } yield res
    }

  private def validateRequestPermissions(
      publicTenantId: TenantId,
      createApiKeyRequest: CreateApiKeyRequest,
      apiKeyTemplate: ApiKeyTemplate
  ): EitherT[IO, CreateApiKeyRequestValidatorError, CreateApiKeyRequest] = {
    val allowedPermissionIds = apiKeyTemplate.permissions.map(_.publicPermissionId).toSet
    val requestPermissionIds = createApiKeyRequest.permissionIds.toSet

    val notAllowedPermissions = requestPermissionIds.diff(allowedPermissionIds)

    EitherT.cond(
      notAllowedPermissions.isEmpty,
      createApiKeyRequest,
      PermissionsNotAllowedError(publicTenantId, createApiKeyRequest.templateId, notAllowedPermissions.toList)
    )
  }

  private def validateTimeToLive(
      createApiKeyRequest: CreateApiKeyRequest
  ): EitherT[IO, CreateApiKeyRequestValidatorError, CreateApiKeyRequest] =
    EitherT
      .cond(
        createApiKeyRequest.ttl <= apiKeyConfig.ttlMax,
        createApiKeyRequest,
        TtlTooLargeError(createApiKeyRequest.ttl, apiKeyConfig.ttlMax)
      )

}

object CreateApiKeyRequestValidator {

  sealed abstract class CreateApiKeyRequestValidatorError(override val message: String) extends CustomError
  object CreateApiKeyRequestValidatorError {

    case class UserNotFoundError(publicTenantId: TenantId, publicUserId: UserId)
        extends CreateApiKeyRequestValidatorError(
          message = s"Could not find User with publicTenantId = [$publicTenantId] and publicUserId = [$publicUserId]."
        )

    case class ApiKeyTemplateNotFoundError(publicTenantId: TenantId, publicTemplateId: ApiKeyTemplateId)
        extends CreateApiKeyRequestValidatorError(
          message =
            s"Could not find ApiKeyTemplate with publicTenantId = [$publicTenantId] and publicTemplateId = [$publicTemplateId]."
        )

    case class PermissionsNotAllowedError(
        publicTenantId: TenantId,
        publicTemplateId: ApiKeyTemplateId,
        notAllowedPermissionIds: List[PermissionId]
    ) extends CreateApiKeyRequestValidatorError(
          message =
            s"Permissions with IDs: ${notAllowedPermissionIds.mkString("[", ", ", "]")} are not allowed for ApiKeyTemplate with publicTenantId = [$publicTenantId] and publicTemplateId = [$publicTemplateId]."
        )

    case class TtlTooLargeError(ttlRequest: Duration, ttlMax: FiniteDuration)
        extends CreateApiKeyRequestValidatorError(
          message =
            s"Provided request contains time-to-live (ttl) of: $ttlRequest which is bigger than maximum allowed value of: $ttlMax."
        )
  }

}
