package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.{
  ApiKeyTemplateAlreadyExistsError,
  IncorrectRandomSectionLength
}
import apikeysteward.model.errors.ApiKeyTemplateDbError._
import apikeysteward.model.errors.CommonError.UserDoesNotExistError
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
    userRepository: UserRepository
) extends Logging {

  def createApiKeyTemplate(
      tenantId: TenantId,
      createApiKeyTemplateRequest: CreateApiKeyTemplateRequest
  ): IO[Either[ApiKeyTemplateInsertionError, ApiKeyTemplate]] =
    (for {
      _ <- EitherT(validateRequest(createApiKeyTemplateRequest))
      res <- EitherT(createApiKeyTemplateWithRetry(tenantId, createApiKeyTemplateRequest))
    } yield res).value

  private def validateRequest(
      createApiKeyTemplateRequest: CreateApiKeyTemplateRequest
  ): IO[Either[IncorrectRandomSectionLength, Unit]] =
    IO {
      Either.cond(
        createApiKeyTemplateRequest.randomSectionLength > 0,
        (),
        IncorrectRandomSectionLength(createApiKeyTemplateRequest.randomSectionLength)
      )
    }

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
      publicTenantId: TenantId,
      templateId: ApiKeyTemplateId,
      updateApiKeyTemplateRequest: UpdateApiKeyTemplateRequest
  ): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    apiKeyTemplateRepository
      .update(publicTenantId, ApiKeyTemplateUpdate.from(templateId, updateApiKeyTemplateRequest))
      .flatTap {
        case Right(_) => logger.info(s"Updated API Key Template with templateId: [$templateId].")
        case Left(e) =>
          logger.warn(s"Could not update API Key Template with templateId: [$templateId] because: ${e.message}")
      }

  def deleteApiKeyTemplate(
      publicTenantId: TenantId,
      templateId: ApiKeyTemplateId
  ): IO[Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]] =
    apiKeyTemplateRepository.delete(publicTenantId, templateId).flatTap {
      case Right(_) => logger.info(s"Deleted API Key Template with templateId: [$templateId].")
      case Left(e) =>
        logger.warn(s"Could not delete API Key Template with templateId: [$templateId] because: ${e.message}")
    }

  def getBy(publicTenantId: TenantId, templateId: ApiKeyTemplateId): IO[Option[ApiKeyTemplate]] =
    apiKeyTemplateRepository.getBy(publicTenantId, templateId)

  def getAllForTenant(tenantId: TenantId): IO[List[ApiKeyTemplate]] =
    apiKeyTemplateRepository.getAllForTenant(tenantId)

  def getAllForUser(
      tenantId: TenantId,
      userId: UserId
  ): IO[Either[UserDoesNotExistError, List[ApiKeyTemplate]]] =
    (for {
      _ <- EitherT(userRepository.getBy(tenantId, userId).map(_.toRight(UserDoesNotExistError(tenantId, userId))))

      result <- EitherT.liftF[IO, UserDoesNotExistError, List[ApiKeyTemplate]](
        apiKeyTemplateRepository.getAllForUser(tenantId, userId)
      )
    } yield result).value

}
