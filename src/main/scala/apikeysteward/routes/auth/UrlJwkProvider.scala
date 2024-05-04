package apikeysteward.routes.auth

import apikeysteward.config.JwksConfig
import apikeysteward.routes.auth.UrlJwkProvider.JwksDownloadException
import cats.effect.IO
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.{Response, Status}
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class UrlJwkProvider(jwksConfig: JwksConfig, httpClient: Client[IO]) extends JwkProvider {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  override def getJsonWebKey(keyId: String): IO[Option[JsonWebKey]] =
    httpClient.get(jwksConfig.url) {
      case r @ Response(Status.Ok, _, _, _, _) =>
        r.as[JsonWebKeySet].map(_.findBy(keyId))

      case r: Response[IO] =>
        extractErrorResponse(r).flatMap { responseText =>
          logger.warn(s"Call to obtain JWKS from URL: ${jwksConfig.url} failed. Reason: $responseText")
        } >> IO.raiseError(JwksDownloadException(jwksConfig.url))
    }

  private def extractErrorResponse(response: Response[IO]): IO[String] =
    response.body
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)
}

object UrlJwkProvider {
  case class JwksDownloadException(url: String) extends RuntimeException(s"Call to obtain JWKS from URL: $url failed.")
}
