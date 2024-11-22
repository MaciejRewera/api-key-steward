package apikeysteward.routes.definitions

import java.time.Instant
import java.util.UUID

private[routes] object ApiErrorMessages {

  object General {
    val InternalServerError = "An unexpected error has occurred."
    val Unauthorized = "Credentials are invalid."
    val BadRequest = "Invalid input value provided."
    val NotFound = "The requested object does not exist."
  }

  object AdminApiKey {
    val ApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
  }

  object AdminApiKeyTemplate {
    val ApiKeyTemplateNotFound = "No Template found for provided templateId."
    val ReferencedTenantNotFound = "No Tenant found for provided tenantId."
  }

  object AdminApiKeyTemplatesPermissions {
    val ApiKeyTemplatesPermissionsAlreadyExists =
      "At least one of provided permissionIds is already associated with given Template."
    val ReferencedApiKeyTemplateNotFound = "No Template found for provided templateId."
    val ReferencedPermissionNotFound = "At least one Permission cannot be found for provided permissionIds."

    val ApiKeyTemplatesPermissionsNotFound =
      "At least one Template-Permission association does not exist for provided combination of templateId and permissionIds."
  }

  object AdminApiKeyTemplatesUsers {

    object MultipleUsers {
      val ApiKeyTemplatesUsersAlreadyExists =
        "At least one of provided userIds is already associated with given Template."
      val ReferencedApiKeyTemplateNotFound = "No Template found for provided templateId."
      val ReferencedUserNotFound = "At least one User cannot be found for provided combination of tenantId and userIds."
    }

    object MultipleTemplates {
      val ApiKeyTemplatesUsersAlreadyExists =
        "At least one of provided templateIds is already associated with given User."
      val ReferencedApiKeyTemplateNotFound = "At least one Template cannot be found for provided templateIds."
      val ReferencedUserNotFound = "No User found for provided combination of tenantId and userIds."
    }

    val ApiKeyTemplatesUsersNotFound =
      "At least one Template-User association does not exist for provided combination of templateId and userIds."
  }

  object AdminTenant {
    val TenantNotFound = "No Tenant found for provided tenantId."

    def TenantIsNotDeactivated(tenantId: UUID): String = {
      val method = AdminTenantEndpoints.deactivateTenantEndpoint.method.getOrElse("PUT")
      val path = AdminTenantEndpoints.deactivateTenantEndpoint.showPathTemplate()

      s"Could not delete Tenant with tenantId = [$tenantId] because it is active and only inactive Tenants can be permanently deleted. Deactivate the Tenant first, using $method $path API."
    }
  }

  object AdminApplication {
    val ApplicationNotFound = "No Application found for provided applicationId."
    val ReferencedTenantNotFound = "No Tenant found for provided tenantId."

    def ApplicationIsNotDeactivated(applicationId: UUID): String = {
      val method = AdminApplicationEndpoints.deactivateApplicationEndpoint.method.getOrElse("PUT")
      val path = AdminApplicationEndpoints.deactivateApplicationEndpoint.showPathTemplate()

      s"Could not delete Application with applicationId = [$applicationId] because it is active and only inactive Applications can be permanently deleted. Deactivate the Application first, using $method $path API."
    }
  }

  object AdminPermission {
    val PermissionNotFound = "No Permission found for provided combination of applicationId and permissionId."
    val ReferencedApplicationNotFound = "No Application found for provided applicationId."

    val PermissionAlreadyExistsForThisApplication =
      "Permission with this name already exists for Application with provided ID"
  }

  object AdminUser {
    val UserNotFound = "No User found for provided combination of tenantId and userId."
    val ReferencedTenantNotFound = "No Tenant found for provided tenantId."

    val UserAlreadyExistsForThisTenant =
      "User with this userId already exists for Tenant with provided ID"
  }

  object Management {
    val DeleteApiKeyNotFound = "No API Key found for provided keyId."
    val GetSingleApiKeyNotFound = "No API Key found for provided keyId."
  }

  object ValidateApiKey {
    val ValidateApiKeyIncorrect = "Provided API Key is incorrect or does not exist."
    def validateApiKeyExpired(since: Instant): String = s"Provided API Key is expired since: $since."
  }

}
