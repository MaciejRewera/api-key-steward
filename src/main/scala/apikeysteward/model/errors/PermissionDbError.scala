package apikeysteward.model.errors

import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId

import java.sql.SQLException
import java.util.UUID

sealed abstract class PermissionDbError(override val message: String) extends CustomError
object PermissionDbError {

  sealed abstract class PermissionInsertionError(override val message: String) extends PermissionDbError(message)
  object PermissionInsertionError {

    case class PermissionAlreadyExistsError(publicPermissionId: String)
        extends PermissionInsertionError(
          message = s"Permission with publicPermissionId = [$publicPermissionId] already exists."
        )

    case class PermissionAlreadyExistsForThisResourceServerError(permissionName: String, resourceServerId: UUID)
        extends PermissionInsertionError(
          message =
            s"Permission with name = $permissionName already exists for ResourceServer with ID = [${resourceServerId.toString}]."
        )

    trait ReferencedTenantDoesNotExistError extends PermissionInsertionError { val errorMessage: String }
    object ReferencedTenantDoesNotExistError {

      private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
          extends PermissionInsertionError(errorMessage)
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

    trait ReferencedResourceServerDoesNotExistError extends PermissionInsertionError { val errorMessage: String }
    object ReferencedResourceServerDoesNotExistError {

      private case class ReferencedResourceServerDoesNotExistErrorImpl(override val errorMessage: String)
          extends PermissionInsertionError(errorMessage)
          with ReferencedResourceServerDoesNotExistError

      def fromDbId(resourceServerId: UUID): ReferencedResourceServerDoesNotExistError =
        ReferencedResourceServerDoesNotExistErrorImpl(
          errorMessage = s"ResourceServer with ID = [${resourceServerId.toString}] does not exist."
        )
      def apply(publicResourceServerId: ResourceServerId): ReferencedResourceServerDoesNotExistError =
        ReferencedResourceServerDoesNotExistErrorImpl(
          errorMessage = s"ResourceServer with publicResourceServerId = [$publicResourceServerId] does not exist."
        )
    }

    case class PermissionInsertionErrorImpl(cause: SQLException)
        extends PermissionInsertionError(message = s"An error occurred when inserting Permission: $cause")
  }

  case class PermissionNotFoundError(publicResourceServerId: ResourceServerId, publicPermissionId: PermissionId)
      extends PermissionDbError(
        message =
          s"Could not find Permission with publicPermissionId = [$publicPermissionId] for ResourceServer with publicResourceServerId = [$publicResourceServerId]."
      )
}
