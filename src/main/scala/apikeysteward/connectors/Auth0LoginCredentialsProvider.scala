package apikeysteward.connectors

import apikeysteward.config.Auth0ApiConfig
import apikeysteward.connectors.Auth0LoginCredentialsProvider.Auth0LoginCredentials
import cats.effect.IO

class Auth0LoginCredentialsProvider(auth0ApiConfig: Auth0ApiConfig) {

  def getCredentialsFor(tenantDomain: String): IO[Option[Auth0LoginCredentials]] = {
    val result = Some(
      Auth0LoginCredentials(
        clientId = auth0ApiConfig.clientId,
        clientSecret = auth0ApiConfig.clientSecret,
        audience = auth0ApiConfig.audience
      )
    )

    IO.pure(result)
  }

}

object Auth0LoginCredentialsProvider {

  case class Auth0LoginCredentials(
      clientId: String,
      clientSecret: String,
      audience: String
  )

}
