package apikeysteward.model.errors

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity

import java.sql.SQLException
import java.util.UUID

sealed abstract class ApiKeyTemplatesPermissionsDbError(override val message: String) extends CustomError
object ApiKeyTemplatesPermissionsDbError {

  sealed abstract class ApiKeyTemplatesPermissionsInsertionError(override val message: String)
      extends ApiKeyTemplatesPermissionsDbError(message)
  object ApiKeyTemplatesPermissionsInsertionError {

    case class ApiKeyTemplatesPermissionsInsertionErrorImpl(cause: SQLException)
        extends ApiKeyTemplatesPermissionsInsertionError(
          message = s"An error occurred when inserting ApiKeyTemplatesPermissions: $cause"
        )

    case class ApiKeyTemplatesPermissionsAlreadyExistsError(apiKeyTemplateId: UUID, permissionId: UUID)
        extends ApiKeyTemplatesPermissionsInsertionError(
          message =
            s"ApiKeyTemplatesPermissions with apiKeyTemplateId = [${apiKeyTemplateId.toString}] and permissionId = [${permissionId.toString}] already exists."
        )

    trait ReferencedTenantDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
      val errorMessage: String
    }
    object ReferencedTenantDoesNotExistError {

      private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
          extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
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

    trait ReferencedApiKeyTemplateDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
      val errorMessage: String
    }
    object ReferencedApiKeyTemplateDoesNotExistError {

      private case class ReferencedApiKeyTemplateDoesNotExistErrorImpl(override val errorMessage: String)
          extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
          with ReferencedApiKeyTemplateDoesNotExistError

      def fromDbId(apiKeyTemplateId: UUID): ReferencedApiKeyTemplateDoesNotExistError =
        ReferencedApiKeyTemplateDoesNotExistErrorImpl(
          errorMessage = s"ApiKeyTemplate with ID = [${apiKeyTemplateId.toString}] does not exist."
        )
      def apply(publicApiKeyTemplateId: ApiKeyTemplateId): ReferencedApiKeyTemplateDoesNotExistError =
        ReferencedApiKeyTemplateDoesNotExistErrorImpl(
          errorMessage = s"ApiKeyTemplate with publicTemplateId = [$publicApiKeyTemplateId] does not exist."
        )
    }

    trait ReferencedPermissionDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
      val errorMessage: String
    }
    object ReferencedPermissionDoesNotExistError {

      private case class ReferencedPermissionDoesNotExistErrorImpl(override val errorMessage: String)
          extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
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

  case class ApiKeyTemplatesPermissionsNotFoundError(missingEntities: List[ApiKeyTemplatesPermissionsEntity.Write])
      extends ApiKeyTemplatesPermissionsDbError(
        message = {
          val missingEntitiesFormatted =
            missingEntities.map(e => (e.apiKeyTemplateId, e.permissionId).toString).mkString("[", ", ", "]")

          s"Could not find ApiKeyTemplatesPermissions with (apiKeyTemplateId, permissionId): $missingEntitiesFormatted."
        }
      )
}
