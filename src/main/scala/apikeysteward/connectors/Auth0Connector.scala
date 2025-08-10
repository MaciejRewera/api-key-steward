package apikeysteward.connectors

import apikeysteward.model.errors.Auth0Error
import apikeysteward.model.errors.Auth0Error.Auth0UpstreamErrorResponse
import apikeysteward.utils.Logging
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Request, Response, Status}

private[connectors] class Auth0Connector(httpClient: Client[IO]) extends Logging {

  def performApiCall[T](request: Request[IO])(implicit ed: EntityDecoder[IO, T]): EitherT[IO, Auth0Error, T] =
    EitherT {
      logger.info(s"Calling Auth0 endpoint: ${request.uri}").flatMap { _ =>
        httpClient.run(request).use {
          case r @ Response(Status.Ok, _, _, _, _) =>
            r.as[T].map(_.asRight)

          case r: Response[IO] =>
            for {
              responseText <- ConnectorUtils.extractErrorResponse(r)
              errorMessage = s"Call to Auth0 API with URL: ${request.uri} failed. Reason: $responseText"
              _ <- logger.warn(errorMessage)

              res <- IO.pure(
                Auth0UpstreamErrorResponse(r.status.code, errorMessage).asInstanceOf[Auth0Error].asLeft
              )
            } yield res
        }
      }
    }

}
