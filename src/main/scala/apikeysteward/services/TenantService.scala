package apikeysteward.services

import apikeysteward.model.errors.TenantDbError
import apikeysteward.model.errors.TenantDbError.TenantInsertionError.TenantAlreadyExistsError
import apikeysteward.model.errors.TenantDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{Tenant, TenantUpdate}
import apikeysteward.repositories.TenantRepository
import apikeysteward.routes.model.admin.tenant.{CreateTenantRequest, UpdateTenantRequest}
import apikeysteward.utils.Retry.RetryException
import apikeysteward.utils.{Logging, Retry}
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class TenantService(uuidGenerator: UuidGenerator, tenantRepository: TenantRepository) extends Logging {

  def createTenant(createTenantRequest: CreateTenantRequest): IO[Either[TenantInsertionError, Tenant]] =
    createTenantWithRetry(createTenantRequest)

  private def createTenantWithRetry(
      createTenantRequest: CreateTenantRequest
  ): IO[Either[TenantInsertionError, Tenant]] = {

    val createTenantMaxRetries = 3
    def isWorthRetrying(err: TenantInsertionError): Boolean = err match {
      case _: TenantAlreadyExistsError => true
      case _                           => false
    }

    def createTenantAction: IO[Either[TenantInsertionError, Tenant]] =
      for {
        _        <- logger.info("Generating Tenant ID...")
        tenantId <- uuidGenerator.generateUuid.flatTap(_ => logger.info("Generated Tenant ID."))

        _ <- logger.info("Inserting Tenant into database...")
        newTenant <- tenantRepository.insert(Tenant.from(tenantId, createTenantRequest)).flatTap {
          case Right(_) => logger.info(s"Inserted Tenant with tenantId: [$tenantId] into database.")
          case Left(e)  => logger.warn(s"Could not insert Tenant into database because: ${e.message}")
        }
      } yield newTenant

    Retry
      .retry(maxRetries = createTenantMaxRetries, isWorthRetrying)(
        createTenantAction
      )
      .map(_.asRight)
      .recover { case exc: RetryException[TenantInsertionError] => exc.error.asLeft[Tenant] }
  }

  def updateTenant(
      tenantId: TenantId,
      updateTenantRequest: UpdateTenantRequest
  ): IO[Either[TenantNotFoundError, Tenant]] =
    tenantRepository.update(TenantUpdate.from(tenantId, updateTenantRequest)).flatTap {
      case Right(_) => logger.info(s"Updated Tenant with tenantId: [$tenantId].")
      case Left(e)  => logger.warn(s"Could not update Tenant with tenantId: [$tenantId] because: ${e.message}")
    }

  def reactivateTenant(tenantId: TenantId): IO[Either[TenantNotFoundError, Tenant]] =
    tenantRepository.activate(tenantId).flatTap {
      case Right(_) => logger.info(s"Activated Tenant with tenantId: [$tenantId].")
      case Left(e)  => logger.warn(s"Could not activate Tenant with tenantId: [$tenantId] because: ${e.message}")
    }

  def deactivateTenant(tenantId: TenantId): IO[Either[TenantNotFoundError, Tenant]] =
    tenantRepository.deactivate(tenantId).flatTap {
      case Right(_) => logger.info(s"Deactivated Tenant with tenantId: [$tenantId].")
      case Left(e)  => logger.warn(s"Could not deactivate Tenant with tenantId: [$tenantId] because: ${e.message}")
    }

  def deleteTenant(tenantId: TenantId): IO[Either[TenantDbError, Tenant]] =
    tenantRepository.delete(tenantId).flatTap {
      case Right(_) => logger.info(s"Deleted Tenant with tenantId: [$tenantId].")
      case Left(e)  => logger.warn(s"Could not delete Tenant with tenantId: [$tenantId] because: ${e.message}")
    }

  def getBy(tenantId: TenantId): IO[Option[Tenant]] =
    tenantRepository.getBy(tenantId)

  def getAll: IO[List[Tenant]] =
    tenantRepository.getAll

}
