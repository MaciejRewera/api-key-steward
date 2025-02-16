package apikeysteward.model.errors

import apikeysteward.model.Tenant.TenantId

import java.sql.SQLException

sealed abstract class TenantDbError(override val message: String) extends CustomError
object TenantDbError {

  sealed abstract class TenantInsertionError(override val message: String) extends TenantDbError(message)
  object TenantInsertionError {

    case class TenantAlreadyExistsError(publicTenantId: String)
        extends TenantInsertionError(
          message = s"Tenant with publicTenantId = [$publicTenantId] already exists."
        )

    case class TenantInsertionErrorImpl(cause: SQLException)
        extends TenantInsertionError(message = s"An error occurred when inserting Tenant: $cause")
  }

  def tenantNotFoundError(publicTenantId: TenantId): TenantDbError = tenantNotFoundError(publicTenantId.toString)
  def tenantNotFoundError(publicTenantId: String): TenantDbError = TenantNotFoundError(publicTenantId)

  case class TenantNotFoundError(publicTenantId: String)
      extends TenantDbError(message = s"Could not find Tenant with publicTenantId = [$publicTenantId].")

  def tenantIsNotDeactivatedError(publicTenantId: TenantId): TenantDbError = TenantIsNotDeactivatedError(
    publicTenantId
  )

  case class TenantIsNotDeactivatedError(publicTenantId: TenantId)
      extends TenantDbError(
        message =
          s"Could not delete Tenant with publicTenantId = [${publicTenantId.toString}] because it is not deactivated."
      )

  case class CannotDeleteDependencyError(publicTenantId: TenantId, dependencyError: CustomError)
      extends TenantDbError(
        message =
          s"Could not delete Tenant with publicTenantId = [${publicTenantId.toString}] because one of its dependencies cannot be deleted: ${dependencyError.message}"
      )

}
