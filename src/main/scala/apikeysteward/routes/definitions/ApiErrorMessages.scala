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

  object AdminTenant {
    val TenantNotFound = "No Tenant found for provided tenantId."
    def TenantIsNotDeactivated(tenantId: UUID): String = {
      val method = AdminTenantEndpoints.deactivateTenantEndpoint.method.getOrElse("PUT")
      val path = AdminTenantEndpoints.deactivateTenantEndpoint.showPathTemplate()

      s"Could not delete Tenant with tenantId = [$tenantId] because it is active and only inactive Tenants can be permanently deleted. Deactivate the Tenant first, using $method $path API."
    }
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
