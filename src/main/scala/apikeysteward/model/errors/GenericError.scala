package apikeysteward.model.errors

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId

sealed abstract class GenericError(override val message: String) extends CustomError
object GenericError {

  case class ApiKeyTemplateDoesNotExistError(publicApiKeyTemplateId: ApiKeyTemplateId)
      extends GenericError(
        message = s"ApiKeyTemplate with publicTemplateId = [$publicApiKeyTemplateId] does not exist."
      )

  case class UserDoesNotExistError(publicTenantId: TenantId, publicUserId: UserId)
      extends GenericError(
        message =
          s"User with publicUserId = [$publicUserId] does not exist for Tenant with publicTenantId = [$publicTenantId]."
      )

}
