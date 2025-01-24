package apikeysteward.services

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
import cats.implicits.catsSyntaxTuple2Parallel

import scala.concurrent.duration.Duration

class CreateApiKeyRequestValidator(
    userRepository: UserRepository,
    apiKeyTemplateRepository: ApiKeyTemplateRepository
) extends Logging {

  def validateCreateRequest(
      publicTenantId: TenantId,
      publicUserId: UserId,
      createApiKeyRequest: CreateApiKeyRequest
  ): IO[Either[CreateApiKeyRequestValidatorError, CreateApiKeyRequest]] =
    (for {
      validationData <- fetchValidationData(publicTenantId, publicUserId, createApiKeyRequest.templateId)
      (_, apiKeyTemplate) = validationData

      _ <- EitherT.fromEither[IO](validateRequestPermissions(publicTenantId, createApiKeyRequest, apiKeyTemplate))
      _ <- EitherT.fromEither[IO](validateTimeToLive(createApiKeyRequest, apiKeyTemplate))

    } yield createApiKeyRequest).value.flatTap {
      case Right(_) =>
        logger.info(s"CreateApiKeyRequest validation was successful.")
      case Left(validationError) =>
        logger.warn(s"CreateApiKeyRequest validation failed because: ${validationError.message}")
    }

  private def fetchValidationData(
      publicTenantId: TenantId,
      publicUserId: UserId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[IO, CreateApiKeyRequestValidatorError, (User, ApiKeyTemplate)] =
    (
      fetchUser(publicTenantId, publicUserId),
      fetchApiKeyTemplate(publicTenantId, publicTemplateId)
    ).parMapN((_, _))

  private def fetchUser(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): EitherT[IO, CreateApiKeyRequestValidatorError, User] =
    EitherT {
      for {
        userOpt <- userRepository.getBy(publicTenantId, publicUserId)
        res = userOpt.toRight(UserNotFoundError(publicTenantId, publicUserId))
      } yield res
    }

  private def fetchApiKeyTemplate(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[IO, CreateApiKeyRequestValidatorError, ApiKeyTemplate] =
    EitherT {
      for {
        templateOpt <- apiKeyTemplateRepository.getBy(publicTenantId, publicTemplateId)
        res = templateOpt.toRight(ApiKeyTemplateNotFoundError(publicTenantId, publicTemplateId))
      } yield res
    }

  private def validateRequestPermissions(
      publicTenantId: TenantId,
      createApiKeyRequest: CreateApiKeyRequest,
      apiKeyTemplate: ApiKeyTemplate
  ): Either[CreateApiKeyRequestValidatorError, CreateApiKeyRequest] = {
    val allowedPermissionIds = apiKeyTemplate.permissions.map(_.publicPermissionId).toSet
    val requestPermissionIds = createApiKeyRequest.permissionIds.toSet

    val notAllowedPermissions = requestPermissionIds.diff(allowedPermissionIds)

    Either.cond(
      notAllowedPermissions.isEmpty,
      createApiKeyRequest,
      PermissionsNotAllowedError(publicTenantId, createApiKeyRequest.templateId, notAllowedPermissions.toList)
    )
  }

  private def validateTimeToLive(
      createApiKeyRequest: CreateApiKeyRequest,
      apiKeyTemplate: ApiKeyTemplate
  ): Either[CreateApiKeyRequestValidatorError, CreateApiKeyRequest] =
    Either.cond(
      createApiKeyRequest.ttl <= apiKeyTemplate.apiKeyMaxExpiryPeriod,
      createApiKeyRequest,
      TtlTooLargeError(createApiKeyRequest.ttl, apiKeyTemplate.apiKeyMaxExpiryPeriod)
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

    case class TtlTooLargeError(ttlRequest: Duration, ttlMax: Duration)
        extends CreateApiKeyRequestValidatorError(
          message =
            s"Provided request contains time-to-live (ttl) of: $ttlRequest which is bigger than maximum allowed value of: $ttlMax."
        )
  }

}
