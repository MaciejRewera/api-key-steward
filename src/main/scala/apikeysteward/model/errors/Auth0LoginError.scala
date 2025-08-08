package apikeysteward.model.errors

import apikeysteward.connectors.UpstreamErrorResponse

import java.sql.SQLException

sealed abstract class Auth0LoginError(override val message: String) extends CustomError

object Auth0LoginError {

  case class Auth0LoginUpsertError(cause: SQLException)
      extends Auth0LoginError(message = s"An error occurred when upserting Auth0Login: $cause")

  case class CredentialsNotFoundError(tenantDomain: String)
      extends Auth0LoginError(message = s"Could not find credentials for tenant domain: $tenantDomain")

  case class Auth0LoginUpstreamErrorResponse(override val statusCode: Int, errorMessage: String)
      extends Auth0LoginError(message = s"An error occurred when calling Auth0 login API: $errorMessage")
      with UpstreamErrorResponse

}
