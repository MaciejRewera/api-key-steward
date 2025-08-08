package apikeysteward.connectors

import apikeysteward.config.Auth0ApiConfig
import apikeysteward.connectors.Auth0LoginConnector.{Auth0LoginRequest, Auth0LoginResponse}
import apikeysteward.connectors.Auth0LoginCredentialsProvider.Auth0LoginCredentials
import apikeysteward.model.errors.Auth0LoginError
import apikeysteward.model.errors.Auth0LoginError.{Auth0LoginUpstreamErrorResponse, CredentialsNotFoundError}
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.client.Client
import org.http4s.{Method, Request, Response, Status, Uri}

class Auth0LoginConnector(
    auth0ApiConfig: Auth0ApiConfig,
    auth0LoginCredentialsProvider: Auth0LoginCredentialsProvider,
    httpClient: Client[IO]
) extends Logging {

  def fetchAccessToken(tenantDomain: String): EitherT[IO, Auth0LoginError, Auth0LoginResponse] =
    for {
      credentials <- EitherT.fromOptionF(
        auth0LoginCredentialsProvider.getCredentialsFor(tenantDomain),
        CredentialsNotFoundError(tenantDomain).asInstanceOf[Auth0LoginError]
      )
      request = buildRequest(credentials)

      result <- EitherT {
        logger.info("Calling Auth0 login endpoint...").flatMap { _ =>
          httpClient.run(request).use {
            case r @ Response(Status.Ok, _, _, _, _) =>
              r.as[Auth0LoginResponse].map(_.asRight)

            case r: Response[IO] =>
              for {
                responseText <- ConnectorUtils.extractErrorResponse(r)
                errorMessage = s"Call to log into Auth0 failed for tenant domain: $tenantDomain. Reason: $responseText"
                _ <- logger.warn(errorMessage)

                res <- IO(
                  Auth0LoginUpstreamErrorResponse(r.status.code, errorMessage).asInstanceOf[Auth0LoginError].asLeft
                )
              } yield res
          }
        }
      }
    } yield result

  private def buildRequest(credentials: Auth0LoginCredentials): Request[IO] = {
    val uri = Uri
      .unsafeFromString(auth0ApiConfig.domain + "/oauth/token")
      .withQueryParam("include_totals", true)

    val requestBody = Auth0LoginRequest(
      client_id = credentials.clientId,
      client_secret = credentials.clientSecret,
      audience = credentials.audience,
      grant_type = "client_credentials"
    )

    Request[IO](
      method = Method.POST,
      uri = uri
    ).withEntity(requestBody)
  }

}

object Auth0LoginConnector {

  case class Auth0LoginRequest(
      client_id: String,
      client_secret: String,
      audience: String,
      grant_type: String
  )

  object Auth0LoginRequest {
    implicit val codec: Codec[Auth0LoginRequest] = deriveCodec[Auth0LoginRequest]
  }

  case class Auth0LoginResponse(
      access_token: String,
      scope: String,
      expires_in: Int,
      token_type: String
  )

  object Auth0LoginResponse {
    implicit val codec: Codec[Auth0LoginResponse] = deriveCodec[Auth0LoginResponse]
  }

}
