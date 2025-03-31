package apikeysteward.model.errors

import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId

import java.sql.SQLException
import java.util.UUID

sealed abstract class UserDbError(override val message: String) extends CustomError

object UserDbError {

  sealed abstract class UserInsertionError(override val message: String) extends UserDbError(message)

  object UserInsertionError {

    case class UserAlreadyExistsForThisTenantError(publicUserId: UserId, tenantId: UUID)
        extends UserInsertionError(
          message =
            s"User with publicUserId = $publicUserId already exists for Tenant with ID = [${tenantId.toString}]."
        )

    trait ReferencedTenantDoesNotExistError extends UserInsertionError { val errorMessage: String }

    object ReferencedTenantDoesNotExistError {

      private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
          extends UserInsertionError(errorMessage)
          with ReferencedTenantDoesNotExistError

      def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
        ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
        )

      def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
        ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
        )

    }

    case class UserInsertionErrorImpl(cause: SQLException)
        extends UserInsertionError(message = s"An error occurred when inserting User: $cause")

  }

  case class UserNotFoundError(publicTenantId: TenantId, publicUserId: UserId)
      extends UserDbError(
        message =
          s"Could not find User with publicUserId = [$publicUserId] for Tenant with publicTenantId = [$publicTenantId]."
      )

}
