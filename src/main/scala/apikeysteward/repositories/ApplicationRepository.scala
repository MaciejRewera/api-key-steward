package apikeysteward.repositories

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.{ApplicationInsertionError, ApplicationNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{Application, ApplicationUpdate}
import apikeysteward.repositories.db.entity.ApplicationEntity
import apikeysteward.repositories.db.{ApplicationDb, TenantDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

class ApplicationRepository(tenantDb: TenantDb, applicationDb: ApplicationDb)(transactor: Transactor[IO]) {

  def insert(publicTenantId: TenantId, application: Application): IO[Either[ApplicationInsertionError, Application]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(tenantDb.getByPublicTenantId(publicTenantId), ReferencedTenantDoesNotExistError(publicTenantId))
        .map(_.id)

      applicationEntityRead <- EitherT(applicationDb.insert(ApplicationEntity.Write.from(tenantId, application)))

      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).value.transact(transactor)

  def update(applicationUpdate: ApplicationUpdate): IO[Either[ApplicationNotFoundError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.update(ApplicationEntity.Update.from(applicationUpdate)))
      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).value.transact(transactor)

  def activate(applicationId: ApplicationId): IO[Either[ApplicationNotFoundError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.activate(applicationId))
      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).value.transact(transactor)

  def deactivate(applicationId: ApplicationId): IO[Either[ApplicationNotFoundError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.deactivate(applicationId))
      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).value.transact(transactor)

  def delete(applicationId: ApplicationId): IO[Either[ApplicationDbError, Application]] =
    (for {
      applicationEntityRead <- EitherT(applicationDb.deleteDeactivated(applicationId))
      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).value.transact(transactor)

  def getBy(applicationId: ApplicationId): IO[Option[Application]] =
    (for {
      applicationEntityRead <- OptionT(applicationDb.getByPublicApplicationId(applicationId))
      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[Application]] =
    (for {
      applicationEntityRead <- applicationDb.getAllForTenant(publicTenantId)
      resultApplication = Application.from(applicationEntityRead)
    } yield resultApplication).compile.toList.transact(transactor)

}
