package apikeysteward.model.errors

import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}

import java.sql.SQLException
import java.util.UUID

sealed abstract class ResourceServerDbError(override val message: String) extends CustomError
object ResourceServerDbError {

  sealed abstract class ResourceServerInsertionError(override val message: String)
      extends ResourceServerDbError(message)
  object ResourceServerInsertionError {

    case class ResourceServerAlreadyExistsError(publicResourceServerId: String)
        extends ResourceServerInsertionError(
          message = s"ResourceServer with publicResourceServerId = [$publicResourceServerId] already exists."
        )

    trait ReferencedTenantDoesNotExistError extends ResourceServerInsertionError { val errorMessage: String }
    object ReferencedTenantDoesNotExistError {

      private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
          extends ResourceServerInsertionError(errorMessage)
          with ReferencedTenantDoesNotExistError

      def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
        errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
      )
      def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
        errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
      )
    }

    def cannotInsertPermissionError(
        publicResourceServerId: ResourceServerId,
        permissionInsertionError: PermissionInsertionError
    ): ResourceServerInsertionError =
      CannotInsertPermissionError(publicResourceServerId, permissionInsertionError)

    case class CannotInsertPermissionError(
        publicResourceServerId: ResourceServerId,
        permissionInsertionError: PermissionInsertionError
    ) extends ResourceServerInsertionError(
          message =
            s"Could not insert Permissions for ResourceServer with publicResourceServerId = [$publicResourceServerId], because: $permissionInsertionError"
        )

    case class ResourceServerInsertionErrorImpl(cause: SQLException)
        extends ResourceServerInsertionError(message = s"An error occurred when inserting ResourceServer: $cause")
  }

  def resourceServerNotFoundError(publicResourceServerId: ResourceServerId): ResourceServerDbError =
    resourceServerNotFoundError(publicResourceServerId.toString)

  def resourceServerNotFoundError(publicResourceServerId: String): ResourceServerDbError =
    ResourceServerNotFoundError(publicResourceServerId)

  case class ResourceServerNotFoundError(publicResourceServerId: String)
      extends ResourceServerDbError(
        message = s"Could not find ResourceServer with publicResourceServerId = [$publicResourceServerId]."
      )

  def cannotDeletePermissionError(
      publicResourceServerId: ResourceServerId,
      permissionNotFoundError: PermissionNotFoundError
  ): ResourceServerDbError =
    CannotDeletePermissionError(publicResourceServerId, permissionNotFoundError)

  case class CannotDeletePermissionError(
      publicResourceServerId: ResourceServerId,
      permissionNotFoundError: PermissionNotFoundError
  ) extends ResourceServerDbError(
        message =
          s"Could not delete Permissions for ResourceServer with publicResourceServerId = [$publicResourceServerId], because: $permissionNotFoundError"
      )

}
