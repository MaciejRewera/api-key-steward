package apikeysteward.services

import apikeysteward.model.RepositoryErrors.ResourceServerDbError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerInsertionError.ResourceServerAlreadyExistsError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError._
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ResourceServer, ResourceServerUpdate}
import apikeysteward.repositories.ResourceServerRepository
import apikeysteward.routes.model.admin.resourceserver.{CreateResourceServerRequest, UpdateResourceServerRequest}
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toTraverseOps}

class ResourceServerService(uuidGenerator: UuidGenerator, resourceServerRepository: ResourceServerRepository)
    extends Logging {

  def createResourceServer(
      tenantId: TenantId,
      createResourceServerRequest: CreateResourceServerRequest
  ): IO[Either[ResourceServerInsertionError, ResourceServer]] =
    createResourceServerWithRetry(tenantId, createResourceServerRequest)

  private def createResourceServerWithRetry(
      tenantId: TenantId,
      createResourceServerRequest: CreateResourceServerRequest
  ): IO[Either[ResourceServerInsertionError, ResourceServer]] = {

    val createResourceServerMaxRetries = 3
    def isWorthRetrying(err: ResourceServerInsertionError): Boolean = err match {
      case _: ResourceServerAlreadyExistsError => true
      case _                                   => false
    }

    def createResourceServerAction: IO[Either[ResourceServerInsertionError, ResourceServer]] =
      for {
        _ <- logger.info("Generating ResourceServer ID...")
        resourceServerId <- uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated ResourceServer ID."))

        _ <- logger.info("Generating Permission IDs...")
        permissionIds <- createResourceServerRequest.permissions
          .traverse(_ => uuidGenerator.generateUuid)
          .flatTap(_ => logger.info("Generated Permission IDs."))

        _ <- logger.info("Inserting ResourceServer into database...")
        newResourceServer <- resourceServerRepository
          .insert(tenantId, ResourceServer.from(resourceServerId, permissionIds, createResourceServerRequest))
          .flatTap {
            case Right(_) =>
              logger.info(s"Inserted ResourceServer with resourceServerId: [$resourceServerId] into database.")
            case Left(e) => logger.warn(s"Could not insert ResourceServer into database because: ${e.message}")
          }

      } yield newResourceServer

    Retry
      .retry(maxRetries = createResourceServerMaxRetries, isWorthRetrying)(createResourceServerAction)
      .map(_.asRight)
      .recover { case exc: RetryException[ResourceServerInsertionError] => exc.error.asLeft[ResourceServer] }
  }

  def updateResourceServer(
      resourceServerId: ResourceServerId,
      updateResourceServerRequest: UpdateResourceServerRequest
  ): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    resourceServerRepository.update(ResourceServerUpdate.from(resourceServerId, updateResourceServerRequest)).flatTap {
      case Right(_) => logger.info(s"Updated ResourceServer with resourceServerId: [$resourceServerId].")
      case Left(e) =>
        logger.warn(s"Could not update ResourceServer with resourceServerId: [$resourceServerId] because: ${e.message}")
    }

  def reactivateResourceServer(
      resourceServerId: ResourceServerId
  ): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    resourceServerRepository.activate(resourceServerId).flatTap {
      case Right(_) => logger.info(s"Activated ResourceServer with resourceServerId: [$resourceServerId].")
      case Left(e) =>
        logger.warn(
          s"Could not activate ResourceServer with resourceServerId: [$resourceServerId] because: ${e.message}"
        )
    }

  def deactivateResourceServer(
      resourceServerId: ResourceServerId
  ): IO[Either[ResourceServerNotFoundError, ResourceServer]] =
    resourceServerRepository.deactivate(resourceServerId).flatTap {
      case Right(_) => logger.info(s"Deactivated ResourceServer with resourceServerId: [$resourceServerId].")
      case Left(e) =>
        logger.warn(
          s"Could not deactivate ResourceServer with resourceServerId: [$resourceServerId] because: ${e.message}"
        )
    }

  def deleteResourceServer(resourceServerId: ResourceServerId): IO[Either[ResourceServerDbError, ResourceServer]] =
    resourceServerRepository.delete(resourceServerId).flatTap {
      case Right(_) => logger.info(s"Deleted ResourceServer with resourceServerId: [$resourceServerId].")
      case Left(e) =>
        logger.warn(s"Could not delete ResourceServer with resourceServerId: [$resourceServerId] because: ${e.message}")
    }

  def getBy(resourceServerId: ResourceServerId): IO[Option[ResourceServer]] =
    resourceServerRepository.getBy(resourceServerId)

  def getAllForTenant(tenantId: TenantId): IO[List[ResourceServer]] =
    resourceServerRepository.getAllForTenant(tenantId)

}
