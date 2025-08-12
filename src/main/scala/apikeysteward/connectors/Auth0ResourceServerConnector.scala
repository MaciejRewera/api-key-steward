package apikeysteward.connectors

import apikeysteward.config.Auth0ApiConfig
import apikeysteward.connectors.model.auth0.{Auth0ResourceServer, Auth0ResourceServerResponse}
import apikeysteward.model.errors.Auth0Error
import apikeysteward.repositories.Auth0AccessTokenProvider
import apikeysteward.repositories.Auth0AccessTokenProvider.Auth0AccessToken
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIStringSyntax

class Auth0ResourceServerConnector(
    auth0AccessTokenProvider: Auth0AccessTokenProvider,
    auth0ApiConfig: Auth0ApiConfig,
    auth0Connector: Auth0Connector
) extends Logging {

  def fetchAllResourceServers(tenantDomain: String): EitherT[IO, Auth0Error, List[Auth0ResourceServer]] =
    for {
      accessToken <- EitherT(auth0AccessTokenProvider.getAccessToken(tenantDomain))
      request = buildRequest(accessToken)

      apiResult <- auth0Connector.performApiCall[Auth0ResourceServerResponse](request)
      result = apiResult.resource_servers
    } yield result

  private def buildRequest(accessToken: Auth0AccessToken): Request[IO] = {
    val uri = Uri
      .unsafeFromString(auth0ApiConfig.domain + "/api/v2/resource-servers")
      .withQueryParam("include_totals", true)

    val authorizationHeader = Header.Raw(ci"Authorization", s"Bearer $accessToken")

    Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(List(authorizationHeader))
    )
  }

}
