package apikeysteward.services

import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError.ApplicationAlreadyExistsError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.{ApplicationInsertionError, ApplicationNotFoundError}
import apikeysteward.model.{Application, ApplicationUpdate}
import apikeysteward.repositories.ApplicationRepository
import apikeysteward.routes.model.admin.application.{CreateApplicationRequest, UpdateApplicationRequest}
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

import java.util.UUID

class ApplicationService(uuidGenerator: UuidGenerator, applicationRepository: ApplicationRepository) extends Logging {

  def createApplication(
      tenantId: UUID,
      createApplicationRequest: CreateApplicationRequest
  ): IO[Either[ApplicationInsertionError, Application]] = createApplicationWithRetry(tenantId, createApplicationRequest)

  private def createApplicationWithRetry(
      tenantId: UUID,
      createApplicationRequest: CreateApplicationRequest
  ): IO[Either[ApplicationInsertionError, Application]] = {

    val createApplicationMaxRetries = 3
    def isWorthRetrying(err: ApplicationInsertionError): Boolean = err match {
      case _: ApplicationAlreadyExistsError => true
      case _                                => false
    }

    def createApplicationAction: IO[Either[ApplicationInsertionError, Application]] =
      for {
        _ <- logger.info("Generating Application ID...")
        applicationId <- uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated Application ID."))

        _ <- logger.info("Inserting Application into database...")
        newApplication <- applicationRepository
          .insert(tenantId, Application.from(applicationId, createApplicationRequest))
          .flatTap {
            case Right(_) => logger.info(s"Inserted Application with applicationId: [$tenantId] into database.")
            case Left(e)  => logger.warn(s"Could not insert Application into database because: ${e.message}")
          }

      } yield newApplication

    Retry
      .retry(maxRetries = createApplicationMaxRetries, isWorthRetrying)(createApplicationAction)
      .map(_.asRight)
      .recover { case exc: RetryException[ApplicationInsertionError] => exc.error.asLeft[Application] }
  }

  def updateApplication(
      applicationId: UUID,
      updateApplicationRequest: UpdateApplicationRequest
  ): IO[Either[ApplicationNotFoundError, Application]] =
    applicationRepository.update(ApplicationUpdate.from(applicationId, updateApplicationRequest)).flatTap {
      case Right(_) => logger.info(s"Updated Application with applicationId: [$applicationId].")
      case Left(e) =>
        logger.warn(s"Could not update Application with applicationId: [$applicationId] because: ${e.message}")
    }

  def reactivateApplication(applicationId: UUID): IO[Either[ApplicationNotFoundError, Application]] =
    applicationRepository.activate(applicationId).flatTap {
      case Right(_) => logger.info(s"Activated Application with applicationId: [$applicationId].")
      case Left(e) =>
        logger.warn(s"Could not activate Application with applicationId: [$applicationId] because: ${e.message}")
    }

  def deactivateApplication(applicationId: UUID): IO[Either[ApplicationNotFoundError, Application]] =
    applicationRepository.deactivate(applicationId).flatTap {
      case Right(_) => logger.info(s"Deactivated Application with applicationId: [$applicationId].")
      case Left(e) =>
        logger.warn(s"Could not deactivate Application with applicationId: [$applicationId] because: ${e.message}")
    }

  def deleteApplication(applicationId: UUID): IO[Either[ApplicationDbError, Application]] =
    applicationRepository.delete(applicationId).flatTap {
      case Right(_) => logger.info(s"Deleted Application with applicationId: [$applicationId].")
      case Left(e) =>
        logger.warn(s"Could not delete Application with applicationId: [$applicationId] because: ${e.message}")
    }

  def getBy(applicationId: UUID): IO[Option[Application]] =
    applicationRepository.getBy(applicationId)

  def getAllForTenant(tenantId: UUID): IO[List[Application]] =
    applicationRepository.getAllForTenant(tenantId)

}
