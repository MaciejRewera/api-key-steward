package apikeysteward.model.errors

import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ApiKeysPermissionsEntity

import java.sql.SQLException
import java.util.UUID

sealed abstract class ApiKeysPermissionsDbError(override val message: String) extends CustomError

object ApiKeysPermissionsDbError {

  sealed abstract class ApiKeysPermissionsInsertionError(override val message: String)
      extends ApiKeysPermissionsDbError(message)

  object ApiKeysPermissionsInsertionError {

    case class ApiKeysPermissionsInsertionErrorImpl(cause: SQLException)
        extends ApiKeysPermissionsInsertionError(
          message = s"An error occurred when inserting ApiKeysPermissions: $cause"
        )

    case class ApiKeysPermissionsAlreadyExistsError(apiKeyId: UUID, permissionId: UUID)
        extends ApiKeysPermissionsInsertionError(
          message =
            s"ApiKeysPermissions with apiKeyId = [${apiKeyId.toString}] and permissionId = [${permissionId.toString}] already exists."
        )

    trait ReferencedTenantDoesNotExistError extends ApiKeysPermissionsInsertionError {
      val errorMessage: String
    }

    object ReferencedTenantDoesNotExistError {

      private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
          extends ApiKeysPermissionsInsertionError(errorMessage)
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

    trait ReferencedApiKeyDoesNotExistError extends ApiKeysPermissionsInsertionError {
      val errorMessage: String
    }

    object ReferencedApiKeyDoesNotExistError {

      private case class ReferencedApiKeyDoesNotExistErrorImpl(override val errorMessage: String)
          extends ApiKeysPermissionsInsertionError(errorMessage)
          with ReferencedApiKeyDoesNotExistError

      def fromDbId(apiKeyDbId: UUID): ReferencedApiKeyDoesNotExistError =
        ReferencedApiKeyDoesNotExistErrorImpl(
          errorMessage = s"ApiKey with ID = [${apiKeyDbId.toString}] does not exist."
        )

      def apply(publicApiKeyId: ApiKeyId): ReferencedApiKeyDoesNotExistError =
        ReferencedApiKeyDoesNotExistErrorImpl(
          errorMessage = s"ApiKey with publicId = [$publicApiKeyId] does not exist."
        )

    }

    trait ReferencedPermissionDoesNotExistError extends ApiKeysPermissionsInsertionError {
      val errorMessage: String
    }

    object ReferencedPermissionDoesNotExistError {

      private case class ReferencedPermissionDoesNotExistErrorImpl(override val errorMessage: String)
          extends ApiKeysPermissionsInsertionError(errorMessage)
          with ReferencedPermissionDoesNotExistError

      def fromDbId(permissionId: UUID): ReferencedPermissionDoesNotExistError =
        ReferencedPermissionDoesNotExistErrorImpl(
          errorMessage = s"Permission with ID = [${permissionId.toString}] does not exist."
        )

      def apply(publicPermissionId: PermissionId): ReferencedPermissionDoesNotExistError =
        ReferencedPermissionDoesNotExistErrorImpl(
          errorMessage = s"Permission with publicPermissionId = [$publicPermissionId] does not exist."
        )

    }

  }

  case class ApiKeysPermissionsNotFoundError(missingEntities: List[ApiKeysPermissionsEntity.Write])
      extends ApiKeysPermissionsDbError(
        message = {
          val missingEntitiesFormatted =
            missingEntities.map(e => (e.apiKeyDataId, e.permissionId).toString).mkString("[", ", ", "]")

          s"Could not find ApiKeysPermissions with (apiKeyDataId, permissionId): $missingEntitiesFormatted."
        }
      )

}
