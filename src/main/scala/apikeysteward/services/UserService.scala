package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User
import apikeysteward.model.User.UserId
import apikeysteward.repositories.{ApiKeyTemplateRepository, TenantRepository, UserRepository}
import apikeysteward.routes.model.admin.user.CreateUserRequest
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO

class UserService(
    userRepository: UserRepository,
    tenantRepository: TenantRepository,
    apiKeyTemplateRepository: ApiKeyTemplateRepository
) extends Logging {

  def createUser(tenantId: TenantId, createUserRequest: CreateUserRequest): IO[Either[UserInsertionError, User]] =
    for {
      _ <- logger.info(
        s"Inserting User with userId: [${createUserRequest.userId}] for Tenant with tenantId: [$tenantId] into database..."
      )
      newUser <- userRepository.insert(tenantId, User.from(createUserRequest)).flatTap {
        case Right(_) =>
          logger.info(
            s"Inserted User with userId: [${createUserRequest.userId}] for Tenant with tenantId: [$tenantId] into database."
          )
        case Left(e) =>
          logger.warn(
            s"Could not insert User with userId: [${createUserRequest.userId}] for Tenant with tenantId: [$tenantId] into database because: ${e.message}"
          )
      }

    } yield newUser

  def deleteUser(tenantId: TenantId, userId: UserId): IO[Either[UserNotFoundError, User]] =
    userRepository.delete(tenantId, userId).flatTap {
      case Right(_) => logger.info(s"Deleted User with userId: [$userId] for Tenant with tenantId: [$tenantId].")
      case Left(e) =>
        logger.warn(
          s"Could not delete User with userId: [$userId] for Tenant with tenantId: [$tenantId] because: ${e.message}"
        )
    }

  def getBy(tenantId: TenantId, userId: UserId): IO[Option[User]] =
    userRepository.getBy(tenantId, userId)

  def getAllForTenant(tenantId: TenantId): IO[Either[ReferencedTenantDoesNotExistError, List[User]]] =
    (for {
      _ <- EitherT(tenantRepository.getBy(tenantId).map(_.toRight(ReferencedTenantDoesNotExistError(tenantId))))

      result <- EitherT.liftF[IO, ReferencedTenantDoesNotExistError, List[User]](
        userRepository.getAllForTenant(tenantId)
      )
    } yield result).value

  def getAllForTemplate(
      templateId: ApiKeyTemplateId
  ): IO[Either[ReferencedApiKeyTemplateDoesNotExistError, List[User]]] =
    (for {
      _ <- EitherT(
        apiKeyTemplateRepository.getBy(templateId).map(_.toRight(ReferencedApiKeyTemplateDoesNotExistError(templateId)))
      )

      result <- EitherT.liftF[IO, ReferencedApiKeyTemplateDoesNotExistError, List[User]](
        userRepository.getAllForTemplate(templateId)
      )
    } yield result).value

}
