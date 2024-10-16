package apikeysteward.services

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationNotFoundError
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.PermissionAlreadyExistsError
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.{ApplicationRepository, PermissionRepository}
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class PermissionService(
    uuidGenerator: UuidGenerator,
    permissionRepository: PermissionRepository,
    applicationRepository: ApplicationRepository
) extends Logging {

  def createPermission(
      applicationId: ApplicationId,
      createPermissionRequest: CreatePermissionRequest
  ): IO[Either[PermissionInsertionError, Permission]] =
    createPermissionWithRetry(applicationId, createPermissionRequest)

  private def createPermissionWithRetry(
      applicationId: ApplicationId,
      createPermissionRequest: CreatePermissionRequest
  ): IO[Either[PermissionInsertionError, Permission]] = {

    val createPermissionMaxRetries = 3
    def isWorthRetrying(err: PermissionInsertionError): Boolean = err match {
      case _: PermissionAlreadyExistsError => true
      case _                               => false
    }

    def createPermissionAction: IO[Either[PermissionInsertionError, Permission]] =
      for {
        _ <- logger.info("Generating Permission ID...")
        permissionId <- uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated Permission ID."))

        _ <- logger.info("Inserting Permission into database...")
        newPermission <- permissionRepository
          .insert(applicationId, Permission.from(permissionId, createPermissionRequest))
          .flatTap {
            case Right(_) => logger.info(s"Inserted Permission with permissionId: [$permissionId] into database.")
            case Left(e)  => logger.warn(s"Could not insert Permission into database because: ${e.message}")
          }
      } yield newPermission

    Retry
      .retry(maxRetries = createPermissionMaxRetries, isWorthRetrying)(createPermissionAction)
      .map(_.asRight)
      .recover { case exc: RetryException[PermissionInsertionError] => exc.error.asLeft[Permission] }
  }

  def deletePermission(
      applicationId: ApplicationId,
      permissionId: PermissionId
  ): IO[Either[PermissionNotFoundError, Permission]] =
    permissionRepository.delete(applicationId, permissionId).flatTap {
      case Right(_) => logger.info(s"Deleted Permission with permissionId: [$permissionId].")
      case Left(e) =>
        logger.warn(s"Could not delete Permission with permissionId: [$permissionId] because: ${e.message}")
    }

  def getBy(applicationId: ApplicationId, permissionId: PermissionId): IO[Option[Permission]] =
    permissionRepository.getBy(applicationId, permissionId)

  def getAllBy(
      applicationId: ApplicationId
  )(nameFragment: Option[String]): IO[Either[ApplicationNotFoundError, List[Permission]]] =
    (for {
      _ <- EitherT(
        applicationRepository
          .getBy(applicationId)
          .map(_.toRight(ApplicationNotFoundError(applicationId.toString)))
      )

      result <- EitherT.liftF[IO, ApplicationNotFoundError, List[Permission]](
        permissionRepository.getAllBy(applicationId)(nameFragment)
      )
    } yield result).value

}
