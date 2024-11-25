package apikeysteward.services

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError
import apikeysteward.model.RepositoryErrors.GenericError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerNotFoundError
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.PermissionAlreadyExistsError
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.{ApiKeyTemplateRepository, PermissionRepository, ResourceServerRepository}
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class PermissionService(
    uuidGenerator: UuidGenerator,
    permissionRepository: PermissionRepository,
    resourceServerRepository: ResourceServerRepository,
    apiKeyTemplateRepository: ApiKeyTemplateRepository
) extends Logging {

  def createPermission(
      resourceServerId: ResourceServerId,
      createPermissionRequest: CreatePermissionRequest
  ): IO[Either[PermissionInsertionError, Permission]] =
    createPermissionWithRetry(resourceServerId, createPermissionRequest)

  private def createPermissionWithRetry(
      resourceServerId: ResourceServerId,
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
          .insert(resourceServerId, Permission.from(permissionId, createPermissionRequest))
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
      resourceServerId: ResourceServerId,
      permissionId: PermissionId
  ): IO[Either[PermissionNotFoundError, Permission]] =
    permissionRepository.delete(resourceServerId, permissionId).flatTap {
      case Right(_) => logger.info(s"Deleted Permission with permissionId: [$permissionId].")
      case Left(e) =>
        logger.warn(s"Could not delete Permission with permissionId: [$permissionId] because: ${e.message}")
    }

  def getBy(resourceServerId: ResourceServerId, permissionId: PermissionId): IO[Option[Permission]] =
    permissionRepository.getBy(resourceServerId, permissionId)

  def getAllForTemplate(
      templateId: ApiKeyTemplateId
  ): IO[Either[GenericError.ApiKeyTemplateDoesNotExistError, List[Permission]]] =
    (for {
      _ <- EitherT(
        apiKeyTemplateRepository
          .getBy(templateId)
          .map(_.toRight(GenericError.ApiKeyTemplateDoesNotExistError(templateId)))
      )

      result <- EitherT.liftF[IO, GenericError.ApiKeyTemplateDoesNotExistError, List[Permission]](
        permissionRepository.getAllFor(templateId)
      )
    } yield result).value

  def getAllBy(
      resourceServerId: ResourceServerId
  )(nameFragment: Option[String]): IO[Either[ResourceServerNotFoundError, List[Permission]]] =
    (for {
      _ <- EitherT(
        resourceServerRepository
          .getBy(resourceServerId)
          .map(_.toRight(ResourceServerNotFoundError(resourceServerId.toString)))
      )

      result <- EitherT.liftF[IO, ResourceServerNotFoundError, List[Permission]](
        permissionRepository.getAllBy(resourceServerId)(nameFragment)
      )
    } yield result).value

}
