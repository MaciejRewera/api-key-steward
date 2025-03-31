package apikeysteward.model.errors

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId

sealed abstract class CommonError(override val message: String) extends CustomError

object CommonError {

  case class ApiKeyTemplateDoesNotExistError(publicApiKeyTemplateId: ApiKeyTemplateId)
      extends CommonError(
        message = s"ApiKeyTemplate with publicTemplateId = [$publicApiKeyTemplateId] does not exist."
      )

  case class UserDoesNotExistError(publicTenantId: TenantId, publicUserId: UserId)
      extends CommonError(
        message =
          s"User with publicUserId = [$publicUserId] does not exist for Tenant with publicTenantId = [$publicTenantId]."
      )

}
